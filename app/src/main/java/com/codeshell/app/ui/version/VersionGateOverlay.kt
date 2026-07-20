package com.codeshell.app.ui.version

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.codeshell.app.R

@Composable
fun VersionGateOverlay(
    uiState: VersionGateUiState,
    onDismissSoftUpdate: () -> Unit,
    onOpenStore: () -> Unit,
    onExitApp: () -> Unit,
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.showSoftUpdate && uiState.config != null) {
        val config = uiState.config
        AlertDialog(
            onDismissRequest = onDismissSoftUpdate,
            title = { Text(config.updateTitle) },
            text = { Text(config.updateMessage) },
            confirmButton = {
                TextButton(onClick = onOpenStore) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSoftUpdate) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }

    if (uiState.showBlockingUpdate && uiState.config != null) {
        val config = uiState.config
        AlertDialog(
            onDismissRequest = { },
            title = { Text(config.updateTitle) },
            text = { Text(config.updateMessage) },
            confirmButton = {
                TextButton(onClick = onOpenStore) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = onExitApp) {
                    Text(stringResource(R.string.exit_app))
                }
            }
        )
    }

    if (uiState.showDiscontinued && uiState.config != null) {
        val config = uiState.config
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.app_discontinued_title)) },
            text = {
                Text(
                    config.updateMessage.ifBlank {
                        stringResource(R.string.app_discontinued_message)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onExitApp) {
                    Text(stringResource(R.string.exit_app))
                }
            }
        )
    }
}

@Composable
fun rememberExitAppAction(): () -> Unit {
    val context = LocalContext.current
    return {
        (context as? Activity)?.finishAffinity()
    }
}
