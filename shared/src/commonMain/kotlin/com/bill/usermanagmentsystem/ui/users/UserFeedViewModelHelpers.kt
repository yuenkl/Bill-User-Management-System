package com.bill.usermanagmentsystem.ui.users

import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.domain.usecase.AddUserValidator
import com.bill.usermanagmentsystem.domain.usecase.EmailValidationError
import com.bill.usermanagmentsystem.domain.usecase.NameValidationError
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import kotlin.time.Instant

internal data class UserFeedPresentationState(
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

internal fun buildUserFeedUiState(
    users: List<UserRecord>,
    connectivity: ConnectivityStatus,
    now: Instant,
    presentation: UserFeedPresentationState,
    relativeTimeFormatter: RelativeTimeFormatter,
): UserFeedUiState {
    val items = users.map { it.toUiModel(now, relativeTimeFormatter) }
    val offline = connectivity == ConnectivityStatus.Unavailable
    val error = presentation.refreshError
    val initialLoading = items.isEmpty() && !presentation.initialAttemptFinished
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
        refreshing = presentation.refreshing,
        loadingMore = presentation.loadingNextPage,
        canLoadMore = presentation.canLoadNextPage,
        loadMoreError = presentation.nextPageError,
        emptyState = emptyState,
        banner = banner,
        message = presentation.message,
        addUserForm = presentation.addUserForm,
        addUserValidationAlert = presentation.addUserValidationAlert,
        deleteConfirmation = items.firstOrNull { it.localId == presentation.selectedUserId },
        deleteInProgress = presentation.deleteInProgress,
        undoSnackbar = presentation.undoSnackbar,
    )
}

internal fun createAddUserFormState(
    current: AddUserFormUiState = AddUserFormUiState(),
    validator: AddUserValidator,
): AddUserFormUiState {
    val nameValidation = validator.validateName(current.valueFor(AddUserField.Name).orEmpty())
    val emailValidation = validator.validateEmail(current.valueFor(AddUserField.Email).orEmpty())
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

internal fun UserFeedPresentationState.withFailureMessage(failure: Throwable?): UserFeedPresentationState {
    val nextSequence = messageSequence + 1
    return copy(
        message = UserFeedMessage(id = nextSequence, text = failure.toUserMessage()),
        messageSequence = nextSequence,
    )
}

internal fun AddUserFormUiState.withSubmissionFailure(failure: Throwable?): AddUserFormUiState {
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

internal fun AddUserFormUiState.withValue(
    field: AddUserField,
    value: String,
): AddUserFormUiState =
    updateDetail(field) { detail ->
        detail.copy(value = value, error = null)
    }

internal fun AddUserFormUiState.withError(
    field: AddUserField,
    error: String,
): AddUserFormUiState =
    updateDetail(field) { detail ->
        detail.copy(error = error)
    }

internal fun AddUserFormUiState.withoutError(field: AddUserField): AddUserFormUiState =
    updateDetail(field) { detail ->
        detail.copy(error = null)
    }

internal fun Throwable?.toAddUserValidationAlert(): AddUserValidationAlert? =
    toAddUserApiFieldErrors()
        .takeIf { it.isNotEmpty() }
        ?.let(::AddUserValidationAlert)

internal fun Throwable?.toUserMessage(): String =
    this?.userDataErrorOrNull()?.toUserMessage() ?: "The user directory could not be refreshed."

private fun UserRecord.toUiModel(
    now: Instant,
    relativeTimeFormatter: RelativeTimeFormatter,
): UserItemUiModel =
    UserItemUiModel(
        localId = user.localId,
        name = user.name,
        email = user.email,
        gender = user.gender,
        status = user.status,
        relativeTime = relativeTimeFormatter.format(user.observedAt, now),
    )

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
