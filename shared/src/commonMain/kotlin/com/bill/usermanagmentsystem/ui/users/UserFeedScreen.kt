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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message
    LaunchedEffect(message?.id) {
        if (message != null) {
            onMessageConsumed(message.id)
            snackbarHostState.showSnackbar(message.text)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val formPresentation = addUserFormPresentation(maxWidth)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("User directory") },
                    actions = {
                        TextButton(onClick = onRefresh) { Text("Refresh") }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    state.initialLoading -> LoadingFeed()
                    state.emptyState != null -> EmptyFeed(state.emptyState, onRetry)
                    else -> UserList(state.users, state.banner, onRetryUserCreation)
                }
            }
        }

        state.addUserForm?.let { form ->
            if (formPresentation == AddUserFormPresentation.Dialog) {
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
}

internal enum class AddUserFormPresentation {
    Sheet,
    Dialog,
}

internal fun addUserFormPresentation(width: Dp): AddUserFormPresentation =
    if (width >= 600.dp) AddUserFormPresentation.Dialog else AddUserFormPresentation.Sheet

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
private fun LoadingFeed() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(5) { UserCardShimmer() }
    }
}

@Composable
private fun UserList(
    users: List<UserItemUiModel>,
    banner: UserFeedBanner?,
    onRetryUserCreation: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (banner != null) {
            item(key = "feed-banner") { FeedBanner(banner) }
        }
        items(users, key = UserItemUiModel::localId) { user ->
            UserCard(
                user = user,
                onRetryCreation = { onRetryUserCreation(user.localId) },
            )
        }
    }
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
            .semantics { contentDescription = text },
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
