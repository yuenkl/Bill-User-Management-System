package com.bill.usermanagmentsystem.data.repository

import com.bill.usermanagmentsystem.data.local.StoredUser
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.testing.FakeConnectivityObserver
import com.bill.usermanagmentsystem.data.testing.FakeTimeProvider
import com.bill.usermanagmentsystem.data.testing.FakeUserLocalDataSource
import com.bill.usermanagmentsystem.data.testing.FakeUserRemoteDataSource
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class OfflineFirstUserRepositoryTest {
    @Test
    fun refreshStoresTheInitialApiPageAndInitializesPagination() =
        runTest {
            val local = FakeUserLocalDataSource()
            val remote =
                FakeUserRemoteDataSource().apply {
                    totalPages = 2
                    fetchHandler = { RemoteResult.Success(listOf(remoteUser(remoteId = 42))) }
                    pageHandler = { RemoteResult.Success(emptyList()) }
                }
            val repository = repository(local, remote)

            assertTrue(repository.refresh().isSuccess)
            assertEquals(listOf(42L), local.mergedSnapshots.single().map { it.remoteId })

            assertTrue(repository.loadNextPage().isSuccess)
            assertEquals(listOf(2L), remote.pageRequests)
        }

    @Test
    fun nextPageFailureKeepsTheCursorForAnExplicitRetry() =
        runTest {
            val remote =
                FakeUserRemoteDataSource().apply {
                    totalPages = 2
                    fetchHandler = { RemoteResult.Success(emptyList()) }
                    pageHandler = { RemoteResult.RetryableFailure("Service unavailable") }
                }
            val repository = repository(FakeUserLocalDataSource(), remote)

            assertTrue(repository.refresh().isSuccess)
            assertTrue(repository.loadNextPage().isFailure)

            remote.pageHandler = { RemoteResult.Success(listOf(remoteUser(remoteId = 7))) }
            assertTrue(repository.loadNextPage().isSuccess)
            assertEquals(listOf(2L, 2L), remote.pageRequests)
        }

    @Test
    fun refreshAndPagingRejectOfflineWorkWithoutCallingTheApi() =
        runTest {
            val remote = FakeUserRemoteDataSource()
            val repository =
                repository(
                    local = FakeUserLocalDataSource(),
                    remote = remote,
                    connectivity = FakeConnectivityObserver(ConnectivityStatus.Unavailable),
                )

            val refresh = repository.refresh()

            assertEquals(UserDataError.Offline, refresh.exceptionOrNull()?.userDataErrorOrNull())
            assertEquals(0, remote.fetchCalls)
        }

    @Test
    fun addStoresTheUserOnlyAfterRemoteCreationSucceeds() =
        runTest {
            val local = FakeUserLocalDataSource()
            val remote =
                FakeUserRemoteDataSource().apply {
                    createHandler = { RemoteResult.Success(remoteUser(remoteId = 99)) }
                }
            val repository = repository(local, remote)

            assertEquals("99", repository.addUser(input()).getOrNull())
            assertEquals(listOf(99L), local.mergedPages.single().map { it.remoteId })
        }

    @Test
    fun validationFailureDoesNotInsertALocalUser() =
        runTest {
            val local = FakeUserLocalDataSource()
            val remote =
                FakeUserRemoteDataSource().apply {
                    createHandler = { RemoteResult.ValidationFailure("email: has already been taken") }
                }
            val repository = repository(local, remote)

            val result = repository.addUser(input())

            assertEquals(
                UserDataError.ValidationRejected("email: has already been taken"),
                result.exceptionOrNull()?.userDataErrorOrNull(),
            )
            assertTrue(local.mergedPages.isEmpty())
        }

    @Test
    fun confirmedDeleteWaitsForRemoteSuccessBeforeRemovingTheLocalUser() =
        runTest {
            val local = FakeUserLocalDataSource().apply { storedUsers["1"] = storedUser() }
            val remote =
                FakeUserRemoteDataSource().apply {
                    deleteHandler = { RemoteResult.Success(Unit) }
                }
            val repository = repository(local, remote)

            val deleted = repository.deleteImmediately("1").getOrThrow()

            assertEquals(listOf(42L), remote.deleteRequests)
            assertEquals("Local user", deleted.userName)
            assertTrue("1" !in local.storedUsers)
        }

    @Test
    fun failedRemoteDeleteKeepsTheLocalUser() =
        runTest {
            val stored = storedUser()
            val local = FakeUserLocalDataSource().apply { storedUsers["1"] = stored }
            val remote =
                FakeUserRemoteDataSource().apply {
                    deleteHandler = { RemoteResult.RetryableFailure("Offline") }
                }

            val result = repository(local, remote).deleteImmediately("1")

            assertTrue(result.isFailure)
            assertEquals(stored, local.storedUsers["1"])
        }

    @Test
    fun undoRecreatesTheUserThroughPostAndMergesTheResponse() =
        runTest {
            val local = FakeUserLocalDataSource()
            val remote =
                FakeUserRemoteDataSource().apply {
                    createHandler = { request ->
                        RemoteResult.Success(
                            remoteUser(
                                remoteId = 99,
                                name = request.name,
                                email = request.email,
                            ),
                        )
                    }
                }
            val repository = repository(local, remote)

            assertEquals("99", repository.restoreDeletedUser(input()).getOrNull())
            assertEquals(1, remote.createRequests.size)
            assertEquals(listOf(99L), local.mergedPages.single().map { it.remoteId })
        }

    private fun repository(
        local: FakeUserLocalDataSource,
        remote: FakeUserRemoteDataSource,
        connectivity: FakeConnectivityObserver = FakeConnectivityObserver(),
    ) = OfflineFirstUserRepository(
        localDataSource = local,
        remoteDataSource = remote,
        connectivityObserver = connectivity,
        timeProvider = FakeTimeProvider(instant(1_000)),
    )

    private companion object {
        fun instant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

        fun input() =
            AddUserInput(
                name = "Ada Lovelace",
                email = "ada@example.com",
                gender = Gender.Female,
                status = UserStatus.Active,
            )

        fun remoteUser(
            remoteId: Long,
            name: String = "Ada Lovelace",
            email: String = "ada@example.com",
        ) = RemoteUser(
            remoteId = remoteId,
            name = name,
            email = email,
            gender = Gender.Female,
            status = UserStatus.Active,
            serverPosition = null,
        )

        fun storedUser() =
            StoredUser(
                localId = "1",
                remoteId = 42,
                name = "Local user",
                email = "local@example.com",
                gender = Gender.Female,
                status = UserStatus.Active,
                observedAt = instant(1_000),
                serverPosition = 0,
            )
    }
}
