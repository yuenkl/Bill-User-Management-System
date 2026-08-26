package com.bill.usermanagmentsystem.data.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.bill.usermanagmentsystem.data.local.SqlDelightUserLocalDataSource
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.testing.FakeConnectivityObserver
import com.bill.usermanagmentsystem.data.testing.FakeTimeProvider
import com.bill.usermanagmentsystem.data.testing.FakeUserRemoteDataSource
import com.bill.usermanagmentsystem.data.testing.QueueIdGenerator
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSyncIntegrationTest {
    @Test
    fun offlineCreateSurvivesReopenAndLaterSynchronizesSameLocalRow() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val databaseName = "offline-create-sync.db"
        application.deleteDatabase(databaseName)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        var driver = AndroidSqliteDriver(UserManagementDatabase.Schema, application, databaseName)
        var local = SqlDelightUserLocalDataSource(
            database = UserManagementDatabase(driver),
            idGenerator = QueueIdGenerator("unused-before-reopen"),
            queryDispatcher = dispatcher,
        )

        try {
            local.insertPendingCreate(
                localId = "stable-local-id",
                mutationId = "durable-create",
                input = AddUserInput(
                    name = "Offline user",
                    email = "offline@example.com",
                    gender = Gender.Female,
                    status = UserStatus.Active,
                ),
                observedAt = instant(1_000),
            )

            driver.close()
            driver = AndroidSqliteDriver(UserManagementDatabase.Schema, application, databaseName)
            local = SqlDelightUserLocalDataSource(
                database = UserManagementDatabase(driver),
                idGenerator = QueueIdGenerator("snapshot-generated-id"),
                queryDispatcher = dispatcher,
            )

            val remote = FakeUserRemoteDataSource().apply {
                createHandler = {
                    RemoteResult.Success(remoteUser(remoteId = 44))
                }
                fetchHandler = {
                    RemoteResult.Success(listOf(remoteUser(remoteId = 44, serverPosition = 1)))
                }
            }
            val coordinator = DefaultSyncCoordinator(
                localDataSource = local,
                remoteDataSource = remote,
                connectivityObserver = FakeConnectivityObserver(),
                timeProvider = FakeTimeProvider(instant(2_000)),
                retryPolicy = RetryPolicy(),
                applicationScope = backgroundScope,
            )

            assertTrue(coordinator.sync().isSuccess)

            val stored = local.getUser("stable-local-id")!!
            assertEquals(44, stored.remoteId)
            assertEquals(instant(1_000), stored.observedAt)
            assertTrue(local.getAllMutations().isEmpty())
            assertEquals(1, remote.createRequests.size)
        } finally {
            driver.close()
            application.deleteDatabase(databaseName)
        }
    }

    private companion object {
        fun instant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

        fun remoteUser(
            remoteId: Long,
            serverPosition: Long? = null,
        ) = RemoteUser(
            remoteId = remoteId,
            name = "Offline user",
            email = "offline@example.com",
            gender = Gender.Female,
            status = UserStatus.Active,
            serverPosition = serverPosition,
        )
    }
}
