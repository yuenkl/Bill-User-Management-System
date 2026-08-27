package com.bill.usermanagmentsystem.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddUserValidatorTest {
    private val validator = AddUserValidator()

    @Test
    fun nameRequiresTwoToEightyCharactersAndAUnicodeLetter() {
        assertEquals(NameValidationError.Required, validator.validateName(""))
        assertEquals(NameValidationError.Required, validator.validateName("   "))
        assertEquals(NameValidationError.TooShort, validator.validateName("A"))
        assertNull(validator.validateName("Al"))
        assertNull(validator.validateName("李 雷"))
        assertNull(validator.validateName("Mary-Jane O'Neil"))
        assertEquals(NameValidationError.MissingLetter, validator.validateName("--"))
        assertEquals(NameValidationError.ControlCharacter, validator.validateName("Ada\u0000Lovelace"))
        assertEquals(NameValidationError.TooLong, validator.validateName("A".repeat(81)))
    }

    @Test
    fun emailRejectsMalformedPartsWhitespaceAndLengthBoundaries() {
        assertEquals(EmailValidationError.Required, validator.validateEmail(""))
        assertEquals(EmailValidationError.LocalPartRequired, validator.validateEmail("@example.com"))
        assertEquals(EmailValidationError.InvalidDomain, validator.validateEmail("ada@"))
        assertEquals(EmailValidationError.ExactlyOneAt, validator.validateEmail("ada@@example.com"))
        assertEquals(EmailValidationError.Whitespace, validator.validateEmail("ada @example.com"))
        assertEquals(
            EmailValidationError.LocalPartTooLong,
            validator.validateEmail("a".repeat(65) + "@example.com"),
        )
        assertEquals(EmailValidationError.InvalidDomain, validator.validateEmail("ada@example..com"))
        assertEquals(EmailValidationError.InvalidDomain, validator.validateEmail("ada@-example.com"))
        assertEquals(EmailValidationError.InvalidDomain, validator.validateEmail("ada@example-.com"))
        assertEquals(EmailValidationError.FinalLabelTooShort, validator.validateEmail("ada@example.c"))
        assertEquals(
            EmailValidationError.TooLong,
            validator.validateEmail(
                "a".repeat(64) + "@" + "b".repeat(63) + "." +
                    "c".repeat(63) + "." + "d".repeat(62),
            ),
        )
    }

    @Test
    fun representativeEmailsAndOuterWhitespaceAreAccepted() {
        assertNull(validator.validateEmail("ada.lovelace+notes@example.co.uk"))
        assertNull(validator.validateEmail(" user@sub-domain.example "))
        assertEquals("Ada Lovelace", validator.normalize("  Ada Lovelace  "))
    }
}
