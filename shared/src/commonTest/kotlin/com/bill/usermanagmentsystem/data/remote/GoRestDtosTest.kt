package com.bill.usermanagmentsystem.data.remote

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun missingOrNullUserFieldsDecodeAsNull() {
        val missingStatus =
            json.decodeFromString<GoRestUserDto>(
                """{"id":42,"name":"Ada","email":"ada@example.com","gender":"female"}""",
            )
        val nullStatus =
            json.decodeFromString<GoRestUserDto>(
                """{"id":42,"name":"Ada","email":"ada@example.com","gender":"female","status":null}""",
            )

        assertNull(missingStatus.status)
        assertNull(nullStatus.status)
    }

    @Test
    fun fieldErrorFixtureDecodes() {
        val errors =
            json.decodeFromString<List<GoRestFieldErrorDto>>(
                """[{"field":"email","message":"has already been taken"}]""",
            )

        assertEquals(GoRestFieldErrorDto("email", "has already been taken"), errors.single())
    }

    @Test
    fun nullableErrorFieldsDecode() {
        val error = json.decodeFromString<GoRestFieldErrorDto>("""{"field":null,"message":null}""")

        assertNull(error.field)
        assertNull(error.message)
    }
}
