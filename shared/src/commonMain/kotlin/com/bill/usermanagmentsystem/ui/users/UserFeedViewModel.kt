package com.bill.usermanagmentsystem.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.domain.usecase.AddUser
import com.bill.usermanagmentsystem.domain.usecase.AddUserValidator
import com.bill.usermanagmentsystem.domain.usecase.DeleteUserWithUndo
import com.bill.usermanagmentsystem.domain.usecase.EmailValidationError
import com.bill.usermanagmentsystem.domain.usecase.LoadNextUsersPage
import com.bill.usermanagmentsystem.domain.usecase.NameValidationError
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
import com.bill.usermanagmentsystem.domain.usecase.UndoUserDeletion
import com.bill.usermanagmentsystem.platform.AppLifecycleObserver
import com.bill.usermanagmentsystem.platform.AppLifecycleState
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val deleteUserWithUndo: DeleteUserWithUndo,
    private val undoUserDeletion: UndoUserDeletion,
    private val connectivityObserver: ConnectivityObserver,
    private val lifecycleObserver: AppLifecycleObserver,
    private val timeProvider: TimeProvider,
    private val relativeTimeFormatter: RelativeTimeFormatter,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val presentation = MutableStateFlow(PresentationState())
    private var synchronizationJob: Job? = null
    private var pageLoadJob: Job? = null
    private var deletionJob: Job? = null
    private var undoJob: Job? = null

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
                buildUiState(users, connectivity, now, presentationState)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = UserFeedUiState(),
            )

    init {
        requestSynchronization(manual = false)
        observeAutomaticTriggers()
    }

    fun refresh() {
        requestSynchronization(manual = true)
    }

    fun retry() {
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
                    addUserForm = createFormState(),
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
                createFormState(
                    current
                        .copy(
                            touchedFields = current.touchedFields + AddUserField.Name,
                        ).withValue(AddUserField.Name, name)
                        .withoutError(AddUserField.Form),
                )
            }
        }
    }

    fun updateAddUserEmail(email: String) {
        updateForm { current ->
            if (current.submitting) {
                current
            } else {
                createFormState(
                    current
                        .copy(
                            touchedFields = current.touchedFields + AddUserField.Email,
                        ).withValue(AddUserField.Email, email)
                        .withoutError(AddUserField.Form),
                )
            }
        }
    }

    fun selectAddUserGender(gender: Gender) {
        updateForm { current ->
            if (current.submitting) {
                current
            } else {
                createFormState(
                    current
                        .copy(
                            touchedFields = current.touchedFields + AddUserField.Gender,
                        ).withValue(AddUserField.Gender, gender.apiValue)
                        .withoutError(AddUserField.Form),
                )
            }
        }
    }

    fun selectAddUserStatus(status: UserStatus) {
        updateForm { current ->
            if (current.submitting) {
                current
            } else {
                createFormState(
                    current
                        .withValue(AddUserField.Status, status.apiValue)
                        .withoutError(AddUserField.Form),
                )
            }
        }
    }

    fun submitAddUser() {
        val current = presentation.value.addUserForm ?: return
        if (current.submitting) return

        val validated =
            createFormState(
                current
                    .copy(
                        touchedFields =
                            current.touchedFields +
                                setOf(
                                    AddUserField.Name,
                                    AddUserField.Email,
                                    AddUserField.Gender,
                                ),
                    ).withoutError(AddUserField.Form),
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
        viewModelScope.launch(dispatcher) {
            val result =
                addUser(
                    AddUserInput(
                        name = addUserValidator.normalize(submitting.valueFor(AddUserField.Name).orEmpty()),
                        email = addUserValidator.normalize(submitting.valueFor(AddUserField.Email).orEmpty()),
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
        }
    }

    fun consumeMessage(id: Long) {
        presentation.update { current ->
            if (current.message?.id == id) current.copy(message = null) else current
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
            viewModelScope.launch(dispatcher) {
                presentation.update { it.copy(deleteInProgress = true) }
                val result = deleteUserWithUndo(localId)
                presentation.update { current ->
                    val completed =
                        current.copy(
                            selectedUserId = null,
                            deleteInProgress = false,
                        )
                    result.fold(
                        onSuccess = { deleted ->
                            completed.copy(
                                undoSnackbar =
                                    DeleteUndoUiModel(
                                        userName = deleted.userName,
                                        input = deleted.input,
                                    ),
                            )
                        },
                        onFailure = { completed.withFailureMessage(it) },
                    )
                }
            }
    }

    fun undoDelete(input: AddUserInput) {
        if (undoJob?.isActive == true) return
        if (presentation.value.undoSnackbar?.input != input) return

        undoJob =
            viewModelScope.launch(dispatcher) {
                val result = undoUserDeletion(input)
                presentation.update { it.copy(undoSnackbar = null) }
                if (result.isFailure) publishFailure(result.exceptionOrNull())
            }
    }

    fun dismissUndoDelete(input: AddUserInput) {
        presentation.update { current ->
            if (current.undoSnackbar?.input == input) current.copy(undoSnackbar = null) else current
        }
    }

    private fun requestSynchronization(manual: Boolean) {
        if (synchronizationJob?.isActive == true) return
        pageLoadJob?.cancel()
        synchronizationJob =
            viewModelScope.launch(dispatcher) {
                presentation.update { it.copy(refreshing = manual) }
                val result = refreshUsers()
                presentation.update { current ->
                    val refreshed =
                        current.copy(
                            initialAttemptFinished = true,
                            refreshing = false,
                            loadingNextPage = false,
                            canLoadNextPage = result.isSuccess,
                            nextPageError = null,
                            refreshError = result.exceptionOrNull()?.userDataErrorOrNull(),
                        )
                    if (manual && result.isFailure) {
                        refreshed.withFailureMessage(result.exceptionOrNull())
                    } else {
                        refreshed
                    }
                }
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
            viewModelScope.launch(dispatcher) {
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
        viewModelScope.launch(dispatcher) {
            connectivityObserver.status
                .drop(1)
                .filter { it == ConnectivityStatus.Available }
                .collect { requestSynchronization(manual = false) }
        }
        viewModelScope.launch(dispatcher) {
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
        }.flowOn(dispatcher)

    private fun buildUiState(
        users: List<UserRecord>,
        connectivity: ConnectivityStatus,
        now: Instant,
        presentationState: PresentationState,
    ): UserFeedUiState {
        val items = users.map { it.toUiModel(now) }
        val offline = connectivity == ConnectivityStatus.Unavailable
        val error = presentationState.refreshError
        val initialLoading = items.isEmpty() && !presentationState.initialAttemptFinished
        val emptyState =
            when {
                items.isNotEmpty() || initialLoading -> null
                offline -> UserFeedEmptyState.Offline
                error == UserDataError.AuthenticationRequired -> UserFeedEmptyState.AuthenticationRequired
                error != null -> UserFeedEmptyState.Error(error.toUserMessage())
                else -> UserFeedEmptyState.Empty
            }
        val banner =
            when {
                items.isEmpty() -> null
                offline -> UserFeedBanner.Offline
                error == UserDataError.AuthenticationRequired -> UserFeedBanner.AuthenticationRequired
                error != null -> UserFeedBanner.RefreshFailed(error.toUserMessage())
                else -> null
            }
        return UserFeedUiState(
            users = items,
            initialLoading = initialLoading,
            refreshing = presentationState.refreshing,
            loadingMore = presentationState.loadingNextPage,
            canLoadMore = presentationState.canLoadNextPage,
            loadMoreError = presentationState.nextPageError,
            emptyState = emptyState,
            banner = banner,
            message = presentationState.message,
            addUserForm = presentationState.addUserForm,
            addUserValidationAlert = presentationState.addUserValidationAlert,
            deleteConfirmation =
                items.firstOrNull {
                    it.localId == presentationState.selectedUserId
                },
            deleteInProgress = presentationState.deleteInProgress,
            undoSnackbar = presentationState.undoSnackbar,
        )
    }

    private fun publishFailure(failure: Throwable?) {
        presentation.update { it.withFailureMessage(failure) }
    }

    private fun PresentationState.withFailureMessage(failure: Throwable?): PresentationState {
        val nextSequence = messageSequence + 1
        return copy(
            message =
                UserFeedMessage(
                    id = nextSequence,
                    text = failure.toUserMessage(),
                ),
            messageSequence = nextSequence,
        )
    }

    private fun UserRecord.toUiModel(now: Instant): UserItemUiModel =
        UserItemUiModel(
            localId = user.localId,
            name = user.name,
            email = user.email,
            gender = user.gender,
            status = user.status,
            relativeTime = relativeTimeFormatter.format(user.observedAt, now),
        )

    private fun updateForm(transform: (AddUserFormUiState) -> AddUserFormUiState) {
        val current = presentation.value.addUserForm ?: return
        presentation.update { state ->
            state.copy(
                addUserForm = transform(current),
                addUserValidationAlert = null,
            )
        }
    }

    private fun createFormState(current: AddUserFormUiState = AddUserFormUiState()): AddUserFormUiState {
        val nameValidation = addUserValidator.validateName(current.valueFor(AddUserField.Name).orEmpty())
        val emailValidation = addUserValidator.validateEmail(current.valueFor(AddUserField.Email).orEmpty())
        var form = current
        if (AddUserField.Name in current.touchedFields && nameValidation != null) {
            form = form.withError(AddUserField.Name, nameValidation.userMessage())
        }
        if (AddUserField.Email in current.touchedFields && emailValidation != null) {
            form = form.withError(AddUserField.Email, emailValidation.userMessage())
        }
        if (AddUserField.Gender in current.touchedFields && current.gender() == null) {
            form = form.withError(AddUserField.Gender, "Choose a gender.")
        }
        return form.copy(
            isValid = nameValidation == null && emailValidation == null && current.gender() != null,
        )
    }

    private fun AddUserFormUiState.withSubmissionFailure(failure: Throwable?): AddUserFormUiState {
        val fieldErrors = failure.toAddUserApiFieldErrors()
        var form = copy(submitting = false)
        val fieldsWithErrors =
            fieldErrors.mapNotNull { error ->
                AddUserField.fromApiName(error.field)?.also { field ->
                    form = form.withError(field, error.message)
                }
            }
        return if (fieldsWithErrors.isNotEmpty()) {
            form
        } else {
            form.withError(AddUserField.Form, failure.toAddUserMessage())
        }
    }

    private fun AddUserFormUiState.withValue(
        field: AddUserField,
        value: String,
    ): AddUserFormUiState =
        updateDetail(field) { detail ->
            detail.copy(value = value, error = null)
        }

    private fun AddUserFormUiState.withError(
        field: AddUserField,
        error: String,
    ): AddUserFormUiState =
        updateDetail(field) { detail ->
            detail.copy(error = error)
        }

    private fun AddUserFormUiState.withoutError(field: AddUserField): AddUserFormUiState =
        updateDetail(field) { detail ->
            detail.copy(error = null)
        }

    private fun AddUserFormUiState.updateDetail(
        field: AddUserField,
        transform: (UserDetail) -> UserDetail,
    ): AddUserFormUiState =
        copy(
            details =
                details
                    .map { detail ->
                        if (detail.type == field) transform(detail) else detail
                    }.let { updatedDetails ->
                        if (updatedDetails.any { it.type == field }) {
                            updatedDetails
                        } else {
                            updatedDetails + transform(UserDetail(type = field))
                        }
                    },
        )

    private data class PresentationState(
        val initialAttemptFinished: Boolean = false,
        val refreshing: Boolean = false,
        val loadingNextPage: Boolean = false,
        val canLoadNextPage: Boolean = false,
        val nextPageError: String? = null,
        val message: UserFeedMessage? = null,
        val addUserForm: AddUserFormUiState? = null,
        val addUserValidationAlert: AddUserValidationAlert? = null,
        val refreshError: UserDataError? = null,
        val messageSequence: Long = 0,
        val selectedUserId: String? = null,
        val deleteInProgress: Boolean = false,
        val undoSnackbar: DeleteUndoUiModel? = null,
    )
}

private fun NameValidationError.userMessage(): String =
    when (this) {
        NameValidationError.Required -> "Enter a name."
        NameValidationError.TooShort -> "Name must be at least 2 characters."
        NameValidationError.TooLong -> "Name must be 80 characters or fewer."
        NameValidationError.MissingLetter -> "Name must contain at least one letter."
        NameValidationError.ControlCharacter -> "Name contains an unsupported control character."
    }

private fun EmailValidationError.userMessage(): String =
    when (this) {
        EmailValidationError.Required -> "Enter an email address."
        EmailValidationError.TooLong -> "Email must be 254 characters or fewer."
        EmailValidationError.ExactlyOneAt -> "Email must contain exactly one @."
        EmailValidationError.Whitespace -> "Email cannot contain whitespace."
        EmailValidationError.LocalPartRequired -> "Enter the part before @."
        EmailValidationError.LocalPartTooLong -> "The part before @ must be 64 characters or fewer."
        EmailValidationError.InvalidDomain -> "Enter a valid email domain."
        EmailValidationError.FinalLabelTooShort -> "The final domain label must be at least 2 characters."
    }

private fun Throwable?.toUserMessage(): String =
    this?.userDataErrorOrNull()?.toUserMessage() ?: "The user directory could not be refreshed."

private fun Throwable?.toAddUserMessage(): String = this?.userDataErrorOrNull()?.toUserMessage() ?: "The user could not be saved."

private fun UserDataError.toUserMessage(): String =
    when (this) {
        UserDataError.Offline -> "You're offline. Cached users will remain available."
        UserDataError.AuthenticationRequired -> "Check the GoRest access token, then retry."
        UserDataError.DeleteTooLate -> "That deletion can no longer be undone."
        is UserDataError.UserNotFound -> "That user is no longer available."
        is UserDataError.ValidationRejected -> reason
        is UserDataError.RetryScheduled -> reason
        is UserDataError.Persistence -> "Saved users could not be read. $reason"
        is UserDataError.RemoteContract -> "The service returned unexpected data. $reason"
        is UserDataError.Unexpected -> reason
    }

private fun Throwable?.toAddUserValidationAlert(): AddUserValidationAlert? =
    toAddUserApiFieldErrors()
        .takeIf { it.isNotEmpty() }
        ?.let(::AddUserValidationAlert)

private fun Throwable?.toAddUserApiFieldErrors(): List<AddUserApiFieldError> {
    val reason =
        (this?.userDataErrorOrNull() as? UserDataError.ValidationRejected)?.reason
            ?: return emptyList()
    return reason
        .split(';')
        .mapNotNull { issue ->
            val separator = issue.indexOf(':')
            if (separator < 0) return@mapNotNull null
            val field = issue.substring(0, separator).trim().lowercase()
            val message = issue.substring(separator + 1).trim()
            AddUserApiFieldError(field = field, message = message)
                .takeIf { it.field.isNotEmpty() && it.message.isNotEmpty() }
        }
}
