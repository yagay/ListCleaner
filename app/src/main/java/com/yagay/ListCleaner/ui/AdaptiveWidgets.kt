package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

/** Shared callbacks, with separate native UI components selected at the theme boundary. */
@Composable
internal fun AdaptiveCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialCard(modifier = modifier, content = content)
        UiThemeStyle.MIUIX_STYLE -> MiuixCard(modifier = modifier, content = content)
    }
}

@Composable
internal fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        UiThemeStyle.MIUIX_STYLE -> MiuixButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
internal fun AdaptiveSwitch(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialSwitch(checked, onCheckedChange, modifier, enabled = enabled)
        UiThemeStyle.MIUIX_STYLE -> MiuixSwitch(checked, onCheckedChange, modifier, enabled = enabled)
    }
}

@Composable
internal fun AdaptiveCheckbox(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialCheckbox(checked, onCheckedChange, modifier, enabled = enabled)
        UiThemeStyle.MIUIX_STYLE -> MiuixCheckbox(checked, onCheckedChange, modifier, enabled = enabled)
    }
}
