package com.bill.usermanagmentsystem.data.remote

import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.NetworkEngineFactory
import com.bill.usermanagmentsystem.platform.TimeProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val PAGE_SIZE = 10
private const val MAX_PAGE_NUMBER = Long.MAX_VALUE / PAGE_SIZE
private const val LINKS_NEXT_HEADER = "X-Links-Next"
private const val RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset"
private const val DEFAULT_VALIDATION_FAILURE_MESSAGE = "The server rejected the user details."

internal fun createGoRestHttpClient(
    engineFactory: NetworkEngineFactory,
    enableApiLogging: Boolean = false,
): HttpClient =
    createGoRestHttpClient(
        engine = engineFactory.create(),
        enableApiLogging = enableApiLogging,
        logger = engineFactory.apiLogger,
    )

internal fun createGoRestHttpClient(
    engine: HttpClientEngine,
    enableApiLogging: Boolean = false,
    logger: Logger = NoOpLogger,
): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(goRestJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15.seconds.inWholeMilliseconds
            connectTimeoutMillis = 10.seconds.inWholeMilliseconds
            socketTimeoutMillis = 15.seconds.inWholeMilliseconds
        }
        if (enableApiLogging) {
            install(Logging) {
                this.logger = logger
                level = LogLevel.HEADERS
                sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
            }
        }
    }

private object NoOpLogger : Logger {
    override fun log(message: String) = Unit
}

internal class GoRestUserRemoteDataSource(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val timeProvider: TimeProvider,
    private val networkDispatcher: CoroutineDispatcher,
) : UserRemoteDataSource {
    private val usersUrl = "${appConfig.baseUrl.trimEnd('/')}/users"

    override suspend fun fetchInitialPage(): RemoteResult<RemotePage> =
        withContext(networkDispatcher) {
            remoteCall {
                when (val initialPage = fetchPageResponse(page = null)) {
                    is PageResult.Failure -> initialPage.failure
                    is PageResult.Success ->
                        RemoteResult.Success(
                            RemotePage(
                                users = initialPage.users.toRemoteUsers(page = 1),
                                page = 1,
                                nextPage = initialPage.nextPage.after(page = 1),
                            ),
                        )
                }
            }
        }

    override suspend fun fetchPage(page: Long): RemoteResult<RemotePage> =
        withContext(networkDispatcher) {
            remoteCall {
                require(page in 1..MAX_PAGE_NUMBER) { "Page number is outside the supported range." }
                when (val result = fetchPageResponse(page = page)) {
                    is PageResult.Failure -> result.failure
                    is PageResult.Success ->
                        RemoteResult.Success(
                            RemotePage(
                                users = result.users.toRemoteUsers(page),
                                page = page,
                                nextPage = result.nextPage.after(page),
                            ),
                        )
                }
            }
        }

    override suspend fun createUser(request: CreateUserRequest): RemoteResult<RemoteUser> {
        if (appConfig.apiToken.isBlank()) return RemoteResult.AuthenticationFailure
        return withContext(networkDispatcher) {
            remoteCall {
                val response =
                    httpClient.post(usersUrl) {
                        bearerToken()
                        contentType(ContentType.Application.Json)
                        setBody(
                            GoRestCreateUserDto(
                                name = request.name,
                                email = request.email,
                                gender = request.gender.apiValue,
                                status = request.status.apiValue,
                            ),
                        )
                    }
                if (response.status == HttpStatusCode.Created) {
                    RemoteResult.Success(response.body<GoRestUserDto>().toRemoteUser(serverPosition = null))
                } else {
                    response.toFailure()
                }
            }
        }
    }

    override suspend fun deleteUser(remoteId: Long): RemoteResult<Unit> {
        if (appConfig.apiToken.isBlank()) return RemoteResult.AuthenticationFailure
        return withContext(networkDispatcher) {
            remoteCall {
                val response = httpClient.delete("$usersUrl/$remoteId") { bearerToken() }
                when {
                    response.status.value in 200..299 -> RemoteResult.Success(Unit)
                    response.status == HttpStatusCode.NotFound -> RemoteResult.NotFound
                    else -> response.toFailure()
                }
            }
        }
    }

    private suspend fun fetchPageResponse(page: Long?): PageResult {
        val response =
            httpClient.get(usersUrl) {
                header(HttpHeaders.CacheControl, "no-cache")
                bearerToken()
                page?.let { parameter("page", it) }
            }
        if (response.status.value !in 200..299) {
            return PageResult.Failure(response.toFailure())
        }

        val nextPage =
            response.headers[LINKS_NEXT_HEADER]
                ?.trim()
                ?.let(::parseNextPage)
                ?: return PageResult.Success(response.body(), nextPage = null)
        return PageResult.Success(response.body(), nextPage)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearerToken() {
        appConfig.apiToken
            .takeIf(String::isNotBlank)
            ?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
    }

    private suspend fun HttpResponse.toFailure(): RemoteResult<Nothing> =
        when (status.value) {
            401, 403 -> RemoteResult.AuthenticationFailure
            422 -> RemoteResult.ValidationFailure(readValidationMessage())
            429 ->
                RemoteResult.RetryableFailure(
                    reason = "The service is rate limited.",
                    serverRetryAt = retryAt(),
                )
            in 500..599 -> RemoteResult.RetryableFailure("The service returned HTTP ${status.value}.")
            else -> RemoteResult.PermanentFailure("The service returned HTTP ${status.value}.")
        }

    private suspend fun HttpResponse.readValidationMessage(): String {
        val payload = bodyAsText()
        return try {
            goRestJson
                .decodeFromString<List<GoRestFieldErrorDto>>(payload)
                .mapNotNull(GoRestFieldErrorDto::toValidationMessage)
                .joinToString(separator = "; ")
                .ifBlank { DEFAULT_VALIDATION_FAILURE_MESSAGE }
        } catch (_: SerializationException) {
            try {
                goRestJson
                    .decodeFromString<GoRestMessageErrorDto>(payload)
                    .message
                    ?.takeIf(String::isNotBlank)
                    ?: DEFAULT_VALIDATION_FAILURE_MESSAGE
            } catch (_: SerializationException) {
                DEFAULT_VALIDATION_FAILURE_MESSAGE
            }
        }
    }

    private fun HttpResponse.retryAt(): Instant? {
        headers[RATE_LIMIT_RESET_HEADER]
            ?.trim()
            ?.toLongOrNull()
            ?.let(Instant::fromEpochSeconds)
            ?.let { return it }
        return headers[HttpHeaders.RetryAfter]
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?.let { seconds -> timeProvider.now() + seconds.seconds }
    }

    private sealed interface PageResult {
        data class Success(
            val users: List<GoRestUserDto>,
            val nextPage: Long?,
        ) : PageResult

        data class Failure(
            val failure: RemoteResult<Nothing>,
        ) : PageResult
    }
}

private fun parseNextPage(nextLink: String): Long {
    val pageValue =
        nextLink
            .substringAfter("?", missingDelimiterValue = "")
            .split("&")
            .firstOrNull { it.substringBefore("=") == "page" }
            ?.substringAfter("=", missingDelimiterValue = "")
            ?.toLongOrNull()
    return requireNotNull(pageValue?.takeIf { it in 1..MAX_PAGE_NUMBER }) {
        "The service returned an invalid next-page link."
    }
}

private fun Long?.after(page: Long): Long? =
    this?.also { nextPage ->
        require(nextPage > page) { "The service returned a non-forward next-page link." }
    }

private inline fun <T> remoteCall(block: () -> RemoteResult<T>): RemoteResult<T> =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: HttpRequestTimeoutException) {
        RemoteResult.RetryableFailure("The request timed out.")
    } catch (failure: IOException) {
        RemoteResult.RetryableFailure(failure.message ?: "The network request failed.")
    } catch (failure: SerializationException) {
        RemoteResult.PermanentFailure(failure.message ?: "The service returned malformed data.")
    } catch (failure: IllegalArgumentException) {
        RemoteResult.PermanentFailure(failure.message ?: "The service returned invalid data.")
    } catch (failure: Throwable) {
        RemoteResult.PermanentFailure(failure.message ?: "The remote request failed unexpectedly.")
    }

