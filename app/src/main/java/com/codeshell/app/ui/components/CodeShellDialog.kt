package com.codeshell.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText

@Composable
fun CodeShellDialog(
    onDismiss: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = EditorBackground,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, EditorDefaultText.copy(alpha = 0.12f)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = EditorDefaultText,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

@Composable
fun CodeShellDialogAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (destructive) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
        } else {
            EditorDefaultText.copy(alpha = 0.85f)
        },
        modifier = modifier
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .semantics { role = Role.Button }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun CodeShellDialogChoice(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = EditorDefaultText,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .semantics { role = Role.Button }
            .padding(vertical = 10.dp),
    )
}

@Composable
fun CodeShellChoiceDivider() {
    HorizontalDivider(color = EditorDefaultText.copy(alpha = 0.08f))
}
