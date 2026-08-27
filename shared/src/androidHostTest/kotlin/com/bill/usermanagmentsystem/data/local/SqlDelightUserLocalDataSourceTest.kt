package com.bill.usermanagmentsystem.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightUserLocalDataSourceTest {
    @Test
    fun snapshotThenPageMergeKeepsServerOrderAndExistingRows() =
        runTest {
            withFixture("page-merge") { source ->
                source.mergeSnapshot(
                    users = listOf(snapshot(remoteId = 41, name = "First page", position = 0)),
                    observedAt = instant(1_000),
                )
                source.mergePage(
                    users = listOf(snapshot(remoteId = 21, name = "Next page", position = 1)),
                    observedAt = instant(2_000),
                )

                val visible = source.observeUsers().first()
                assertEquals(listOf("First page", "Next page"), visible.map { it.user.name })
                assertEquals(instant(1_000), visible.first().user.observedAt)
                assertEquals(instant(2_000), visible.last().user.observedAt)
            }
        }

    @Test
    fun refreshSnapshotReplacesPreviouslyLoadedPages() =
        runTest {
            withFixture("snapshot-replacement") { source ->
                source.mergeSnapshot(
                    users = listOf(snapshot(remoteId = 41, name = "Initial", position = 0)),
                    observedAt = instant(1_000),
                )
                source.mergePage(
                    users = listOf(snapshot(remoteId = 21, name = "Old next page", position = 1)),
                    observedAt = instant(1_000),
                )

                source.mergeSnapshot(
                    users = listOf(snapshot(remoteId = 7, name = "Refreshed", position = 0)),
                    observedAt = instant(2_000),
                )

                assertEquals(listOf("Refreshed"), source.observeUsers().first().map { it.user.name })
            }
        }

    @Test
    fun existingRemoteUserIsUpdatedWithoutChangingItsFirstObservedTime() =
        runTest {
            withFixture("remote-upsert") { source ->
                source.mergeSnapshot(
                    users = listOf(snapshot(remoteId = 41, name = "Before", position = 0)),
                    observedAt = instant(1_000),
                )

                source.mergeSnapshot(
                    users = listOf(snapshot(remoteId = 41, name = "After", position = 0)),
                    observedAt = instant(2_000),
                )

                val user =
                    source
                        .observeUsers()
                        .first()
                        .single()
                        .user
                assertEquals("After", user.name)
                assertEquals(instant(1_000), user.observedAt)
            }
        }

    @Test
    fun locallyCreatedUsersWithEqualTimestampsUseDescendingLocalId() =
        runTest {
            withFixture("local-create-order") { source ->
                source.mergePage(
                    users = listOf(snapshot(remoteId = 41, name = "Older", position = null)),
                    observedAt = instant(1_000),
                )
                source.mergePage(
                    users = listOf(snapshot(remoteId = 42, name = "Newer", position = null)),
                    observedAt = instant(1_000),
                )

                assertEquals(
                    listOf("Newer", "Older"),
                    source.observeUsers().first().map { it.user.name },
                )
            }
        }

    @Test
    fun deleteRemovesTheConfirmedUser() =
        runTest {
            withFixture("confirmed-delete") { source ->
                source.mergeSnapshot(
                    users = listOf(snapshot(remoteId = 7, name = "Ada", position = 0)),
                    observedAt = instant(1_000),
                )
                val localId =
                    source
                        .observeUsers()
                        .first()
                        .single()
                        .user.localId

                source.deleteUser(localId)

                assertEquals(emptyList(), source.observeUsers().first())
                assertEquals(null, source.getUser(localId))
            }
        }

    private suspend fun <T> kotlinx.coroutines.test.TestScope.withFixture(
        suffix: String,
        block: suspend (SqlDelightUserLocalDataSource) -> T,
    ): T {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val databaseName = "user-management-$suffix.db"
        application.deleteDatabase(databaseName)
        val driver = AndroidSqliteDriver(UserManagementDatabase.Schema, application, databaseName)
        return try {
            block(
                SqlDelightUserLocalDataSource(
                    database = UserManagementDatabase(driver),
                    queryDispatcher = UnconfinedTestDispatcher(testScheduler),
                ),
            )
        } finally {
            driver.close()
            application.deleteDatabase(databaseName)
        }
    }

    private companion object {
        fun instant(epochMilliseconds: Long): Instant = Instant.fromEpochMilliseconds(epochMilliseconds)

        fun snapshot(
            remoteId: Long,
            name: String,
            position: Long?,
        ) = SnapshotUser(
            remoteId = remoteId,
            name = name,
            email = "$remoteId@example.com",
            gender = Gender.Female,
            status = UserStatus.Active,
            serverPosition = position,
        )
    }
}
