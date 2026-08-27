package com.bill.usermanagmentsystem.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.UserSynchronization
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightUserLocalDataSourceTest {
    @Test
    fun visibleUsersPutPendingCreatesFirstAndExcludeHiddenRows() = runTest {
        withFixture("visible-ordering") {
            source.mergeSnapshot(
                users = listOf(
                    snapshot(remoteId = 20, name = "Second", position = 2),
                    snapshot(remoteId = 10, name = "First", position = 1),
                ),
                observedAt = instant(1_000),
            )
            source.insertPendingCreate(
                mutationId = "create-pending",
                input = input(name = "Pending"),
                observedAt = instant(2_000),
            )

            assertEquals(
                listOf("Pending", "First", "Second"),
                source.observeVisibleUsers().first().map { it.user.name },
            )

            val first = source.observeVisibleUsers().first().first { it.user.name == "First" }
            source.requestDelete(first.user.localId, instant(8_000))

            assertEquals(
                listOf("Pending", "Second"),
                source.observeVisibleUsers().first().map { it.user.name },
            )
        }
    }

    @Test
    fun createAndOutboxInsertRollbackTogetherWhenMutationInsertFails() = runTest {
        withFixture("atomic-create") {
            val firstLocalId = source.insertPendingCreate(
                mutationId = "duplicate-mutation",
                input = input(name = "First"),
                observedAt = instant(1_000),
            )

            assertFails {
                source.insertPendingCreate(
                    mutationId = "duplicate-mutation",
                    input = input(name = "Must roll back"),
                    observedAt = instant(2_000),
                )
            }

            assertEquals(listOf(firstLocalId), source.getAllMutations().map { it.userLocalId })
        }
    }

    @Test
    fun createSuccessKeepsStableLocalIdentityAndObservedTime() = runTest {
        withFixture("create-success") {
            val localId = source.insertPendingCreate(
                mutationId = "create-id",
                input = input(name = "Local name"),
                observedAt = instant(1_000),
            )

            source.completeCreate(
                mutationId = "create-id",
                localId = localId,
                remoteUser = snapshot(remoteId = 42, name = "Server name", position = 0),
            )

            val user = source.getUser(localId)!!
            assertEquals(localId, user.localId)
            assertEquals(42, user.remoteId)
            assertEquals("Server name", user.name)
            assertEquals(instant(1_000), user.observedAt)
            assertEquals(StoredUserSyncStatus.Synced, user.synchronization)
            assertTrue(source.getAllMutations().isEmpty())
        }
    }

    @Test
    fun completedCreatesAppearNewestFirstAfterTheyReceiveRemoteIds() = runTest {
        withFixture("completed-create-ordering") {
            val olderLocalId = source.insertPendingCreate(
                mutationId = "older-create",
                input = input(name = "Zoe older"),
                observedAt = instant(1_000),
            )
            source.completeCreate(
                mutationId = "older-create",
                localId = olderLocalId,
                remoteUser = snapshot(remoteId = 42, name = "Zoe older", position = null),
            )
            val newerLocalId = source.insertPendingCreate(
                mutationId = "newer-create",
                input = input(name = "Ada newer"),
                observedAt = instant(2_000),
            )
            source.completeCreate(
                mutationId = "newer-create",
                localId = newerLocalId,
                remoteUser = snapshot(remoteId = 43, name = "Ada newer", position = null),
            )

            assertEquals(
                listOf("Ada newer", "Zoe older"),
                source.observeVisibleUsers().first().map { it.user.name },
            )
        }
    }

    @Test
    fun refreshRemovesCompletedLocalCreateThatIsAbsentFromApiSnapshot() = runTest {
        withFixture("completed-create-refresh") {
            val localId = source.insertPendingCreate(
                mutationId = "create-id",
                input = input(name = "Created user"),
                observedAt = instant(1_000),
            )
            source.completeCreate(
                mutationId = "create-id",
                localId = localId,
                remoteUser = snapshot(remoteId = 42, name = "Created user", position = null),
            )

            source.mergeSnapshot(
                users = listOf(snapshot(remoteId = 77, name = "API user", position = 0)),
                observedAt = instant(2_000),
            )

            assertNull(source.getUser(localId))
            assertEquals(
                listOf("API user"),
                source.observeVisibleUsers().first().map { it.user.name },
            )
        }
    }

    @Test
    fun refreshMovesCompletedLocalCreateToItsApiPosition() = runTest {
        withFixture("completed-create-api-position") {
            val localId = source.insertPendingCreate(
                mutationId = "create-id",
                input = input(name = "Created user"),
                observedAt = instant(1_000),
            )
            source.completeCreate(
                mutationId = "create-id",
                localId = localId,
                remoteUser = snapshot(remoteId = 42, name = "Created user", position = null),
            )

            source.mergeSnapshot(
                users = listOf(
                    snapshot(remoteId = 77, name = "First API user", position = 0),
                    snapshot(remoteId = 42, name = "Created user", position = 1),
                ),
                observedAt = instant(2_000),
            )

            assertEquals(localId, source.getUser(localId)?.localId)
            assertEquals(1, source.getUser(localId)?.serverPosition)
            assertEquals(
                listOf("First API user", "Created user"),
                source.observeVisibleUsers().first().map { it.user.name },
            )
        }
    }

    @Test
    fun pendingCreateSurvivesDatabaseReopen() = runTest {
        withFixture("create-reopen") {
            val localId = source.insertPendingCreate(
                mutationId = "offline-create",
                input = input(name = "Offline"),
                observedAt = instant(1_000),
            )

            reopen()

            assertEquals(StoredUserSyncStatus.PendingCreate, source.getUser(localId)?.synchronization)
            assertEquals("offline-create", source.getAllMutations().single().mutationId)
        }
    }

    @Test
    fun existingTextIdsMigrateToIncreasingDatabaseAssignedIds() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val databaseName = "user-management-numeric-id-migration.db"
        application.deleteDatabase(databaseName)
        val legacy = application.openOrCreateDatabase(databaseName, 0, null)
        legacy.execSQL(
            """
            CREATE TABLE users (
                local_id TEXT NOT NULL PRIMARY KEY,
                remote_id INTEGER UNIQUE,
                name TEXT NOT NULL,
                email TEXT NOT NULL,
                gender TEXT NOT NULL,
                status TEXT NOT NULL,
                observed_at_epoch_ms INTEGER NOT NULL,
                server_position INTEGER,
                sync_status TEXT NOT NULL CHECK (
                    sync_status IN ('SYNCED', 'PENDING_CREATE', 'CREATE_FAILED', 'PENDING_DELETE')
                ),
                hidden INTEGER NOT NULL DEFAULT 0,
                undo_deadline_epoch_ms INTEGER,
                last_sync_error TEXT
            )
            """.trimIndent(),
        )
        legacy.execSQL(
            """
            CREATE TABLE pending_mutations (
                mutation_id TEXT NOT NULL PRIMARY KEY,
                user_local_id TEXT NOT NULL,
                kind TEXT NOT NULL CHECK (kind IN ('CREATE', 'DELETE')),
                created_at_epoch_ms INTEGER NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                state TEXT NOT NULL CHECK (state IN ('PENDING', 'RETRYABLE_WAIT', 'BLOCKED')),
                retry_at_epoch_ms INTEGER,
                last_error TEXT,
                FOREIGN KEY (user_local_id) REFERENCES users(local_id) ON DELETE CASCADE,
                UNIQUE (user_local_id, kind)
            )
            """.trimIndent(),
        )
        legacy.execSQL(
            """
            INSERT INTO users VALUES (
                'legacy-id', 44, 'Legacy user', 'legacy@example.com', 'female', 'active',
                1000, NULL, 'SYNCED', 0, NULL, NULL
            )
            """.trimIndent(),
        )
        legacy.execSQL(
            """
            INSERT INTO pending_mutations VALUES (
                'legacy-create', 'legacy-id', 'CREATE', 1000, 0, 'PENDING', NULL, NULL
            )
            """.trimIndent(),
        )
        legacy.version = 1
        legacy.close()

        val driver = AndroidSqliteDriver(UserManagementDatabase.Schema, application, databaseName)
        val source = SqlDelightUserLocalDataSource(
            database = UserManagementDatabase(driver),
            idGenerator = CountingIdGenerator(),
            queryDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        try {
            assertEquals(44, source.getUser("1")?.remoteId)
            assertEquals("1", source.getAllMutations().single().userLocalId)

            assertEquals(
                "2",
                source.insertPendingCreate(
                    mutationId = "new-create",
                    input = input(name = "New user"),
                    observedAt = instant(2_000),
                ),
            )
        } finally {
            driver.close()
            application.deleteDatabase(databaseName)
        }
    }

    @Test
    fun deletingPendingCreateCancelsCreateWithoutDeleteMutation() = runTest {
        withFixture("cancel-create") {
            val localId = source.insertPendingCreate(
                mutationId = "create-local-only",
                input = input(),
                observedAt = instant(1_000),
            )

            source.requestDelete(localId, instant(6_000))

            assertNull(source.getUser(localId))
            assertTrue(source.getAllMutations().isEmpty())
        }
    }

    @Test
    fun undoBeforeDeadlineRestoresUserWithoutDeleteMutation() = runTest {
        withFixture("undo-delete") {
            source.mergeSnapshot(listOf(snapshot(remoteId = 7)), instant(1_000))
            val user = source.observeVisibleUsers().first().single().user
            source.requestDelete(user.localId, instant(6_000))

            source.undoDelete(user.localId, instant(5_999))

            assertEquals(user.localId, source.observeVisibleUsers().first().single().user.localId)
            assertTrue(source.getAllMutations().isEmpty())
        }
    }

    @Test
    fun undoableDeletionProjectionSurvivesDatabaseReopen() = runTest {
        withFixture("undo-projection-reopen") {
            source.mergeSnapshot(listOf(snapshot(remoteId = 7, name = "Ada")), instant(1_000))
            val localId = source.observeVisibleUsers().first().single().user.localId
            source.requestDelete(localId, instant(6_000))

            reopen()

            val deletion = source.observeUndoableUsers().first().single()
            assertEquals(localId, deletion.user.localId)
            assertEquals("Ada", deletion.user.name)
            assertEquals(instant(6_000), deletion.deadline)
        }
    }

    @Test
    fun undoAtDeadlineIsRejectedAndDurableDeleteStateRemains() = runTest {
        withFixture("late-undo") {
            source.mergeSnapshot(listOf(snapshot(remoteId = 7)), instant(1_000))
            val localId = source.observeVisibleUsers().first().single().user.localId
            source.requestDelete(localId, instant(6_000))

            val failure = assertFails { source.undoDelete(localId, instant(6_000)) }

            assertEquals(UserDataError.DeleteTooLate, assertIs<UserDataException>(failure).error)
            assertTrue(source.getUser(localId)!!.hidden)
            assertEquals(instant(6_000), source.getUser(localId)?.undoDeadline)
        }
    }

    @Test
    fun undoAndTimeoutRaceHasOneDurableWinner() = runTest {
        withFixture("undo-timeout-race") {
            source.mergeSnapshot(listOf(snapshot(remoteId = 7)), instant(1_000))
            val localId = source.observeVisibleUsers().first().single().user.localId
            source.requestDelete(localId, instant(6_000))

            val undo = async { runCatching { source.undoDelete(localId, instant(5_999)) } }
            val finalize = async { source.finalizeExpiredDeletes(instant(6_000)) }
            val undoResult = undo.await()
            val finalizedCount = finalize.await()

            val restored = source.observeVisibleUsers().first().any { it.user.localId == localId }
            val deleteMutations = source.getAllMutations().filter { it.kind == MutationKind.Delete }
            assertTrue(restored.xor(deleteMutations.isNotEmpty()))
            assertEquals(restored, undoResult.isSuccess)
            assertEquals(if (restored) 0 else 1, finalizedCount)
            assertTrue(deleteMutations.size <= 1)
        }
    }

    @Test
    fun expiredDeleteAfterReopenCreatesExactlyOneMutation() = runTest {
        withFixture("delete-reopen") {
            source.mergeSnapshot(listOf(snapshot(remoteId = 7)), instant(1_000))
            val localId = source.observeVisibleUsers().first().single().user.localId
            source.requestDelete(localId, instant(6_000))

            reopen()

            assertEquals(1, source.finalizeExpiredDeletes(instant(6_000)))
            assertEquals(0, source.finalizeExpiredDeletes(instant(7_000)))
            val mutation = source.getAllMutations().single()
            assertEquals(MutationKind.Delete, mutation.kind)
            assertEquals(localId, mutation.userLocalId)
            assertEquals(StoredUserSyncStatus.PendingDelete, source.getUser(localId)?.synchronization)
        }
    }

    @Test
    fun multipleDeletionDeadlinesFinalizeIndependentlyWithoutDuplicateMutations() = runTest {
        withFixture("multiple-delete-deadlines") {
            source.mergeSnapshot(
                listOf(
                    snapshot(remoteId = 7, name = "Ada", position = 1),
                    snapshot(remoteId = 8, name = "Grace", position = 2),
                ),
                instant(1_000),
            )
            val users = source.observeVisibleUsers().first().associateBy { it.user.name }
            source.requestDelete(users.getValue("Ada").user.localId, instant(6_000))
            source.requestDelete(users.getValue("Grace").user.localId, instant(8_000))

            assertEquals(
                listOf("Ada", "Grace"),
                source.observeUndoableUsers().first().map { it.user.name },
            )
            assertEquals(1, source.finalizeExpiredDeletes(instant(6_000)))
            assertEquals(listOf("Grace"), source.observeUndoableUsers().first().map { it.user.name })
            assertEquals(1, source.finalizeExpiredDeletes(instant(8_000)))
            assertEquals(0, source.finalizeExpiredDeletes(instant(9_000)))

            val mutations = source.getAllMutations()
            assertEquals(2, mutations.size)
            assertEquals(2, mutations.map(StoredMutation::userLocalId).toSet().size)
            assertTrue(mutations.all { it.kind == MutationKind.Delete })
        }
    }

    @Test
    fun snapshotMergePreservesObservedTimeAndPendingDeleteState() = runTest {
        withFixture("snapshot-preservation") {
            source.mergeSnapshot(
                listOf(snapshot(remoteId = 7, name = "Original", position = 1)),
                instant(1_000),
            )
            val localId = source.observeVisibleUsers().first().single().user.localId
            source.requestDelete(localId, instant(2_000))
            source.finalizeExpiredDeletes(instant(2_000))

            source.mergeSnapshot(
                listOf(snapshot(remoteId = 7, name = "Remote replacement", position = 4)),
                instant(9_000),
            )

            val stored = source.getUser(localId)!!
            assertEquals("Original", stored.name)
            assertEquals(instant(1_000), stored.observedAt)
            assertEquals(StoredUserSyncStatus.PendingDelete, stored.synchronization)
            assertTrue(stored.hidden)
        }
    }

    @Test
    fun pageMergeAppendsPrecedingPageWithoutRemovingTheLastPage() = runTest {
        withFixture("page-merge") {
            source.mergeSnapshot(
                listOf(snapshot(remoteId = 41, name = "Page three", position = -60)),
                instant(1_000),
            )

            source.mergePage(
                listOf(snapshot(remoteId = 21, name = "Page two", position = -40)),
                instant(2_000),
            )

            val visible = source.observeVisibleUsers().first()
            assertEquals(listOf("Page three", "Page two"), visible.map { it.user.name })
            assertEquals(instant(1_000), visible.first().user.observedAt)
            assertEquals(instant(2_000), visible.last().user.observedAt)
        }
    }

    @Test
    fun retryableMutationBecomesDueAtPersistedTimeAfterReopen() = runTest {
        withFixture("retry-reopen") {
            source.insertPendingCreate(
                mutationId = "retry-mutation",
                input = input(),
                observedAt = instant(1_000),
            )
            source.markMutationRetryable(
                mutationId = "retry-mutation",
                retryAt = instant(5_000),
                reason = "HTTP 503",
            )

            assertTrue(source.getDueMutations(instant(4_999)).isEmpty())
            reopen()

            val due = source.getDueMutations(instant(5_000)).single()
            assertEquals("retry-mutation", due.mutation.mutationId)
            assertEquals(1, due.mutation.attemptCount)
            assertEquals(MutationState.RetryableWait, due.mutation.state)
        }
    }

    @Test
    fun blockedMutationRequiresExplicitRetryBeforeItIsDue() = runTest {
        withFixture("blocked-retry") {
            source.insertPendingCreate(
                mutationId = "blocked-mutation",
                input = input(),
                observedAt = instant(1_000),
            )
            source.markMutationBlocked("blocked-mutation", "Authentication required")

            assertTrue(source.getDueMutations(instant(10_000)).isEmpty())

            source.retryBlockedMutation("blocked-mutation")

            assertEquals("blocked-mutation", source.getDueMutations(instant(10_000)).single().mutation.mutationId)
        }
    }

    @Test
    fun authenticationBlockedMutationsResumeWhenCredentialsAreUpdated() = runTest {
        withFixture("authentication-retry") {
            source.insertPendingCreate(
                mutationId = "authentication-mutation",
                input = input(),
                observedAt = instant(1_000),
            )
            source.markMutationBlocked("authentication-mutation", "Authentication is required.")
            source.insertPendingCreate(
                mutationId = "permanent-mutation",
                input = input(name = "Permanent user"),
                observedAt = instant(2_000),
            )
            source.markMutationBlocked("permanent-mutation", "The endpoint is unavailable.")

            source.retryAuthenticationBlockedMutations()

            assertEquals(
                listOf("authentication-mutation"),
                source.getDueMutations(instant(10_000)).map { it.mutation.mutationId },
            )
        }
    }

    @Test
    fun failedCreateRetryCreatesOneNewPendingMutation() = runTest {
        withFixture("failed-create-retry") {
            val localId = source.insertPendingCreate(
                mutationId = "original-create",
                input = input(),
                observedAt = instant(1_000),
            )
            source.markCreateFailed("original-create", localId, "Email is taken")

            source.retryFailedCreate(
                localId = localId,
                mutationId = "retried-create",
                createdAt = instant(2_000),
            )

            assertEquals(StoredUserSyncStatus.PendingCreate, source.getUser(localId)?.synchronization)
            assertEquals("retried-create", source.getAllMutations().single().mutationId)
        }
    }

    @Test
    fun emptySnapshotRemovesSyncedRowsButPreservesFailedLocalCreate() = runTest {
        withFixture("empty-snapshot") {
            source.mergeSnapshot(listOf(snapshot(remoteId = 7)), instant(1_000))
            val remoteLocalId = source.observeVisibleUsers().first().single().user.localId
            val localId = source.insertPendingCreate(
                mutationId = "failed-create",
                input = input(name = "Needs review"),
                observedAt = instant(2_000),
            )
            source.markCreateFailed("failed-create", localId, "Email is taken")

            source.mergeSnapshot(emptyList(), instant(3_000))

            assertNull(source.getUser(remoteLocalId))
            val visible = source.observeVisibleUsers().first().single()
            assertEquals(localId, visible.user.localId)
            assertEquals(
                UserSynchronization.CreateFailed("Email is taken"),
                visible.synchronization,
            )
        }
    }

    private suspend fun <T> kotlinx.coroutines.test.TestScope.withFixture(
        suffix: String,
        block: suspend DatabaseFixture.() -> T,
    ): T {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val fixture = DatabaseFixture(
            application = application,
            databaseName = "user-management-$suffix.db",
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        return try {
            fixture.block()
        } finally {
            fixture.close()
        }
    }

    private class DatabaseFixture(
        private val application: Application,
        private val databaseName: String,
        private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) {
        private val ids = CountingIdGenerator()
        private var driver = openDriver()
        var source = openSource()
            private set

        init {
            driver.close()
            application.deleteDatabase(databaseName)
            driver = openDriver()
            source = openSource()
        }

        fun reopen() {
            driver.close()
            driver = openDriver()
            source = openSource()
        }

        fun close() {
            driver.close()
            application.deleteDatabase(databaseName)
        }

        private fun openDriver() = AndroidSqliteDriver(
            schema = UserManagementDatabase.Schema,
            context = application,
            name = databaseName,
        )

        private fun openSource() = SqlDelightUserLocalDataSource(
            database = UserManagementDatabase(driver),
            idGenerator = ids,
            queryDispatcher = dispatcher,
        )
    }

    private class CountingIdGenerator : IdGenerator {
        private var next = 0

        override fun nextId(): String = "generated-${next++}"
    }

    private companion object {
        fun instant(epochMilliseconds: Long): Instant = Instant.fromEpochMilliseconds(epochMilliseconds)

        fun input(name: String = "Local user") = AddUserInput(
            name = name,
            email = "local@example.com",
            gender = Gender.Female,
            status = UserStatus.Active,
        )

        fun snapshot(
            remoteId: Long,
            name: String = "Remote user",
            position: Long? = 1,
        ) = SnapshotUser(
            remoteId = remoteId,
            name = name,
            email = "remote-$remoteId@example.com",
            gender = Gender.Male,
            status = UserStatus.Active,
            serverPosition = position,
        )
    }
}
