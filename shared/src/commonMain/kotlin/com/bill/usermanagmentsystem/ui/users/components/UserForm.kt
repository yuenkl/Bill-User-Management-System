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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.users.AddUserField
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
    val nameErrorMessage = state.errorMessage(AddUserField.Name)
    val emailErrorMessage = state.errorMessage(AddUserField.Email)
    val genderErrorMessage = state.errorMessage(AddUserField.Gender)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Add user",
                    modifier = Modifier.semantics { heading() },
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
                value = state.valueFor(AddUserField.Name).orEmpty(),
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (nameErrorMessage != null) error(nameErrorMessage)
                    },
                enabled = fieldsEnabled,
                singleLine = true,
                label = { Text("Name") },
                isError = nameErrorMessage != null,
                supportingText = supportingError(nameErrorMessage),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { emailFocusRequester.requestFocus() },
                ),
            )

            OutlinedTextField(
                value = state.valueFor(AddUserField.Email).orEmpty(),
                onValueChange = onEmailChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocusRequester)
                    .semantics {
                        if (emailErrorMessage != null) error(emailErrorMessage)
                    },
                enabled = fieldsEnabled,
                singleLine = true,
                label = { Text("Email") },
                isError = emailErrorMessage != null,
                supportingText = supportingError(emailErrorMessage),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
            )

            Column(
                modifier = Modifier.semantics {
                    genderErrorMessage?.let { error(it) }
                },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Gender", fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Gender.entries.forEach { gender ->
                        FilterChip(
                            selected = state.gender() == gender,
                            onClick = { onGenderSelected(gender) },
                            modifier = Modifier.weight(1f),
                            enabled = fieldsEnabled,
                            label = { Text(gender.displayName()) },
                        )
                    }
                }
                genderErrorMessage?.let { SupportingError(it) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = fieldsEnabled,
                        role = Role.Switch,
                        onClick = {
                            onStatusSelected(
                                if (state.status() == UserStatus.Active) {
                                    UserStatus.Inactive
                                } else {
                                    UserStatus.Active
                                },
                            )
                        },
                    )
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (state.status() == UserStatus.Active) "Active" else "Inactive"
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Active user", fontWeight = FontWeight.Medium)
                    Text(
                        text = if (state.status() == UserStatus.Active) "Status: active" else "Status: inactive",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.status() == UserStatus.Active,
                    onCheckedChange = null,
                    enabled = fieldsEnabled,
                )
            }

            state.errorMessage(AddUserField.Form)?.let { SupportingError(it) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = fieldsEnabled) {
                Text("Cancel")
            }
            Button(
                onClick = onSubmit,
                modifier = Modifier.semantics { contentDescription = "Submit user" },
                enabled = state.canSubmit,
            ) {
                Text(if (state.submitting) "Saving…" else "Add user")
            }
        }
    }
}

@Composable
private fun supportingError(message: String?): (@Composable () -> Unit)? {
    val errorMessage = message ?: return null
    return { SupportingError(errorMessage) }
}

@Composable
private fun SupportingError(message: String) {
    Text(
        text = message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun Gender.displayName(): String = when (this) {
    Gender.Female -> "Female"
    Gender.Male -> "Male"
}
