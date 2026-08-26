package com.bill.usermanagmentsystem.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserDataErrorTest {
    @Test
    fun typedErrorSurvivesKotlinResultFailure() {
        val expected = UserDataError.ValidationRejected("Email has already been taken.")
        val result = Result.failure<Unit>(UserDataException(expected))

        assertEquals(expected, result.exceptionOrNull()?.userDataErrorOrNull())
    }

    @Test
    fun unrelatedExceptionDoesNotPretendToBeDomainFailure() {
        assertNull(IllegalStateException("unexpected").userDataErrorOrNull())
    }
}
