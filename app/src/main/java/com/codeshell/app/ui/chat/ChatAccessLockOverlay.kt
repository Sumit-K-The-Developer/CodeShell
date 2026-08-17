package com.codeshell.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codeshell.app.R
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.util.filterFourDigitCode

@Composable
fun ChatAccessLockOverlay(
    digits: String,
    onDigitsChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    theme: ChatTheme,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = theme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val slotStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)

    fun requestDigitInputFocus() {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(Unit) {
        requestDigitInputFocus()
    }

    LaunchedEffect(digits) {
        if (digits.length == 4) {
            onComplete(digits)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EditorBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::requestDigitInputFocus,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::requestDigitInputFocus,
            ),
        ) {
            Text(
                text = stringResource(R.string.chat_access_code_prompt),
                style = labelStyle,
                color = colors.mutedText,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::requestDigitInputFocus,
                ),
            ) {
                repeat(4) { index ->
                    val digit = digits.getOrNull(index)
                    val display = digit?.toString() ?: "_"
                    val slotColor = if (digit != null) colors.text else colors.mutedText.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 52.dp)
                            .border(
                                width = 1.dp,
                                color = colors.accent.copy(alpha = if (index == digits.length) 0.8f else 0.35f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .background(colors.topBar, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = display,
                            style = slotStyle,
                            color = slotColor,
                        )
                    }
                }
            }
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            BasicTextField(
                value = digits,
                onValueChange = { onDigitsChange(filterFourDigitCode(it)) },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}
