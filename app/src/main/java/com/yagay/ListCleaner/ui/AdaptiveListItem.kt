package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem as MaterialListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Surface as NativeSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** MIUIX-native item for complex picker and editor lists. */
@Composable
internal fun AdaptiveListItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL) {
        MaterialListItem(
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            trailingContent = trailingContent
        )
    } else {
        NativeSurface(color = MiuixTheme.colorScheme.surfaceContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    headlineContent()
                    supportingContent?.invoke()
                }
                trailingContent?.invoke()
            }
        }
    }
}
