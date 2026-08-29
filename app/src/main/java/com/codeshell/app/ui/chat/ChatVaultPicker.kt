package com.codeshell.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeshell.app.R
import com.codeshell.app.data.model.VaultItem
import com.codeshell.app.ui.theme.OnlineGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-select chat vault picker. Lets the user pick one or more vault items
 * and add them as pending media drafts in a chat (never auto-sent).
 */
@Composable
fun ChatVaultPickerDialog(
    items: List<VaultItem>,
    loading: Boolean,
    onThumbnail: suspend (VaultItem) -> Result<ByteArray>,
    onAddDrafts: (List<VaultItem>) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxSize(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.vault_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    items.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.vault_picker_empty)) }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        gridItems(items, key = { it.id }) { item ->
                            val selected = item.id in selectedIds
                            ChatVaultPickerItem(
                                item = item,
                                onThumbnail = onThumbnail,
                                selected = selected,
                                onToggle = {
                                    selectedIds = if (selected) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.vault_picker_selected, selectedIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                selectedIds = emptySet()
                                onCancel()
                            },
                        ) { Text(stringResource(R.string.cancel)) }
                        TextButton(
                            onClick = { selectedIds = emptySet() },
                            enabled = selectedIds.isNotEmpty(),
                        ) { Text(stringResource(R.string.clear)) }
                        TextButton(
                            onClick = {
                                val selected = items.filter { it.id in selectedIds }
                                selectedIds = emptySet()
                                onAddDrafts(selected)
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) { Text(stringResource(R.string.vault_picker_confirm)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatVaultPickerItem(
    item: VaultItem,
    onThumbnail: suspend (VaultItem) -> Result<ByteArray>,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    var thumbnail by remember(item.id, item.contentVersion) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id, item.contentVersion) {
        thumbnail = withContext(Dispatchers.IO) {
            onThumbnail(item).getOrNull()?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
        }
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onToggle)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) OnlineGreen else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val preview = thumbnail
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = item.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
            // Play-button overlay for video items.
            if (item.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            // Checkmark overlay for selected items.
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0x80FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = OnlineGreen,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Text(
            text = item.fileName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
