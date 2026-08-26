package com.bill.usermanagmentsystem.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.users.components.UserCard
import com.bill.usermanagmentsystem.ui.users.components.UserCardShimmer
import com.bill.usermanagmentsystem.ui.users.components.UserForm
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserFeedRoute(
    modifier: Modifier = Modifier,
    viewModel: UserFeedViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserFeedScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onAddUser = viewModel::openAddUserForm,
        onAddUserDismissed = viewModel::dismissAddUserForm,
        onAddUserNameChanged = viewModel::updateAddUserName,
        onAddUserEmailChanged = viewModel::updateAddUserEmail,
        onAddUserGenderSelected = viewModel::selectAddUserGender,
        onAddUserStatusSelected = viewModel::selectAddUserStatus,
        onAddUserSubmitted = viewModel::submitAddUser,
        onRetryUserCreation = viewModel::retryUserCreation,
        onMessageConsumed = viewModel::consumeMessage,
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
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAddUser: () -> Unit,
    onAddUserDismissed: () -> Unit,
    onAddUserNameChanged: (String) -> Unit,
    onAddUserEmailChanged: (String) -> Unit,
    onAddUserGenderSelected: (Gender) -> Unit,
    onAddUserStatusSelected: (UserStatus) -> Unit,
    onAddUserSubmitted: () -> Unit,
    onRetryUserCreation: (String) -> Unit,
    onMessageConsumed: (Long) -> Unit,
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
    val message = state.message
    val undoSnackbar = state.undoSnackbar
    LaunchedEffect(message?.id, undoSnackbar?.input) {
        if (message != null && undoSnackbar == null) {
            onMessageConsumed(message.id)
            snackbarHostState.showSnackbar(message.text)
        }
    }
    LaunchedEffect(undoSnackbar?.input) {
        if (undoSnackbar != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "${undoSnackbar.userName} deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndoDelete(undoSnackbar.input)
            } else {
                onUndoDeleteDismissed(undoSnackbar.input)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layoutMode = adaptiveLayoutMode(maxWidth)
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
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddUser,
                    modifier = Modifier
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
                modifier = Modifier
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
                    else -> UserList(
                        users = state.users,
                        banner = state.banner,
                        layoutMode = layoutMode,
                        loadingMore = state.loadingMore,
                        canLoadMore = state.canLoadMore,
                        loadMoreError = state.loadMoreError,
                        onRetryUserCreation = onRetryUserCreation,
                        onUserLongClick = onUserLongClick,
                        onLoadNextPage = onLoadNextPage,
                        onRetryNextPage = onRetryNextPage,
                    )
                }
            }
        }

        state.addUserForm?.let { form ->
            if (layoutMode == AdaptiveLayoutMode.Wide) {
                Dialog(onDismissRequest = onAddUserDismissed) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .semantics { contentDescription = "Add user dialog" },
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 6.dp,
                    ) {
                        UserFormContent(
                            state = form,
                            onNameChange = onAddUserNameChanged,
                            onEmailChange = onAddUserEmailChanged,
                            onGenderSelected = onAddUserGenderSelected,
                            onStatusSelected = onAddUserStatusSelected,
                            onCancel = onAddUserDismissed,
                            onSubmit = onAddUserSubmitted,
                        )
                    }
                }
            } else {
                ModalBottomSheet(
                    onDismissRequest = onAddUserDismissed,
                    modifier = Modifier.semantics { contentDescription = "Add user sheet" },
                ) {
                    UserFormContent(
                        state = form,
                        onNameChange = onAddUserNameChanged,
                        onEmailChange = onAddUserEmailChanged,
                        onGenderSelected = onAddUserGenderSelected,
                        onStatusSelected = onAddUserStatusSelected,
                        onCancel = onAddUserDismissed,
                        onSubmit = onAddUserSubmitted,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding(),
                    )
                }
            }
        }
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

internal enum class AdaptiveLayoutMode(val columns: Int) {
    Compact(columns = 1),
    Wide(columns = 2),
}

internal fun adaptiveLayoutMode(width: Dp): AdaptiveLayoutMode =
    if (width >= 600.dp) AdaptiveLayoutMode.Wide else AdaptiveLayoutMode.Compact

@Composable
private fun UserFormContent(
    state: AddUserFormUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onGenderSelected: (Gender) -> Unit,
    onStatusSelected: (UserStatus) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UserForm(
        state = state,
        onNameChange = onNameChange,
        onEmailChange = onEmailChange,
        onGenderSelected = onGenderSelected,
        onStatusSelected = onStatusSelected,
        onCancel = onCancel,
        onSubmit = onSubmit,
        modifier = modifier,
    )
}

