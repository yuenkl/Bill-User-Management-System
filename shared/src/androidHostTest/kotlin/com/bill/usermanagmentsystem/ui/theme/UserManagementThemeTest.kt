package com.bill.usermanagmentsystem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserManagementThemeTest {
    @Test
    fun lightAndDarkThemesProvideDistinctStatusAndSurfaceColors() =
        runComposeUiTest {
            var darkTheme by mutableStateOf(false)
            var background = Color.Unspecified
            var pendingContainer = Color.Unspecified
            var pendingContent = Color.Unspecified
            var failureContainer = Color.Unspecified
            var failureContent = Color.Unspecified

            setContent {
                UserManagementTheme(darkTheme = darkTheme) {
                    val colors = MaterialTheme.colorScheme
                    SideEffect {
                        background = colors.background
                        pendingContainer = colors.tertiaryContainer
                        pendingContent = colors.onTertiaryContainer
                        failureContainer = colors.errorContainer
                        failureContent = colors.onErrorContainer
                    }
                    Text("Theme probe")
                }
            }

            lateinit var light: ThemeColors
            runOnIdle {
                light =
                    ThemeColors(
                        background,
                        pendingContainer,
                        pendingContent,
                        failureContainer,
                        failureContent,
                    )
                darkTheme = true
            }

            runOnIdle {
                val dark =
                    ThemeColors(
                        background,
                        pendingContainer,
                        pendingContent,
                        failureContainer,
                        failureContent,
                    )
                assertNotEquals(light.background, dark.background)
                assertNotEquals(light.pendingContainer, dark.pendingContainer)
                assertNotEquals(light.failureContainer, dark.failureContainer)
                assertNotEquals(light.pendingContainer, light.pendingContent)
                assertNotEquals(light.failureContainer, light.failureContent)
                assertNotEquals(dark.pendingContainer, dark.pendingContent)
                assertNotEquals(dark.failureContainer, dark.failureContent)
            }
        }

    private data class ThemeColors(
        val background: Color,
        val pendingContainer: Color,
        val pendingContent: Color,
        val failureContainer: Color,
        val failureContent: Color,
    )
}
