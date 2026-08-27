package com.bill.usermanagmentsystem.data.remote

import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import kotlin.time.Instant

internal interface UserRemoteDataSource {
    /** Discovers and fetches the current final page returned by the public users endpoint. */
    suspend fun fetchLastPage(): RemoteResult<RemotePage>

    suspend fun fetchPreviousPage(page: Long): RemoteResult<RemotePage>

    suspend fun createUser(request: CreateUserRequest): RemoteResult<RemoteUser>

    suspend fun deleteUser(remoteId: Long): RemoteResult<Unit>
}

internal data class RemotePage(
    val users: List<RemoteUser>,
    val page: Long,
    val previousPage: Long?,
)

internal data class CreateUserRequest(
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
)

internal data class RemoteUser(
    val remoteId: Long,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val serverPosition: Long?,
)

internal sealed interface RemoteResult<out T> {
    data class Success<T>(
        val value: T,
    ) : RemoteResult<T>

    data class RetryableFailure(
        val reason: String,
        val serverRetryAt: Instant? = null,
    ) : RemoteResult<Nothing>

    data object AuthenticationFailure : RemoteResult<Nothing>

    data class ValidationFailure(
        val reason: String,
    ) : RemoteResult<Nothing>

    data object NotFound : RemoteResult<Nothing>

    data class PermanentFailure(
        val reason: String,
    ) : RemoteResult<Nothing>
}
