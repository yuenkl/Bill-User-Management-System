package com.bill.usermanagmentsystem.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bill.usermanagmentsystem.ui.users.components.UserCard
import com.bill.usermanagmentsystem.ui.users.components.UserCardShimmer

@Composable
internal fun LoadingFeed(layoutMode: AdaptiveLayoutMode) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (layoutMode) {
            AdaptiveLayoutMode.Compact ->
                LazyColumn(
                    modifier = Modifier.widthIn(max = MAX_FEED_WIDTH).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(5) { UserCardShimmer() }
                }

            AdaptiveLayoutMode.Wide ->
                LazyVerticalGrid(
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
internal fun UserList(
    users: List<UserItemUiModel>,
    banner: UserFeedBanner?,
    layoutMode: AdaptiveLayoutMode,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    loadMoreError: String?,
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
            AdaptiveLayoutMode.Compact ->
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
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

            AdaptiveLayoutMode.Wide ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier =
                        Modifier
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
                    modifier =
                        Modifier
                            .size(32.dp)
                            .semantics { contentDescription = "Loading more users" },
                )
            }
        }
    } else {
        Surface(
            modifier =
                Modifier
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
    onUserLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    UserCard(
        user = user,
        onLongClick = { onUserLongClick(user.localId) },
        modifier = modifier,
    )
}

@Composable
private fun FeedBanner(banner: UserFeedBanner) {
    val text =
        when (banner) {
            UserFeedBanner.Offline -> "Offline · showing saved users"
            UserFeedBanner.AuthenticationRequired -> "Access token required · showing saved users"
            is UserFeedBanner.RefreshFailed -> "Refresh failed · ${banner.message}"
        }
    Surface(
        modifier =
            Modifier
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
internal fun EmptyFeed(
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
