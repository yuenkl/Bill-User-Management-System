package com.bill.usermanagmentsystem.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoRestDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun userFixtureDecodesRequiredFieldsAndIgnoresUnknownFields() {
        val user =
            json.decodeFromString<GoRestUserDto>(
                """{"id":42,"name":"Ada","email":"ada@example.com","gender":"female","status":"active","extra":true}""",
            )

        assertEquals(42, user.id)
        assertEquals("Ada", user.name)
    }

    @Test
    fun missingRequiredUserFieldIsRejected() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<GoRestUserDto>(
                """{"id":42,"name":"Ada","email":"ada@example.com","gender":"female"}""",
            )
        }
    }

    @Test
    fun fieldErrorFixtureDecodes() {
        val errors =
            json.decodeFromString<List<GoRestFieldErrorDto>>(
                """[{"field":"email","message":"has already been taken"}]""",
            )

        assertEquals(GoRestFieldErrorDto("email", "has already been taken"), errors.single())
    }
}
