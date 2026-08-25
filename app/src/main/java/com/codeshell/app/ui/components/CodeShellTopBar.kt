package com.codeshell.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeshell.app.BuildConfig
import com.codeshell.app.R
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.FabAccent
import com.codeshell.app.ui.theme.LearningColors
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.ui.util.tapOrDoubleTap

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
    isNewsRefreshing: Boolean = false,
    useLearningTheme: Boolean = false,
) {
    val topBarTextColor = if (useLearningTheme) LearningColors.EditorDefaultText else EditorDefaultText
    val topBarBackground = if (useLearningTheme) LearningColors.TopBarBackground else TopBarBackground
    val showSecretToggle = secretChipDoubleTapEnabled != null && onToggleSecretChipDoubleTap != null
    val isNewsMode = onNewsRefresh != null
    val titleColor = when {
        !showSecretToggle -> topBarTextColor
        secretChipDoubleTapEnabled == true -> {
            if (useLearningTheme) LearningColors.FabAccent
            else if (BuildConfig.SHELL_FLAVOR == "grammar") FabAccent else Color.White
        }
        else -> SecretEntryDisabledColor
    }
    val titleStyle = if (showSecretToggle && secretChipDoubleTapEnabled == true) {
        MaterialTheme.typography.titleLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
        )
    } else {
        MaterialTheme.typography.titleLarge
    }
    val titleModifier = if (showSecretToggle) {
        Modifier.tapOrDoubleTap(
            onTap = {},
            onDoubleTap = onToggleSecretChipDoubleTap!!,
        )
    } else {
        Modifier
    }
    val refreshRotation = if (isNewsRefreshing) {
        rememberInfiniteTransition(label = "news_refresh").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "news_refresh_rotation",
        ).value
    } else 0f

    CenterAlignedTopAppBar(
        modifier = modifier,
        navigationIcon = {
            if (isNewsMode) {
                IconButton(onClick = onNewsRefresh!!) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = topBarTextColor,
                        modifier = Modifier.graphicsLayer { rotationZ = refreshRotation },
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.app_name),
                color = titleColor,
                style = titleStyle,
                modifier = titleModifier,
            )
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (regionToggleChecked != null && onRegionToggleChange != null) {
                    RegionTextToggle(
                        checked = regionToggleChecked,
                        onToggle = onRegionToggleChange,
                        textColor = topBarTextColor,
                    )
                }
                if (!isNewsMode) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = topBarTextColor,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = topBarBackground,
            titleContentColor = topBarTextColor
        )
    )
}

@Composable
private fun RegionTextToggle(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "INDIA",
            fontSize = 9.sp,
            fontWeight = if (!checked) FontWeight.Bold else FontWeight.Normal,
            color = if (!checked) textColor else textColor.copy(alpha = 0.4f),
            modifier = Modifier
                .clickable { if (checked) onToggle(false) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(
            text = "INTL",
            fontSize = 9.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
            color = if (checked) textColor else textColor.copy(alpha = 0.4f),
            modifier = Modifier
                .clickable { if (!checked) onToggle(true) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
