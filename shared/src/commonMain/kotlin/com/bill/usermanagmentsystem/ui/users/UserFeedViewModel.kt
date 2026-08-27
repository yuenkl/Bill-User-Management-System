package com.bill.usermanagmentsystem.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.domain.usecase.AddUser
import com.bill.usermanagmentsystem.domain.usecase.DeleteUser
import com.bill.usermanagmentsystem.domain.usecase.LoadNextUsersPage
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.UndoUserDeletion
import com.bill.usermanagmentsystem.platform.AppLifecycleObserver
import com.bill.usermanagmentsystem.platform.AppLifecycleState
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.platform.TimeProvider
import com.bill.usermanagmentsystem.ui.users.presentation.UserFeedPresentationState
import com.bill.usermanagmentsystem.ui.users.presentation.buildUserFeedUiState
import com.bill.usermanagmentsystem.ui.users.presentation.toUserMessage
import com.bill.usermanagmentsystem.utils.AddUserValidator
import com.bill.usermanagmentsystem.utils.RelativeTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class UserFeedViewModel(
    observeUsers: ObserveUsers,
    private val refreshUsers: RefreshUsers,
    private val loadNextUsersPage: LoadNextUsersPage,
    private val addUser: AddUser,
    private val addUserValidator: AddUserValidator,
    private val deleteUser: DeleteUser,
    private val undoUserDeletion: UndoUserDeletion,
    private val connectivityObserver: ConnectivityObserver,
    private val lifecycleObserver: AppLifecycleObserver,
    private val timeProvider: TimeProvider,
    private val relativeTimeFormatter: RelativeTimeFormatter,
    private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val presentation = MutableStateFlow(UserFeedPresentationState())
    private val mutableEvents = MutableSharedFlow<UserFeedEvent>(extraBufferCapacity = 1)
    private var synchronizationJob: Job? = null
    private var pageLoadJob: Job? = null
    private var deletionJob: Job? = null
    private var undoJob: Job? = null
    private var pendingUndoInput: AddUserInput? = null

    private val feedData =
        observeUsers().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    val uiState =
        kotlinx.coroutines.flow
            .combine(
                feedData,
                connectivityObserver.status,
                minuteTicker(),
                presentation,
            ) { users, connectivity, now, presentationState ->
                buildUserFeedUiState(
                    users = users,
                    connectivity = connectivity,
                    now = now,
                    presentation = presentationState,
                    relativeTimeFormatter = relativeTimeFormatter,
                )
            }.flowOn(defaultDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = UserFeedUiState(),
            )

    val events: SharedFlow<UserFeedEvent> = mutableEvents.asSharedFlow()

    init {
        requestSynchronization(manual = false)
        observeAutomaticTriggers()
    }

    fun refresh() {
        requestSynchronization(manual = true)
    }

    fun loadNextPage() {
        requestNextPage(force = false)
    }

    fun retryNextPage() {
        requestNextPage(force = true)
    }

    fun openAddUserForm() {
        if (presentation.value.addUserForm == null) {
            presentation.update { state ->
                state.copy(
                    addUserForm = createAddUserFormState(validator = addUserValidator),
                    addUserValidationAlert = null,
                )
            }
        }
    }

    fun dismissAddUserForm() {
        if (presentation.value.addUserForm?.submitting != true) {
            presentation.update { state ->
                state.copy(addUserForm = null, addUserValidationAlert = null)
            }
        }
    }

    fun dismissAddUserValidationAlert() {
        presentation.update { state -> state.copy(addUserValidationAlert = null) }
    }

    fun updateAddUserName(name: String) {
        updateForm { current ->
            if (current.submitting) {
                current
            } else {
                createAddUserFormState(
                    current
                        .copy(
                            touchedFields = current.touchedFields + AddUserFormEntryType.Name,
                        ).withValue(AddUserFormEntryType.Name, name)
                        .withoutError(AddUserFormEntryType.Form),
                    validator = addUserValidator,
                )
            }
        }
    }

    fun updateAddUserEmail(email: String) {
        updateForm { current ->
            if (current.submitting) {
                current
            } else {
                createAddUserFormState(
                    current
                        .copy(
                            touchedFields = current.touchedFields + AddUserFormEntryType.Email,
                        ).withValue(AddUserFormEntryType.Email, email)
                        .withoutError(AddUserFormEntryType.Form),
                    validator = addUserValidator,
                )
            }
        }
    }

    fun selectAddUserGender(gender: Gender) {
        updateForm { current ->
            if (current.submitting) {
                current
            } else {
                createAddUserFormState(
                    current
                        .copy(
                            touchedFields = current.touchedFields + AddUserFormEntryType.Gender,
                        ).withValue(AddUserFormEntryType.Gender, gender.apiValue)
                        .withoutError(AddUserFormEntryType.Form),
                    validator = addUserValidator,
                )
            }
        }
    }

    fun selectAddUserStatus(status: UserStatus) {
        updateForm { current ->
            if (current.submitting) {
                current
            } else {
                createAddUserFormState(
                    current
                        .withValue(AddUserFormEntryType.Status, status.apiValue)
                        .withoutError(AddUserFormEntryType.Form),
                    validator = addUserValidator,
                )
            }
        }
    }

    fun submitAddUser() {
        val current = presentation.value.addUserForm ?: return
        if (current.submitting) return

        val validated =
            createAddUserFormState(
                current
                    .copy(
                        touchedFields =
                            current.touchedFields +
                                setOf(
                                    AddUserFormEntryType.Name,
                                    AddUserFormEntryType.Email,
                                    AddUserFormEntryType.Gender,
                                ),
                    ).withoutError(AddUserFormEntryType.Form),
                validator = addUserValidator,
            )
        if (!validated.canSubmit) {
            presentation.update { state ->
                state.copy(addUserForm = validated)
            }
            return
        }

        val submitting = validated.copy(submitting = true)
        presentation.update { state ->
            state.copy(addUserForm = submitting, addUserValidationAlert = null)
        }
        viewModelScope.launch {
            val result =
                addUser(
                    AddUserInput(
                        name = addUserValidator.normalize(submitting.valueFor(AddUserFormEntryType.Name).orEmpty()),
                        email = addUserValidator.normalize(submitting.valueFor(AddUserFormEntryType.Email).orEmpty()),
                        gender = checkNotNull(submitting.gender()),
                        status = submitting.status(),
                    ),
                )
            val activeForm = presentation.value.addUserForm ?: return@launch
            presentation.update { state ->
                if (result.isSuccess) {
                    state.copy(addUserForm = null)
                } else {
                    val failure = result.exceptionOrNull()
                    state.copy(
                        addUserForm = activeForm.withSubmissionFailure(failure),
                        addUserValidationAlert = failure.toAddUserValidationAlert(),
                    )
                }
            }
            if (result.isSuccess) mutableEvents.emit(UserFeedEvent.ScrollToTop)
        }
    }

    fun selectUserForDeletion(localId: String) {
        if (feedData.value.none { it.user.localId == localId }) return
        presentation.update { it.copy(selectedUserId = localId) }
    }

    fun cancelDelete() {
        if (deletionJob?.isActive == true) return
        presentation.update { it.copy(selectedUserId = null) }
    }

    fun confirmDelete() {
        if (deletionJob?.isActive == true) return
        val localId = presentation.value.selectedUserId ?: return
        if (feedData.value.none { it.user.localId == localId }) {
            presentation.update { it.copy(selectedUserId = null) }
            return
        }

        deletionJob =
            viewModelScope.launch {
                presentation.update { it.copy(deleteInProgress = true) }
                val result = deleteUser(localId)
                presentation.update {
                    it.copy(
                        selectedUserId = null,
                        deleteInProgress = false,
                    )
                }
                result.fold(
                    onSuccess = { deleted ->
                        pendingUndoInput = deleted.input
                        mutableEvents.emit(
                            UserFeedEvent.ShowDeleteUndoSnackbar(
                                userName = deleted.userName,
                                input = deleted.input,
                            ),
                        )
                    },
                    onFailure = { emitFailure(it) },
                )
            }
    }

    fun undoDelete(input: AddUserInput) {
        if (undoJob?.isActive == true) return
        if (pendingUndoInput != input) return
        pendingUndoInput = null

        undoJob =
            viewModelScope.launch {
                val result = undoUserDeletion(input)
                if (result.isSuccess) {
                    mutableEvents.emit(UserFeedEvent.ScrollToTop)
                } else {
                    emitFailure(result.exceptionOrNull())
                }
            }
    }

    fun dismissUndoDelete(input: AddUserInput) {
        if (pendingUndoInput == input) pendingUndoInput = null
    }

    private fun requestSynchronization(manual: Boolean) {
        if (synchronizationJob?.isActive == true) return
        pageLoadJob?.cancel()
        synchronizationJob =
            viewModelScope.launch {
                presentation.update { it.copy(refreshing = manual) }
                val result = refreshUsers()
                val failure = result.exceptionOrNull()
                presentation.update { current ->
                    current.copy(
                        initialAttemptFinished = true,
                        refreshing = false,
                        loadingNextPage = false,
                        canLoadNextPage = if (result.isSuccess) true else current.canLoadNextPage,
                        nextPageError = if (result.isSuccess) null else current.nextPageError,
                        refreshError = failure?.userDataErrorOrNull(),
                    )
                }
                if (manual && failure != null) emitFailure(failure)
            }
    }

    private fun requestNextPage(force: Boolean) {
        val current = presentation.value
        if (
            pageLoadJob?.isActive == true ||
            !current.canLoadNextPage ||
            (!force && current.nextPageError != null)
        ) {
            return
        }
        presentation.update {
            it.copy(
                loadingNextPage = true,
                nextPageError = null,
            )
        }
        pageLoadJob =
            viewModelScope.launch {
                val result = loadNextUsersPage()
                presentation.update { active ->
                    result.fold(
                        onSuccess = { page ->
                            active.copy(
                                loadingNextPage = false,
                                canLoadNextPage = page.hasMore,
                                nextPageError = null,
                            )
                        },
                        onFailure = { failure ->
                            active.copy(
                                loadingNextPage = false,
                                nextPageError = failure.toUserMessage(),
                            )
                        },
                    )
                }
            }
    }

    private fun observeAutomaticTriggers() {
        viewModelScope.launch {
            connectivityObserver.status
                .drop(1)
                .filter { it == ConnectivityStatus.Available }
                .collect { requestSynchronization(manual = false) }
        }
        viewModelScope.launch {
            lifecycleObserver.state
                .drop(1)
                .filter { it == AppLifecycleState.Foreground }
                .collect { requestSynchronization(manual = false) }
        }
    }

    private fun minuteTicker(): Flow<Instant> =
        flow {
            while (true) {
                emit(timeProvider.now())
                delay(1.minutes)
            }
        }

    private suspend fun emitFailure(failure: Throwable?) {
        mutableEvents.emit(UserFeedEvent.ShowSnackbar(failure.toUserMessage()))
    }

    private fun updateForm(transform: (AddUserFormUiState) -> AddUserFormUiState) {
        val current = presentation.value.addUserForm ?: return
        presentation.update { state ->
            state.copy(
                addUserForm = transform(current),
                addUserValidationAlert = null,
            )
        }
    }
}
