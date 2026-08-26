package com.bill.usermanagmentsystem.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF006A61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF74F8E6),
    onSecondaryContainer = Color(0xFF00201C),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
    surfaceVariant = Color(0xFFE1E2EC),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF164584),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFF53DBC9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFF74F8E6),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF44464F),
)

@Composable
fun UserManagementTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
