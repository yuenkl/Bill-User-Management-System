package com.bill.usermanagmentsystem.data.remote

import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.TimeProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class GoRestUserRemoteDataSourceTest {
    @Test
    fun onePageResponseUsesPageOneAndPreservesResponseOrder() = runRemoteTest { requests ->
        val source = source(
            engine = engine { request ->
                requests += request.url.parameters["page"].orEmpty()
                assertEquals("20", request.url.parameters["per_page"])
                jsonResponse(usersJson(9, 3), pageCount = "1")
            },
        )

        val page = assertIs<RemoteResult.Success<RemotePage>>(source.fetchLastPage()).value

        assertEquals(listOf("1"), requests)
        assertEquals(1L, page.page)
        assertEquals(1L, page.totalPages)
        assertEquals(listOf(9L, 3L), page.users.map(RemoteUser::remoteId))
        assertEquals(listOf(-20L, -19L), page.users.map(RemoteUser::serverPosition))
    }

    @Test
    fun pageCountProbeFetchesAndReturnsTheLastPage() = runRemoteTest { requests ->
        val source = source(
            engine = engine { request ->
                val page = request.url.parameters["page"].orEmpty()
                requests += page
                if (page == "1") {
                    jsonResponse(usersJson(1), pageCount = "4")
                } else {
                    jsonResponse(usersJson(40), pageCount = null)
                }
            },
        )

        val page = assertIs<RemoteResult.Success<RemotePage>>(source.fetchLastPage()).value

        assertEquals(listOf("1", "4"), requests)
        assertEquals(4L, page.page)
        assertEquals(4L, page.totalPages)
        assertEquals(listOf(40L), page.users.map(RemoteUser::remoteId))
        assertEquals(listOf(-80L), page.users.map(RemoteUser::serverPosition))
    }

    @Test
    fun missingAndNonNumericPaginationHeadersArePermanentFailures() = runRemoteTest { requests ->
        listOf<String?>(null, "many", "0").forEach { value ->
            val source = source(
                engine = engine { request ->
                    requests += request.url.parameters["page"].orEmpty()
                    jsonResponse(usersJson(1), pageCount = value)
                },
            )

            assertIs<RemoteResult.PermanentFailure>(source.fetchLastPage())
        }
    }

    @Test
    fun requestedPageUsesItsNumberAndDoesNotRequirePaginationHeader() = runRemoteTest { requests ->
        val source = source(
            engine = engine { request ->
                val page = request.url.parameters["page"].orEmpty()
                requests += page
                jsonResponse(usersJson(40, 41), pageCount = null)
            },
        )

        val result = assertIs<RemoteResult.Success<List<RemoteUser>>>(source.fetchPage(3)).value

        assertEquals(listOf("3"), requests)
        assertEquals(listOf(40L, 41L), result.map(RemoteUser::remoteId))
        assertEquals(listOf(-60L, -59L), result.map(RemoteUser::serverPosition))
    }

    @Test
    fun changedPaginationMetadataCanReturnAnEmptyLastPage() = runRemoteTest { requests ->
        val source = source(
            engine = engine { request ->
                val page = request.url.parameters["page"].orEmpty()
                requests += page
                if (page == "1") {
                    jsonResponse(usersJson(1), pageCount = "3")
                } else {
                    jsonResponse("[]", pageCount = null)
                }
            },
        )

        val page = assertIs<RemoteResult.Success<RemotePage>>(source.fetchLastPage()).value

        assertEquals(listOf("1", "3"), requests)
        assertEquals(3L, page.page)
        assertTrue(page.users.isEmpty())
    }

    @Test
    fun malformedRequiredFieldIsAControlledPermanentFailure() = runRemoteTest { _ ->
        val source = source(
            engine = engine {
                jsonResponse(
                    """[{"id":1,"name":"Ada","email":"ada@example.com","gender":"female"}]""",
                    pageCount = "1",
                )
            },
        )

        assertIs<RemoteResult.PermanentFailure>(source.fetchLastPage())
    }

    @Test
    fun statusCodesMapToTypedFailures() = runRemoteTest { _ ->
        val cases = listOf(
            HttpStatusCode.Unauthorized to RemoteResult.AuthenticationFailure::class,
            HttpStatusCode.Forbidden to RemoteResult.AuthenticationFailure::class,
            HttpStatusCode.TooManyRequests to RemoteResult.RetryableFailure::class,
            HttpStatusCode.ServiceUnavailable to RemoteResult.RetryableFailure::class,
            HttpStatusCode.BadRequest to RemoteResult.PermanentFailure::class,
        )

        cases.forEach { (status, expectedType) ->
            val source = source(engine { respond("{}", status) })
            assertTrue(expectedType.isInstance(source.fetchLastPage()))
        }
    }

    @Test
    fun rateLimitFailureHonorsServerRetryAfterTiming() = runRemoteTest { _ ->
        val source = source(
            engine = engine {
                respond(
                    content = "{}",
                    status = HttpStatusCode.TooManyRequests,
                    headers = Headers.build {
                        append(HttpHeaders.RetryAfter, "7")
                    },
                )
            },
        )

        val result = assertIs<RemoteResult.RetryableFailure>(source.fetchLastPage())

        assertEquals(Instant.fromEpochSeconds(1_007), result.serverRetryAt)
    }

    @Test
    fun validationPayloadIsRetainedForCreateFailure() = runRemoteTest { _ ->
        val source = source(
            engine = engine {
                respond(
                    content = """[{"field":"email","message":"has already been taken"}]""",
                    status = HttpStatusCode.UnprocessableEntity,
                    headers = jsonHeaders(),
                )
            },
        )

        val result = assertIs<RemoteResult.ValidationFailure>(
            source.createUser(
                CreateUserRequest(
                    name = "Ada",
                    email = "ada@example.com",
                    gender = com.bill.usermanagmentsystem.domain.model.Gender.Female,
                    status = com.bill.usermanagmentsystem.domain.model.UserStatus.Active,
                ),
            ),
        )

        assertEquals("email: has already been taken", result.reason)
    }

    @Test
    fun publicFetchWorksWithoutTokenWhileWritesFailFastForAuthentication() = runRemoteTest { _ ->
        var requestCount = 0
        val source = source(
            engine = engine {
                requestCount += 1
                jsonResponse(usersJson(1), pageCount = "1")
            },
            apiToken = "",
        )

        assertIs<RemoteResult.Success<RemotePage>>(source.fetchLastPage())
        assertEquals(
            RemoteResult.AuthenticationFailure,
            source.createUser(
                CreateUserRequest(
                    name = "Ada",
                    email = "ada@example.com",
                    gender = com.bill.usermanagmentsystem.domain.model.Gender.Female,
                    status = com.bill.usermanagmentsystem.domain.model.UserStatus.Active,
                ),
            ),
        )
        assertEquals(1, requestCount)
    }

    private fun source(
        engine: MockEngine,
        apiToken: String = "secret",
    ): GoRestUserRemoteDataSource = GoRestUserRemoteDataSource(
        httpClient = createGoRestHttpClient(engine),
        appConfig = AppConfig(apiToken = apiToken, baseUrl = "https://example.test/public/v2/"),
        timeProvider = object : TimeProvider {
            override fun now() = kotlin.time.Instant.fromEpochSeconds(1_000)
        },
    )

    private fun engine(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
            io.ktor.client.request.HttpRequestData,
        ) -> io.ktor.client.request.HttpResponseData,
    ): MockEngine = MockEngine { request -> handler(request) }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(
        content: String,
        pageCount: String?,
    ) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = jsonHeaders(pageCount),
    )

    private fun jsonHeaders(pageCount: String? = null): Headers = Headers.build {
        append(HttpHeaders.ContentType, "application/json")
        if (pageCount != null) append("X-Pagination-Pages", pageCount)
    }

    private fun usersJson(vararg ids: Long): String = ids.joinToString(
        prefix = "[",
        postfix = "]",
    ) { id ->
        """{"id":$id,"name":"User $id","email":"user$id@example.com","gender":"female","status":"active"}"""
    }

    private fun runRemoteTest(block: suspend (MutableList<String>) -> Unit) =
        kotlinx.coroutines.test.runTest { block(mutableListOf()) }
}
