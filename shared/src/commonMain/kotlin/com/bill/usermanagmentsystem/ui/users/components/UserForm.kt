package com.bill.usermanagmentsystem.ui.users.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.users.AddUserFormUiState

@Composable
fun UserForm(
    state: AddUserFormUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onGenderSelected: (Gender) -> Unit,
    onStatusSelected: (UserStatus) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emailFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val fieldsEnabled = !state.submitting

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Add user",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Saved on this device first, then synchronized when possible.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = fieldsEnabled,
            singleLine = true,
            label = { Text("Name") },
            isError = state.nameError != null || state.nameApiError != null,
            supportingText = supportingError(state.nameError, state.nameApiError),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { emailFocusRequester.requestFocus() },
            ),
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester),
            enabled = fieldsEnabled,
            singleLine = true,
            label = { Text("Email") },
            isError = state.emailError != null || state.emailApiError != null,
            supportingText = supportingError(state.emailError, state.emailApiError),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gender", fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Gender.entries.forEach { gender ->
                    FilterChip(
                        selected = state.gender == gender,
                        onClick = { onGenderSelected(gender) },
                        modifier = Modifier.weight(1f),
                        enabled = fieldsEnabled,
                        label = { Text(gender.displayName()) },
                    )
                }
            }
            state.genderError?.let { SupportingError(it) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = fieldsEnabled,
                    role = Role.Switch,
                    onClick = {
                        onStatusSelected(
                            if (state.status == UserStatus.Active) {
                                UserStatus.Inactive
                            } else {
                                UserStatus.Active
                            },
                        )
                    },
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = if (state.status == UserStatus.Active) "Active" else "Inactive"
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Active user", fontWeight = FontWeight.Medium)
                Text(
                    text = if (state.status == UserStatus.Active) "Status: active" else "Status: inactive",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.status == UserStatus.Active,
                onCheckedChange = null,
                enabled = fieldsEnabled,
            )
        }

        state.submissionError?.let { SupportingError(it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = fieldsEnabled) {
                Text("Cancel")
            }
            Button(onClick = onSubmit, enabled = state.canSubmit) {
                Text(if (state.submitting) "Saving…" else "Add user")
            }
        }
    }
}

@Composable
private fun supportingError(
    localError: String?,
    apiError: String?,
): (@Composable () -> Unit)? {
    val error = localError ?: apiError ?: return null
    return { SupportingError(error) }
}

@Composable
private fun SupportingError(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun Gender.displayName(): String = when (this) {
    Gender.Female -> "Female"
    Gender.Male -> "Male"
}
