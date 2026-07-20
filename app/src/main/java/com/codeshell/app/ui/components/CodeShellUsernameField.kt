package com.codeshell.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.util.UsernameFormatter

@Composable
fun CodeShellUsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        prefix = {
            Text(
                text = UsernameFormatter.PREFIX,
                color = EditorDefaultText,
                modifier = Modifier.padding(end = 2.dp)
            )
        },
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        ),
        keyboardActions = if (onImeAction != null) {
            androidx.compose.foundation.text.KeyboardActions(onDone = { onImeAction() })
        } else {
            androidx.compose.foundation.text.KeyboardActions.Default
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = EditorDefaultText)
    )
}

fun filterDigitUsernameInput(input: String): String {
    return UsernameFormatter.parseRawInput(input)
}
