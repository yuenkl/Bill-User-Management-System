package com.bill.usermanagmentsystem.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.UserSynchronization
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.domain.usecase.AddUser
import com.bill.usermanagmentsystem.domain.usecase.AddUserValidator
import com.bill.usermanagmentsystem.domain.usecase.EmailValidationError
import com.bill.usermanagmentsystem.domain.usecase.NameValidationError
import com.bill.usermanagmentsystem.domain.usecase.ObserveSyncState
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
import com.bill.usermanagmentsystem.domain.usecase.RetryUserCreation
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class UserFeedViewModel(
    observeUsers: ObserveUsers,
    observeSyncState: ObserveSyncState,
    private val refreshUsers: RefreshUsers,
    private val addUser: AddUser,
    private val retryUserCreationUseCase: RetryUserCreation,
    private val addUserValidator: AddUserValidator,
    private val connectivityObserver: ConnectivityObserver,
    private val lifecycleObserver: AppLifecycleObserver,
    private val timeProvider: TimeProvider,
    private val relativeTimeFormatter: RelativeTimeFormatter,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val presentation = MutableStateFlow(PresentationState())
    private var synchronizationJob: Job? = null
    private var nextMessageId = 0L

    val uiState = combine(
        observeUsers(),
        observeSyncState(),
        connectivityObserver.status,
        minuteTicker(),
        presentation,
    ) { users, syncState, connectivity, now, presentationState ->
        buildUiState(users, syncState, connectivity, now, presentationState)
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

    fun openAddUserForm() {
        if (presentation.value.addUserForm == null) {
            presentation.value = presentation.value.copy(
                addUserForm = createFormState(),
            )
        }
    }

    fun dismissAddUserForm() {
        if (presentation.value.addUserForm?.submitting != true) {
            presentation.value = presentation.value.copy(addUserForm = null)
        }
    }

    fun updateAddUserName(name: String) {
        updateForm { current ->
            if (current.submitting) current else createFormState(
                current.copy(
                    name = name,
                    nameTouched = true,
                    nameApiError = null,
                    submissionError = null,
                ),
            )
        }
    }

    fun updateAddUserEmail(email: String) {
        updateForm { current ->
            if (current.submitting) current else createFormState(
                current.copy(
                    email = email,
                    emailTouched = true,
                    emailApiError = null,
                    submissionError = null,
                ),
            )
        }
    }

    fun selectAddUserGender(gender: Gender) {
        updateForm { current ->
            if (current.submitting) current else createFormState(
                current.copy(
                    gender = gender,
                    genderTouched = true,
                    submissionError = null,
                ),
            )
        }
    }

    fun selectAddUserStatus(status: UserStatus) {
        updateForm { current ->
            if (current.submitting) current else createFormState(
                current.copy(status = status, submissionError = null),
            )
        }
    }

    fun consumeAddUserApiError(field: AddUserField) {
        updateForm { current ->
            when (field) {
                AddUserField.Name -> current.copy(nameApiError = null)
                AddUserField.Email -> current.copy(emailApiError = null)
            }
        }
    }

    fun submitAddUser() {
        val current = presentation.value.addUserForm ?: return
        if (current.submitting) return

        val validated = createFormState(
            current.copy(
                nameTouched = true,
                emailTouched = true,
                genderTouched = true,
                submissionError = null,
            ),
        )
        if (!validated.canSubmit) {
            presentation.value = presentation.value.copy(addUserForm = validated)
            return
        }

        val submitting = validated.copy(submitting = true)
        presentation.value = presentation.value.copy(addUserForm = submitting)
        viewModelScope.launch(dispatcher) {
            val result = addUser(
                AddUserInput(
                    name = addUserValidator.normalize(submitting.name),
                    email = addUserValidator.normalize(submitting.email),
                    gender = checkNotNull(submitting.gender),
                    status = submitting.status,
                ),
            )
            val activeForm = presentation.value.addUserForm ?: return@launch
            presentation.value = if (result.isSuccess) {
                presentation.value.copy(addUserForm = null)
            } else {
                presentation.value.copy(
                    addUserForm = activeForm.withSubmissionFailure(result.exceptionOrNull()),
                )
            }
        }
    }

    fun retryUserCreation(localId: String) {
        if (localId in presentation.value.retryingUserIds) return
        presentation.value = presentation.value.copy(
            retryingUserIds = presentation.value.retryingUserIds + localId,
        )
        viewModelScope.launch(dispatcher) {
            val result = retryUserCreationUseCase(localId)
            presentation.value = presentation.value.copy(
                retryingUserIds = presentation.value.retryingUserIds - localId,
                message = if (result.isFailure) {
                    UserFeedMessage(
                        id = ++nextMessageId,
                        text = result.exceptionOrNull().toUserMessage(),
                    )
                } else {
                    presentation.value.message
                },
            )
        }
    }

    fun consumeMessage(id: Long) {
        presentation.value = presentation.value.let { current ->
            if (current.message?.id == id) current.copy(message = null) else current
        }
    }

    private fun requestSynchronization(manual: Boolean) {
        if (synchronizationJob?.isActive == true) return
        synchronizationJob = viewModelScope.launch(dispatcher) {
            presentation.value = presentation.value.copy(refreshing = manual)
            val result = refreshUsers()
            presentation.value = presentation.value.copy(
                initialAttemptFinished = true,
                refreshing = false,
                message = if (manual && result.isFailure) {
                    UserFeedMessage(
                        id = ++nextMessageId,
                        text = result.exceptionOrNull().toUserMessage(),
                    )
                } else {
                    presentation.value.message
                },
            )
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

    private fun minuteTicker(): Flow<Instant> = flow {
        while (true) {
            emit(timeProvider.now())
            delay(1.minutes)
        }
    }.flowOn(dispatcher)

    private fun buildUiState(
        users: List<UserRecord>,
        syncState: SyncState,
        connectivity: ConnectivityStatus,
        now: Instant,
        presentationState: PresentationState,
    ): UserFeedUiState {
        val items = users.map { it.toUiModel(now, presentationState.retryingUserIds) }
        val offline = connectivity == ConnectivityStatus.Unavailable
        val error = (syncState as? SyncState.Failed)?.error
        val initialLoading = items.isEmpty() && !presentationState.initialAttemptFinished
        val emptyState = when {
            items.isNotEmpty() || initialLoading -> null
            offline -> UserFeedEmptyState.Offline
            error == UserDataError.AuthenticationRequired -> UserFeedEmptyState.AuthenticationRequired
            error != null -> UserFeedEmptyState.Error(error.toUserMessage())
            else -> UserFeedEmptyState.Empty
        }
        val banner = when {
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
            emptyState = emptyState,
            banner = banner,
            message = presentationState.message,
            addUserForm = presentationState.addUserForm,
        )
    }

    private fun UserRecord.toUiModel(
        now: Instant,
        retryingUserIds: Set<String>,
    ): UserItemUiModel = UserItemUiModel(
        localId = user.localId,
        name = user.name,
        email = user.email,
        gender = user.gender,
        status = user.status,
        relativeTime = relativeTimeFormatter.format(user.observedAt, now),
        synchronization = when (val state = synchronization) {
            UserSynchronization.Synced -> UserItemSynchronization.Synced
            UserSynchronization.PendingCreate -> UserItemSynchronization.Pending
            is UserSynchronization.CreateFailed -> UserItemSynchronization.Failed(
                reason = state.reason,
                retrying = user.localId in retryingUserIds,
            )
        },
    )

    private fun updateForm(transform: (AddUserFormUiState) -> AddUserFormUiState) {
        val current = presentation.value.addUserForm ?: return
        presentation.value = presentation.value.copy(addUserForm = transform(current))
    }

    private fun createFormState(
        current: AddUserFormUiState = AddUserFormUiState(),
    ): AddUserFormUiState {
        val nameValidation = addUserValidator.validateName(current.name)
        val emailValidation = addUserValidator.validateEmail(current.email)
        return current.copy(
            nameError = if (current.nameTouched) nameValidation?.userMessage() else null,
            emailError = if (current.emailTouched) emailValidation?.userMessage() else null,
            genderError = if (current.genderTouched && current.gender == null) {
                "Choose a gender."
            } else {
                null
            },
            isValid = nameValidation == null && emailValidation == null && current.gender != null,
        )
    }

    private fun AddUserFormUiState.withSubmissionFailure(failure: Throwable?): AddUserFormUiState {
        val error = failure?.userDataErrorOrNull()
        val reason = (error as? UserDataError.ValidationRejected)?.reason
        val fieldErrors = reason.orEmpty()
            .split(';')
            .mapNotNull { issue ->
                val separator = issue.indexOf(':')
                if (separator < 0) return@mapNotNull null
                issue.substring(0, separator).trim().lowercase() to
                    issue.substring(separator + 1).trim()
            }
            .toMap()
        return if (fieldErrors.keys.any { it == "name" || it == "email" }) {
            copy(
                submitting = false,
                nameApiError = fieldErrors["name"],
                emailApiError = fieldErrors["email"],
            )
        } else {
            copy(submitting = false, submissionError = failure.toAddUserMessage())
        }
    }

    private data class PresentationState(
        val initialAttemptFinished: Boolean = false,
        val refreshing: Boolean = false,
        val message: UserFeedMessage? = null,
        val addUserForm: AddUserFormUiState? = null,
        val retryingUserIds: Set<String> = emptySet(),
    )
}

private fun NameValidationError.userMessage(): String = when (this) {
    NameValidationError.Required -> "Enter a name."
    NameValidationError.TooShort -> "Name must be at least 2 characters."
    NameValidationError.TooLong -> "Name must be 80 characters or fewer."
    NameValidationError.MissingLetter -> "Name must contain at least one letter."
    NameValidationError.ControlCharacter -> "Name contains an unsupported control character."
}

private fun EmailValidationError.userMessage(): String = when (this) {
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

private fun Throwable?.toAddUserMessage(): String =
    this?.userDataErrorOrNull()?.toUserMessage() ?: "The user could not be saved."

private fun UserDataError.toUserMessage(): String = when (this) {
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
