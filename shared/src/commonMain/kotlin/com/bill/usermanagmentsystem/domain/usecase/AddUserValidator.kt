package com.bill.usermanagmentsystem.domain.usecase

class AddUserValidator {
    fun validateName(value: String): NameValidationError? {
        val normalized = value.trim()
        return when {
            normalized.isEmpty() -> NameValidationError.Required
            normalized.length < MIN_NAME_LENGTH -> NameValidationError.TooShort
            normalized.length > MAX_NAME_LENGTH -> NameValidationError.TooLong
            normalized.any(Char::isISOControl) -> NameValidationError.ControlCharacter
            normalized.none(Char::isLetter) -> NameValidationError.MissingLetter
            else -> null
        }
    }

    fun validateEmail(value: String): EmailValidationError? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return EmailValidationError.Required
        if (normalized.length > MAX_EMAIL_LENGTH) return EmailValidationError.TooLong
        if (normalized.any(Char::isWhitespace)) return EmailValidationError.Whitespace

        val atIndex = normalized.indexOf('@')
        if (atIndex < 0 || atIndex != normalized.lastIndexOf('@')) {
            return EmailValidationError.ExactlyOneAt
        }

        val localPart = normalized.substring(0, atIndex)
        if (localPart.isEmpty()) return EmailValidationError.LocalPartRequired
        if (localPart.length > MAX_LOCAL_PART_LENGTH) return EmailValidationError.LocalPartTooLong

        val domain = normalized.substring(atIndex + 1)
        val labels = domain.split('.')
        if (labels.any { !it.isValidDomainLabel() }) return EmailValidationError.InvalidDomain
        if (labels.last().length < MIN_FINAL_LABEL_LENGTH) {
            return EmailValidationError.FinalLabelTooShort
        }
        return null
    }

    fun normalize(value: String): String = value.trim()

    private fun String.isValidDomainLabel(): Boolean =
        isNotEmpty() &&
            length <= MAX_DOMAIN_LABEL_LENGTH &&
            first() != '-' &&
            last() != '-' &&
            all { character -> character.isLetterOrDigit() || character == '-' }

    private companion object {
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 80
        const val MAX_EMAIL_LENGTH = 254
        const val MAX_LOCAL_PART_LENGTH = 64
        const val MAX_DOMAIN_LABEL_LENGTH = 63
        const val MIN_FINAL_LABEL_LENGTH = 2
    }
}

enum class NameValidationError {
    Required,
    TooShort,
    TooLong,
    MissingLetter,
    ControlCharacter,
}

enum class EmailValidationError {
    Required,
    TooLong,
    ExactlyOneAt,
    Whitespace,
    LocalPartRequired,
    LocalPartTooLong,
    InvalidDomain,
    FinalLabelTooShort,
}
