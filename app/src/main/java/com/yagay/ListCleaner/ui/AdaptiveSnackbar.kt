package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost as MaterialSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Surface as NativeSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Undo remains backed by one host state; MIUIX draws its own native surface and action. */
@Composable
internal fun AdaptiveSnackbarHost(hostState: SnackbarHostState) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL) {
        MaterialSnackbarHost(hostState)
    } else {
        hostState.currentSnackbarData?.let { data ->
            NativeSurface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdaptiveText(data.visuals.message, modifier = Modifier.weight(1f))
                    data.visuals.actionLabel?.let { action ->
                        AdaptiveTextButton(onClick = { data.performAction() }) { AdaptiveText(action) }
                    }
                }
            }
        }
    }
}