private fun GoRestUserDto.toRemoteUser(serverPosition: Long?): RemoteUser {
    val responseId = requireNotNull(id) { "The service returned a user without an ID." }
    require(responseId > 0) { "The service returned an invalid user ID." }
    val responseName = requireNotNull(name) { "The service returned a user without a name." }
    val responseEmail = requireNotNull(email) { "The service returned a user without an email." }
    val responseGender = requireNotNull(gender) { "The service returned a user without a gender." }
    val responseStatus = requireNotNull(status) { "The service returned a user without a status." }
    val parsedGender =
        Gender.entries.firstOrNull { it.apiValue == responseGender }
            ?: throw IllegalArgumentException("The service returned an unsupported gender value.")
    val parsedStatus =
        UserStatus.entries.firstOrNull { it.apiValue == responseStatus }
            ?: throw IllegalArgumentException("The service returned an unsupported status value.")
    return RemoteUser(
        remoteId = responseId,
        name = responseName,
        email = responseEmail,
        gender = parsedGender,
        status = parsedStatus,
        serverPosition = serverPosition,
    )
}

private fun List<GoRestUserDto>.toRemoteUsers(page: Long): List<RemoteUser> =
    mapIndexed { index, user ->
        val serverPosition = ((page - 1) * PAGE_SIZE) + index
        user.toRemoteUser(serverPosition)
    }

private fun GoRestFieldErrorDto.toValidationMessage(): String? {
    val responseField = field?.trim().orEmpty()
    val responseMessage = message?.trim().orEmpty()
    return when {
        responseField.isNotEmpty() && responseMessage.isNotEmpty() -> "$responseField: $responseMessage"
        responseMessage.isNotEmpty() -> responseMessage
        responseField.isNotEmpty() -> responseField
        else -> null
    }
}

private val goRestJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    }
