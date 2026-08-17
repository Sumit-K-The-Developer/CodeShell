package com.codeshell.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeshell.app.R
import com.codeshell.app.ui.chat.ChatThemeColors
import com.codeshell.app.util.NetworkConnectivity

@Composable
fun OfflineBanner(
    colors: ChatThemeColors,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isOnline by NetworkConnectivity.observe(context.applicationContext)
        .collectAsStateWithLifecycle(initialValue = NetworkConnectivity.hasInternet(context))

    if (!isOnline) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = colors.text,
            )
            Text(
                text = stringResource(R.string.no_internet_connection),
                style = MaterialTheme.typography.bodySmall,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
