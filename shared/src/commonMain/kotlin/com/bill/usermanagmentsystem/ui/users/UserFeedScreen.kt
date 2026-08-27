package com.bill.usermanagmentsystem.ui.users

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserFeedRoute(
    modifier: Modifier = Modifier,
    viewModel: UserFeedViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserFeedScreen(
        state = state,
        events = viewModel.events,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onAddUser = viewModel::openAddUserForm,
        onAddUserDismissed = viewModel::dismissAddUserForm,
        onAddUserNameChanged = viewModel::updateAddUserName,
        onAddUserEmailChanged = viewModel::updateAddUserEmail,
        onAddUserGenderSelected = viewModel::selectAddUserGender,
        onAddUserStatusSelected = viewModel::selectAddUserStatus,
        onAddUserSubmitted = viewModel::submitAddUser,
        onAddUserValidationAlertDismissed = viewModel::dismissAddUserValidationAlert,
        onUserLongClick = viewModel::selectUserForDeletion,
        onDeleteCancel = viewModel::cancelDelete,
        onDeleteConfirm = viewModel::confirmDelete,
        onUndoDelete = viewModel::undoDelete,
        onUndoDeleteDismissed = viewModel::dismissUndoDelete,
        onLoadNextPage = viewModel::loadNextPage,
        onRetryNextPage = viewModel::retryNextPage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedScreen(
    state: UserFeedUiState,
    events: Flow<UserFeedEvent> = emptyFlow(),
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAddUser: () -> Unit,
    onAddUserDismissed: () -> Unit,
    onAddUserNameChanged: (String) -> Unit,
    onAddUserEmailChanged: (String) -> Unit,
    onAddUserGenderSelected: (Gender) -> Unit,
    onAddUserStatusSelected: (UserStatus) -> Unit,
    onAddUserSubmitted: () -> Unit,
    onAddUserValidationAlertDismissed: () -> Unit = {},
    onUserLongClick: (String) -> Unit = {},
    onDeleteCancel: () -> Unit = {},
    onDeleteConfirm: () -> Unit = {},
    onUndoDelete: (AddUserInput) -> Unit = {},
    onUndoDeleteDismissed: (AddUserInput) -> Unit = {},
    onLoadNextPage: () -> Unit = {},
    onRetryNextPage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var scrollToTopRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                UserFeedEvent.ScrollToTop -> scrollToTopRequest += 1
                is UserFeedEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is UserFeedEvent.ShowDeleteUndoSnackbar -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result =
                        snackbarHostState.showSnackbar(
                            message = "${event.userName} deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Indefinite,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        onUndoDelete(event.input)
                    } else {
                        onUndoDeleteDismissed(event.input)
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layoutMode = adaptiveLayoutMode(maxWidth, maxHeight)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "User directory",
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    actions = {
                        TextButton(onClick = onRefresh) { Text("Refresh") }
                    },
                )
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp),
                ) { snackbarData ->
                    Snackbar(
                        action = {
                            snackbarData.visuals.actionLabel?.let { actionLabel ->
                                Row {
                                    TextButton(onClick = snackbarData::performAction) {
                                        Text(actionLabel)
                                    }
                                    TextButton(onClick = snackbarData::dismiss) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        },
                    ) {
                        Text(snackbarData.visuals.message)
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddUser,
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .semantics { contentDescription = "Add user" },
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            },
        ) { contentPadding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .semantics {
                            contentDescription = "User feed. Pull to refresh."
                            onClick(label = "Refresh") {
                                onRefresh()
                                true
                            }
                        },
            ) {
                when {
                    state.initialLoading -> LoadingFeed(layoutMode)
                    state.emptyState != null && !state.canLoadMore -> EmptyFeed(state.emptyState, onRetry)
                    else ->
                        UserList(
                            users = state.users,
                            banner = state.banner,
                            layoutMode = layoutMode,
                            scrollToTopRequest = scrollToTopRequest,
                            loadingMore = state.loadingMore,
                            canLoadMore = state.canLoadMore,
                            loadMoreError = state.loadMoreError,
                            onUserLongClick = onUserLongClick,
                            onLoadNextPage = onLoadNextPage,
                            onRetryNextPage = onRetryNextPage,
                        )
                }
            }
        }

        state.addUserForm?.let { form ->
            AddUserFormOverlay(
                form = form,
                layoutMode = layoutMode,
                compactFormMaxHeight = maxHeight * 0.7f,
                onDismiss = onAddUserDismissed,
                onNameChange = onAddUserNameChanged,
                onEmailChange = onAddUserEmailChanged,
                onGenderSelected = onAddUserGenderSelected,
                onStatusSelected = onAddUserStatusSelected,
                onSubmit = onAddUserSubmitted,
            )
        }
    }

    state.addUserValidationAlert?.let { alert ->
        AddUserValidationAlertDialog(
            alert = alert,
            onDismiss = onAddUserValidationAlertDismissed,
        )
    }

    state.deleteConfirmation?.let { user ->
        DeleteConfirmationDialog(
            user = user,
            deleting = state.deleteInProgress,
            onCancel = onDeleteCancel,
            onConfirm = onDeleteConfirm,
        )
    }
}

internal enum class AdaptiveLayoutMode(
    val columns: Int,
) {
    Compact(columns = 1),
    Wide(columns = 2),
}

/**
 * Uses the available window shape rather than a device-width threshold. This keeps portrait
 * screens as a single feed and makes every landscape window a two-column grid.
 */
internal fun adaptiveLayoutMode(
    width: Dp,
    height: Dp,
): AdaptiveLayoutMode = if (width > height) AdaptiveLayoutMode.Wide else AdaptiveLayoutMode.Compact
