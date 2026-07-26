package com.codeshell.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    regionToggleChecked: Boolean? = null,
    onRegionToggleChange: ((Boolean) -> Unit)? = null,
    onNewsRefresh: (() -> Unit)? = null,
) {
    val showSecretToggle = secretChipDoubleTapEnabled != null && onToggleSecretChipDoubleTap != null
    val isNewsMode = onNewsRefresh != null
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
        navigationIcon = {
            if (isNewsMode) {
                IconButton(onClick = onNewsRefresh!!) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = EditorDefaultText,
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.app_name),
                color = titleColor,
                modifier = titleModifier,
            )
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (regionToggleChecked != null && onRegionToggleChange != null) {
                    RegionTextToggle(
                        checked = regionToggleChecked,
                        onToggle = onRegionToggleChange,
                    )
                }
                if (!isNewsMode) {
                    IconButton(onClick = { /* decorative only */ }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(R.string.bookmark),
                            tint = EditorDefaultText
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = TopBarBackground,
            titleContentColor = EditorDefaultText
        )
    )
}

@Composable
private fun RegionTextToggle(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(modifier = modifier) {
        Text(
            text = "INDIA",
            fontSize = 9.sp,
            fontWeight = if (!checked) FontWeight.Bold else FontWeight.Normal,
            color = if (!checked) EditorDefaultText else EditorDefaultText.copy(alpha = 0.4f),
            modifier = Modifier
                .clip(shape)
                .background(if (!checked) EditorDefaultText.copy(alpha = 0.12f) else Color.Transparent)
                .border(0.5.dp, EditorDefaultText.copy(alpha = 0.3f), shape)
                .clickable { if (checked) onToggle(false) }
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
        Text(
            text = "INTL",
            fontSize = 9.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
            color = if (checked) EditorDefaultText else EditorDefaultText.copy(alpha = 0.4f),
            modifier = Modifier
                .clip(shape)
                .background(if (checked) EditorDefaultText.copy(alpha = 0.12f) else Color.Transparent)
                .border(0.5.dp, EditorDefaultText.copy(alpha = 0.3f), shape)
                .clickable { if (!checked) onToggle(true) }
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
