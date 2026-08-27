package com.bill.usermanagmentsystem.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.users.components.UserForm

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AddUserFormOverlay(
    form: AddUserFormUiState,
    layoutMode: AdaptiveLayoutMode,
    compactFormMaxHeight: Dp,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onGenderSelected: (Gender) -> Unit,
    onStatusSelected: (UserStatus) -> Unit,
    onSubmit: () -> Unit,
) {
    if (layoutMode == AdaptiveLayoutMode.Wide) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .semantics { contentDescription = "Add user dialog" },
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                UserForm(
                    state = form,
                    onNameChange = onNameChange,
                    onEmailChange = onEmailChange,
                    onGenderSelected = onGenderSelected,
                    onStatusSelected = onStatusSelected,
                    onCancel = onDismiss,
                    onSubmit = onSubmit,
                )
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = Modifier.semantics { contentDescription = "Add user sheet" },
        ) {
            UserForm(
                state = form,
                onNameChange = onNameChange,
                onEmailChange = onEmailChange,
                onGenderSelected = onGenderSelected,
                onStatusSelected = onStatusSelected,
                onCancel = onDismiss,
                onSubmit = onSubmit,
                modifier = Modifier.heightIn(max = compactFormMaxHeight).imePadding(),
            )
        }
    }
}

@Composable
internal fun AddUserValidationAlertDialog(
    alert: AddUserValidationAlert,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unable to add user") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alert.errors.forEach { error ->
                    Text(
                        text = error.field.replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(error.message)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
internal fun DeleteConfirmationDialog(
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
