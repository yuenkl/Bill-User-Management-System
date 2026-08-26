package com.bill.usermanagmentsystem.ui.users.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun UserCardShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "user-card-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "user-card-shimmer-alpha",
    )
    val placeholder = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Loading users" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Placeholder(Modifier.size(48.dp).clip(CircleShape), placeholder)
            Spacer(Modifier.width(14.dp))
            Column {
                Placeholder(Modifier.fillMaxWidth(0.58f).height(18.dp), placeholder)
                Spacer(Modifier.height(9.dp))
                Placeholder(Modifier.fillMaxWidth(0.82f).height(14.dp), placeholder)
                Spacer(Modifier.height(10.dp))
                Placeholder(Modifier.fillMaxWidth(0.46f).height(14.dp), placeholder)
            }
        }
    }
}

@Composable
private fun Placeholder(
    modifier: Modifier,
    color: Color,
) {
    Spacer(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color),
    )
}
