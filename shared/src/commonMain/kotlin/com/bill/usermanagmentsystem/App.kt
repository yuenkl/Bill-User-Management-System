package com.bill.usermanagmentsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bill.usermanagmentsystem.ui.theme.UserManagementTheme
import com.bill.usermanagmentsystem.ui.users.UserFeedRoute

@Composable
@Preview
fun App() {
    UserManagementTheme {
        UserFeedRoute()
    }
}
