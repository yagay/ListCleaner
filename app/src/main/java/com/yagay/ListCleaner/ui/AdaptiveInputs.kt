package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextField as NativeTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Preserve the caller's validation, IME, error and hint behavior in both native themes. */
@Composable
internal fun AdaptiveOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialOutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier,
            enabled = enabled, singleLine = singleLine, label = label,
            placeholder = placeholder, supportingText = supportingText,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            isError = isError, textStyle = textStyle,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions
        )
        UiThemeStyle.MIUIX_STYLE -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                label?.invoke()
                if (value.isEmpty()) placeholder?.invoke()
                NativeTextField(
                    value = value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(), enabled = enabled,
                    singleLine = singleLine, keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    textStyle = if (textStyle == TextStyle.Default) MiuixTheme.textStyles.main else textStyle,
                    borderColor = if (isError) MaterialTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                )
                supportingText?.invoke()
            }
        }
    }
}
