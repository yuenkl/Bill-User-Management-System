package com.bill.usermanagmentsystem.ui.users

import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.ui.users.presentation.toUserMessage
import com.bill.usermanagmentsystem.utils.AddUserValidator
import com.bill.usermanagmentsystem.utils.EmailValidationError
import com.bill.usermanagmentsystem.utils.NameValidationError

internal fun createAddUserFormState(
    current: AddUserFormUiState = AddUserFormUiState(),
    validator: AddUserValidator,
): AddUserFormUiState {
    val nameValidation = validator.validateName(current.valueFor(AddUserFormEntryType.Name).orEmpty())
    val emailValidation = validator.validateEmail(current.valueFor(AddUserFormEntryType.Email).orEmpty())
    var form = current
    if (AddUserFormEntryType.Name in current.touchedFields && nameValidation != null) {
        form = form.withError(AddUserFormEntryType.Name, nameValidation.userMessage())
    }
    if (AddUserFormEntryType.Email in current.touchedFields && emailValidation != null) {
        form = form.withError(AddUserFormEntryType.Email, emailValidation.userMessage())
    }
    if (AddUserFormEntryType.Gender in current.touchedFields && current.gender() == null) {
        form = form.withError(AddUserFormEntryType.Gender, "Choose a gender.")
    }
    return form.copy(
        isValid = nameValidation == null && emailValidation == null && current.gender() != null,
    )
}

internal fun AddUserFormUiState.withSubmissionFailure(failure: Throwable?): AddUserFormUiState {
    val fieldErrors = failure.toAddUserApiFieldErrors()
    var form = copy(submitting = false)
    val fieldsWithErrors =
        fieldErrors.mapNotNull { error ->
            AddUserFormEntryType.fromApiName(error.field)?.also { field ->
                form = form.withError(field, error.message)
            }
        }
    return if (fieldsWithErrors.isNotEmpty()) {
        form
    } else {
        form.withError(AddUserFormEntryType.Form, failure.toAddUserMessage())
    }
}

internal fun AddUserFormUiState.withValue(
    field: AddUserFormEntryType,
    value: String,
): AddUserFormUiState =
    updateDetail(field) { detail ->
        detail.copy(value = value, error = null)
    }

internal fun AddUserFormUiState.withError(
    field: AddUserFormEntryType,
    error: String,
): AddUserFormUiState =
    updateDetail(field) { detail ->
        detail.copy(error = error)
    }

internal fun AddUserFormUiState.withoutError(field: AddUserFormEntryType): AddUserFormUiState =
    updateDetail(field) { detail ->
        detail.copy(error = null)
    }

internal fun Throwable?.toAddUserValidationAlert(): AddUserValidationAlert? =
    toAddUserApiFieldErrors()
        .takeIf { it.isNotEmpty() }
        ?.let(::AddUserValidationAlert)

private fun AddUserFormUiState.updateDetail(
    field: AddUserFormEntryType,
    transform: (AddUserFormEntry) -> AddUserFormEntry,
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
                        updatedDetails + transform(AddUserFormEntry(type = field))
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
