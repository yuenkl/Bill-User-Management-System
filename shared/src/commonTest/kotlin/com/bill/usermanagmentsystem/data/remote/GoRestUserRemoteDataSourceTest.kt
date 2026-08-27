package com.bill.usermanagmentsystem.data.remote

import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.TimeProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class GoRestUserRemoteDataSourceTest {
    @Test
    fun onePageResponseUsesPageOneAndPreservesResponseOrder() = runRemoteTest { requests ->
        val source = source(
            engine = engine { request ->
                requests += request.url.toString()
                assertEquals(null, request.url.parameters["page"])
                assertEquals(null, request.url.parameters["per_page"])
                assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
                jsonResponse(usersJson(9, 3))
            },
        )

        val page = assertIs<RemoteResult.Success<RemotePage>>(source.fetchInitialPage()).value

        assertEquals(listOf("https://example.test/public/v2/users"), requests)
        assertEquals(1L, page.page)
        assertEquals(null, page.nextPage)
        assertEquals(listOf(9L, 3L), page.users.map(RemoteUser::remoteId))
        assertEquals(listOf(0L, 1L), page.users.map(RemoteUser::serverPosition))
    }

    @Test
    fun initialPageReadsTheNextPageLink() = runRemoteTest { requests ->
        val source = source(
            engine = engine { request ->
                val page = request.url.parameters["page"].orEmpty()
                requests += page
                jsonResponse(
                    usersJson(1),
                    nextLink = "https://example.test/public/v2/users?page=2",
                )
            },
        )

        val page = assertIs<RemoteResult.Success<RemotePage>>(source.fetchInitialPage()).value

        assertEquals(listOf(""), requests)
        assertEquals(1L, page.page)
        assertEquals(2L, page.nextPage)
        assertEquals(listOf(1L), page.users.map(RemoteUser::remoteId))
        assertEquals(listOf(0L), page.users.map(RemoteUser::serverPosition))
    }

    @Test
    fun invalidNextPageLinksArePermanentFailures() = runRemoteTest { requests ->
        listOf(
            "not-a-link",
            "https://example.test/public/v2/users?page=many",
            "https://example.test/public/v2/users?page=0",
            "https://example.test/public/v2/users?page=1",
        ).forEach { value ->
            val source = source(
                engine = engine { request ->
                    requests += request.url.parameters["page"].orEmpty()
                    jsonResponse(usersJson(1), nextLink = value)
                },
            )

            assertIs<RemoteResult.PermanentFailure>(source.fetchInitialPage())
        }
    }

    @Test
    fun requestedPageUsesItsNumberAndDoesNotRequirePaginationHeader() = runRemoteTest { requests ->
        val source = source(
            engine = engine { request ->
                val page = request.url.parameters["page"].orEmpty()
                requests += page
                jsonResponse(
                    usersJson(40, 41),
                    nextLink = "https://example.test/public/v2/users?page=4",
                )
            },
        )

        val result = assertIs<RemoteResult.Success<RemotePage>>(source.fetchPage(3)).value

        assertEquals(listOf("3"), requests)
        assertEquals(4L, result.nextPage)
        assertEquals(listOf(40L, 41L), result.users.map(RemoteUser::remoteId))
        assertEquals(listOf(20L, 21L), result.users.map(RemoteUser::serverPosition))
    }

    @Test
    fun pageResponseCanBeEmptyAndEndPagination() = runRemoteTest { requests ->
        val source = source(
            engine = engine { request ->
                val page = request.url.parameters["page"].orEmpty()
                requests += page
                jsonResponse("[]")
            },
        )

        val page = assertIs<RemoteResult.Success<RemotePage>>(source.fetchPage(3)).value

        assertEquals(listOf("3"), requests)
        assertEquals(3L, page.page)
        assertTrue(page.users.isEmpty())
        assertEquals(null, page.nextPage)
    }

    @Test
    fun malformedRequiredFieldIsAControlledPermanentFailure() = runRemoteTest { _ ->
        val source = source(
            engine = engine {
                jsonResponse(
                    """[{"id":1,"name":"Ada","email":"ada@example.com","gender":"female"}]""",
                )
            },
        )

        assertIs<RemoteResult.PermanentFailure>(source.fetchInitialPage())
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
            assertTrue(expectedType.isInstance(source.fetchInitialPage()))
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

        val result = assertIs<RemoteResult.RetryableFailure>(source.fetchInitialPage())

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
                jsonResponse(usersJson(1))
            },
            apiToken = "",
        )

        assertIs<RemoteResult.Success<RemotePage>>(source.fetchInitialPage())
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

    @Test
    fun apiLoggingRedactsAuthorizationAndExcludesRequestBodies() = runRemoteTest { _ ->
        val logger = RecordingLogger()
        val testToken = "test-api-token-not-for-logs"
        val source = source(
            engine = engine {
                respond(
                    content = userJson(7),
                    status = HttpStatusCode.Created,
                    headers = jsonHeaders(),
                )
            },
            apiToken = testToken,
            enableApiLogging = true,
            logger = logger,
        )

        assertIs<RemoteResult.Success<RemoteUser>>(
            source.createUser(
                CreateUserRequest(
                    name = "Ada Lovelace",
                    email = "ada@example.com",
                    gender = com.bill.usermanagmentsystem.domain.model.Gender.Female,
                    status = com.bill.usermanagmentsystem.domain.model.UserStatus.Active,
                ),
            ),
        )

        val messages = logger.messages.joinToString(separator = "\n")
        assertTrue(messages.contains("POST"))
        assertTrue(messages.contains("Authorization: ***"))
        assertFalse(messages.contains(testToken))
        assertFalse(messages.contains("Ada Lovelace"))
    }

    private fun source(
        engine: MockEngine,
        apiToken: String = "secret",
        enableApiLogging: Boolean = false,
        logger: Logger = NoOpLogger,
    ): GoRestUserRemoteDataSource = GoRestUserRemoteDataSource(
        httpClient = createGoRestHttpClient(
            engine = engine,
            enableApiLogging = enableApiLogging,
            logger = logger,
        ),
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
        nextLink: String? = null,
    ) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = jsonHeaders(nextLink),
    )

    private fun jsonHeaders(nextLink: String? = null): Headers = Headers.build {
        append(HttpHeaders.ContentType, "application/json")
        if (nextLink != null) append("X-Links-Next", nextLink)
    }

    private fun usersJson(vararg ids: Long): String = ids.joinToString(
        prefix = "[",
        postfix = "]",
    ) { id ->
        userJson(id)
    }

    private fun userJson(id: Long): String =
        """{"id":$id,"name":"User $id","email":"user$id@example.com","gender":"female","status":"active"}"""

    private class RecordingLogger : Logger {
        val messages = mutableListOf<String>()

        override fun log(message: String) {
            messages += message
        }
    }

    private object NoOpLogger : Logger {
        override fun log(message: String) = Unit
    }

    private fun runRemoteTest(block: suspend (MutableList<String>) -> Unit) =
        kotlinx.coroutines.test.runTest { block(mutableListOf()) }
}
