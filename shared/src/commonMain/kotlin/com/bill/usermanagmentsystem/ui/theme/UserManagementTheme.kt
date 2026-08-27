package com.bill.usermanagmentsystem.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF315DA8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9E2FF),
        onPrimaryContainer = Color(0xFF001A41),
        secondary = Color(0xFF006A61),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF74F8E6),
        onSecondaryContainer = Color(0xFF00201C),
        tertiary = Color(0xFF765800),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDEA6),
        onTertiaryContainer = Color(0xFF261900),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF9F9FF),
        surface = Color(0xFFF9F9FF),
        surfaceVariant = Color(0xFFE1E2EC),
        outline = Color(0xFF757780),
        outlineVariant = Color(0xFFC5C6D0),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFAFC6FF),
        onPrimary = Color(0xFF002E69),
        primaryContainer = Color(0xFF164584),
        onPrimaryContainer = Color(0xFFD9E2FF),
        secondary = Color(0xFF53DBC9),
        onSecondary = Color(0xFF003731),
        secondaryContainer = Color(0xFF005048),
        onSecondaryContainer = Color(0xFF74F8E6),
        tertiary = Color(0xFFEAC248),
        onTertiary = Color(0xFF3E2E00),
        tertiaryContainer = Color(0xFF594400),
        onTertiaryContainer = Color(0xFFFFDEA6),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF111318),
        surface = Color(0xFF111318),
        surfaceVariant = Color(0xFF44464F),
        outline = Color(0xFF8F909A),
        outlineVariant = Color(0xFF44464F),
    )

private val UserManagementTypography =
    Typography(
        headlineSmall =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyLarge =
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
    )

private val UserManagementShapes =
    Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Composable
fun UserManagementTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = UserManagementTypography,
        shapes = UserManagementShapes,
        content = content,
    )
}
