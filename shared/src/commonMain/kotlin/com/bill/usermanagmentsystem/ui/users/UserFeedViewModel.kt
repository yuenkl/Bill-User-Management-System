package com.bill.usermanagmentsystem.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UserSynchronization
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.domain.usecase.ObserveSyncState
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
import com.bill.usermanagmentsystem.platform.AppLifecycleObserver
import com.bill.usermanagmentsystem.platform.AppLifecycleState
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class UserFeedViewModel(
    observeUsers: ObserveUsers,
    observeSyncState: ObserveSyncState,
    private val refreshUsers: RefreshUsers,
    private val connectivityObserver: ConnectivityObserver,
    private val lifecycleObserver: AppLifecycleObserver,
    private val timeProvider: TimeProvider,
    private val relativeTimeFormatter: RelativeTimeFormatter,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val presentation = MutableStateFlow(PresentationState())
    private var synchronizationJob: Job? = null
    private var nextMessageId = 0L

    val uiState = combine(
        observeUsers(),
        observeSyncState(),
        connectivityObserver.status,
        minuteTicker(),
        presentation,
    ) { users, syncState, connectivity, now, presentationState ->
        buildUiState(users, syncState, connectivity, now, presentationState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserFeedUiState(),
    )

    init {
        requestSynchronization(manual = false)
        observeAutomaticTriggers()
    }

    fun refresh() {
        requestSynchronization(manual = true)
    }

    fun retry() {
        requestSynchronization(manual = true)
    }

    fun consumeMessage(id: Long) {
        presentation.value = presentation.value.let { current ->
            if (current.message?.id == id) current.copy(message = null) else current
        }
    }

    private fun requestSynchronization(manual: Boolean) {
        if (synchronizationJob?.isActive == true) return
        synchronizationJob = viewModelScope.launch(dispatcher) {
            presentation.value = presentation.value.copy(refreshing = manual)
            val result = refreshUsers()
            presentation.value = presentation.value.copy(
                initialAttemptFinished = true,
                refreshing = false,
                message = if (manual && result.isFailure) {
                    UserFeedMessage(
                        id = ++nextMessageId,
                        text = result.exceptionOrNull().toUserMessage(),
                    )
                } else {
                    presentation.value.message
                },
            )
        }
    }

    private fun observeAutomaticTriggers() {
        viewModelScope.launch(dispatcher) {
            connectivityObserver.status
                .drop(1)
                .filter { it == ConnectivityStatus.Available }
                .collect { requestSynchronization(manual = false) }
        }
        viewModelScope.launch(dispatcher) {
            lifecycleObserver.state
                .drop(1)
                .filter { it == AppLifecycleState.Foreground }
                .collect { requestSynchronization(manual = false) }
        }
    }

    private fun minuteTicker(): Flow<Instant> = flow {
        while (true) {
            emit(timeProvider.now())
            delay(1.minutes)
        }
    }.flowOn(dispatcher)

    private fun buildUiState(
        users: List<UserRecord>,
        syncState: SyncState,
        connectivity: ConnectivityStatus,
        now: Instant,
        presentationState: PresentationState,
    ): UserFeedUiState {
        val items = users.map { it.toUiModel(now) }
        val offline = connectivity == ConnectivityStatus.Unavailable
        val error = (syncState as? SyncState.Failed)?.error
        val initialLoading = items.isEmpty() && !presentationState.initialAttemptFinished
        val emptyState = when {
            items.isNotEmpty() || initialLoading -> null
            offline -> UserFeedEmptyState.Offline
            error == UserDataError.AuthenticationRequired -> UserFeedEmptyState.AuthenticationRequired
            error != null -> UserFeedEmptyState.Error(error.toUserMessage())
            else -> UserFeedEmptyState.Empty
        }
        val banner = when {
            items.isEmpty() -> null
            offline -> UserFeedBanner.Offline
            error == UserDataError.AuthenticationRequired -> UserFeedBanner.AuthenticationRequired
            error != null -> UserFeedBanner.RefreshFailed(error.toUserMessage())
            else -> null
        }
        return UserFeedUiState(
            users = items,
            initialLoading = initialLoading,
            refreshing = presentationState.refreshing,
            emptyState = emptyState,
            banner = banner,
            message = presentationState.message,
        )
    }

    private fun UserRecord.toUiModel(now: Instant): UserItemUiModel = UserItemUiModel(
        localId = user.localId,
        name = user.name,
        email = user.email,
        gender = user.gender,
        status = user.status,
        relativeTime = relativeTimeFormatter.format(user.observedAt, now),
        synchronization = when (val state = synchronization) {
            UserSynchronization.Synced -> UserItemSynchronization.Synced
            UserSynchronization.PendingCreate -> UserItemSynchronization.Pending
            is UserSynchronization.CreateFailed -> UserItemSynchronization.Failed(state.reason)
        },
    )

    private data class PresentationState(
        val initialAttemptFinished: Boolean = false,
        val refreshing: Boolean = false,
        val message: UserFeedMessage? = null,
    )
}

private fun Throwable?.toUserMessage(): String =
    this?.userDataErrorOrNull()?.toUserMessage() ?: "The user directory could not be refreshed."

private fun UserDataError.toUserMessage(): String = when (this) {
    UserDataError.Offline -> "You're offline. Cached users will remain available."
    UserDataError.AuthenticationRequired -> "Check the GoRest access token, then retry."
    UserDataError.DeleteTooLate -> "That deletion can no longer be undone."
    is UserDataError.UserNotFound -> "That user is no longer available."
    is UserDataError.ValidationRejected -> reason
    is UserDataError.RetryScheduled -> reason
    is UserDataError.Persistence -> "Saved users could not be read. $reason"
    is UserDataError.RemoteContract -> "The service returned unexpected data. $reason"
    is UserDataError.Unexpected -> reason
}
