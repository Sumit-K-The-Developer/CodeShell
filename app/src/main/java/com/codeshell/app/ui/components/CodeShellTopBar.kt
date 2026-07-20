package com.codeshell.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.codeshell.app.R
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.ui.util.tapOrDoubleTap

private val SecretEntryEnabledColor = Color(0xFFE53935)
private val SecretEntryDisabledColor = Color(0xFF2E7D4F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeShellTopBar(
    modifier: Modifier = Modifier,
    secretChipDoubleTapEnabled: Boolean? = null,
    onToggleSecretChipDoubleTap: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = { /* decorative only */ }) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.bookmark),
                tint = EditorDefaultText
            )
        }
    }
) {
    val showSecretToggle = secretChipDoubleTapEnabled != null && onToggleSecretChipDoubleTap != null
    val titleColor = when {
        !showSecretToggle -> EditorDefaultText
        secretChipDoubleTapEnabled == true -> SecretEntryEnabledColor
        else -> SecretEntryDisabledColor
    }
    val titleModifier = if (showSecretToggle) {
        Modifier.tapOrDoubleTap(
            onTap = {},
            onDoubleTap = onToggleSecretChipDoubleTap!!,
        )
    } else {
        Modifier
    }

    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.app_name),
                color = titleColor,
                modifier = titleModifier,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = TopBarBackground,
            titleContentColor = EditorDefaultText
        )
    )
}
