package com.bill.usermanagmentsystem.data.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.bill.usermanagmentsystem.data.local.SnapshotUser
import com.bill.usermanagmentsystem.data.local.SqlDelightUserLocalDataSource
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.testing.FakeConnectivityObserver
import com.bill.usermanagmentsystem.data.testing.FakeTimeProvider
import com.bill.usermanagmentsystem.data.testing.FakeUserRemoteDataSource
import com.bill.usermanagmentsystem.data.testing.QueueIdGenerator
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSyncIntegrationTest {
    @Test
    fun offlineDeleteSurvivesReopenAndSendsOneDeleteAfterReconnection() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val databaseName = "offline-delete-sync.db"
        application.deleteDatabase(databaseName)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        var driver = AndroidSqliteDriver(UserManagementDatabase.Schema, application, databaseName)
        var local = newLocalDataSource(driver, dispatcher, QueueIdGenerator("delete-mutation"))

        try {
            local.mergeSnapshot(listOf(snapshot(remoteId = 77)), instant(1_000))
            val localId = local.observeVisibleUsers().first().single().user.localId
            local.requestDelete(localId, instant(6_000))
            val offlineCoordinator = DefaultSyncCoordinator(
                localDataSource = local,
                remoteDataSource = FakeUserRemoteDataSource(),
                connectivityObserver = FakeConnectivityObserver(ConnectivityStatus.Unavailable),
                timeProvider = FakeTimeProvider(instant(6_000)),
                retryPolicy = RetryPolicy(),
                applicationScope = backgroundScope,
            )

            val offlineResult = offlineCoordinator.sync()
            assertEquals(UserDataError.Offline, offlineResult.exceptionOrNull()?.userDataErrorOrNull())
            assertTrue(local.getUser(localId)!!.hidden)

            driver.close()
            driver = AndroidSqliteDriver(UserManagementDatabase.Schema, application, databaseName)
            local = newLocalDataSource(driver, dispatcher, QueueIdGenerator("unused-after-reopen"))
            val remote = FakeUserRemoteDataSource().apply {
                deleteHandler = { RemoteResult.Success(Unit) }
                fetchHandler = { RemoteResult.Success(emptyList()) }
            }
            val onlineCoordinator = DefaultSyncCoordinator(
                localDataSource = local,
                remoteDataSource = remote,
                connectivityObserver = FakeConnectivityObserver(),
                timeProvider = FakeTimeProvider(instant(7_000)),
                retryPolicy = RetryPolicy(),
                applicationScope = backgroundScope,
            )

            assertTrue(onlineCoordinator.sync().isSuccess)
            assertEquals(listOf(77L), remote.deleteRequests)
            assertNull(local.getUser(localId))
            assertTrue(local.getAllMutations().isEmpty())
        } finally {
            driver.close()
            application.deleteDatabase(databaseName)
        }
    }

    private fun newLocalDataSource(
        driver: AndroidSqliteDriver,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        idGenerator: QueueIdGenerator,
    ) = SqlDelightUserLocalDataSource(
        database = UserManagementDatabase(driver),
        idGenerator = idGenerator,
        queryDispatcher = dispatcher,
    )

    private companion object {
        fun instant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

        fun snapshot(remoteId: Long) = SnapshotUser(
            remoteId = remoteId,
            name = "Offline user",
            email = "offline@example.com",
            gender = Gender.Female,
            status = UserStatus.Active,
            serverPosition = 0,
        )
    }
}