@Composable
private fun LoadingFeed(layoutMode: AdaptiveLayoutMode) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (layoutMode) {
            AdaptiveLayoutMode.Compact -> LazyColumn(
                modifier = Modifier.widthIn(max = MAX_FEED_WIDTH).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(5) { UserCardShimmer() }
            }

            AdaptiveLayoutMode.Wide -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.widthIn(max = MAX_FEED_WIDTH).fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(6) { UserCardShimmer() }
            }
        }
    }
}

@Composable
private fun UserList(
    users: List<UserItemUiModel>,
    banner: UserFeedBanner?,
    layoutMode: AdaptiveLayoutMode,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    loadMoreError: String?,
    onRetryUserCreation: (String) -> Unit,
    onUserLongClick: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetryNextPage: () -> Unit,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (layoutMode) {
            AdaptiveLayoutMode.Compact -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = MAX_FEED_WIDTH)
                    .fillMaxSize()
                    .semantics { contentDescription = "Users" },
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (banner != null) {
                    item(key = "feed-banner") { FeedBanner(banner) }
                }
                items(users, key = UserItemUiModel::localId) { user ->
                    FeedUserCard(
                        user = user,
                        onRetryUserCreation = onRetryUserCreation,
                        onUserLongClick = onUserLongClick,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (canLoadMore || loadMoreError != null) {
                    item(key = "pagination-${users.size}-${loadMoreError != null}") {
                        PaginationFooter(
                            loading = loadingMore,
                            error = loadMoreError,
                            onLoad = onLoadNextPage,
                            onRetry = onRetryNextPage,
                        )
                    }
                }
            }

            AdaptiveLayoutMode.Wide -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .widthIn(max = MAX_FEED_WIDTH)
                    .fillMaxSize()
                    .semantics { contentDescription = "Users" },
                contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (banner != null) {
                    item(
                        key = "feed-banner",
                        span = { GridItemSpan(maxLineSpan) },
                    ) { FeedBanner(banner) }
                }
                items(users, key = UserItemUiModel::localId) { user ->
                    FeedUserCard(
                        user = user,
                        onRetryUserCreation = onRetryUserCreation,
                        onUserLongClick = onUserLongClick,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (canLoadMore || loadMoreError != null) {
                    item(
                        key = "pagination-${users.size}-${loadMoreError != null}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        PaginationFooter(
                            loading = loadingMore,
                            error = loadMoreError,
                            onLoad = onLoadNextPage,
                            onRetry = onRetryNextPage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaginationFooter(
    loading: Boolean,
    error: String?,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
) {
    if (error == null) {
        LaunchedEffect(loading) {
            if (!loading) onLoad()
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Loading more users" },
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Couldn't load more users", fontWeight = FontWeight.SemiBold)
                Text(error, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun FeedUserCard(
    user: UserItemUiModel,
    onRetryUserCreation: (String) -> Unit,
    onUserLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    UserCard(
        user = user,
        onRetryCreation = { onRetryUserCreation(user.localId) },
        onLongClick = { onUserLongClick(user.localId) },
        modifier = modifier,
    )
}

@Composable
private fun DeleteConfirmationDialog(
    user: UserItemUiModel,
    deleting: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Delete user?",
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.name, fontWeight = FontWeight.SemiBold)
                Text(
                    text = user.email,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !deleting,
            ) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !deleting,
            ) {
                Text(
                    text = if (deleting) "Deleting…" else "Delete",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun FeedBanner(banner: UserFeedBanner) {
    val text = when (banner) {
        UserFeedBanner.Offline -> "Offline · showing saved users"
        UserFeedBanner.AuthenticationRequired -> "Access token required · showing saved users"
        is UserFeedBanner.RefreshFailed -> "Refresh failed · ${banner.message}"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = text
                liveRegion = LiveRegionMode.Polite
            },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyFeed(
    emptyState: UserFeedEmptyState,
    onRetry: () -> Unit,
) {
    val title: String
    val message: String
    val retryable: Boolean
    when (emptyState) {
        UserFeedEmptyState.Empty -> {
            title = "No users yet"
            message = "Pull to refresh and check again."
            retryable = false
        }
        UserFeedEmptyState.Offline -> {
            title = "You're offline"
            message = "Connect to the internet to load the user directory."
            retryable = true
        }
        UserFeedEmptyState.AuthenticationRequired -> {
            title = "Access token required"
            message = "Check the local GoRest configuration, then retry."
            retryable = true
        }
        is UserFeedEmptyState.Error -> {
            title = "Couldn't load users"
            message = emptyState.message
            retryable = true
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (retryable) {
                Spacer(Modifier.size(4.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

private val MAX_FEED_WIDTH = 1_200.dp
