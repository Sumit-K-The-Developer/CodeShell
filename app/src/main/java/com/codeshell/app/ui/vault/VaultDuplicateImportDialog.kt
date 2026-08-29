package com.codeshell.app.ui.vault

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.codeshell.app.R

fun vaultImportSummaryMessage(summary: VaultImportSummary): String {
    fun fileCount(count: Int): String = "$count ${if (count == 1) "file" else "files"}"

    val message = StringBuilder(fileCount(summary.importedCount) + " imported")
    if (summary.skippedCount > 0) {
        message.append(if (summary.replacedCount > 0) ", " else " and ")
            .append(summary.skippedCount)
            .append(" skipped")
    }
    if (summary.replacedCount > 0) {
        message.append(if (summary.skippedCount > 0) ", " else " and ")
            .append(summary.replacedCount)
            .append(" replaced")
    }
    return message.toString()
}

@Composable
fun VaultDuplicateImportDialog(
    duplicate: VaultDuplicateImport,
    onReplace: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.vault_duplicate_title)) },
        text = { Text(stringResource(R.string.vault_duplicate_message, duplicate.fileName)) },
        confirmButton = {
            TextButton(onClick = onReplace) {
                Text(stringResource(R.string.vault_duplicate_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.vault_duplicate_skip))
            }
        },
    )
}
