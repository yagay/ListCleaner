package com.yagay.ListCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

internal data class ListBatchAction(
    @StringRes val labelRes: Int,
    val enabled: Boolean = true,
    val run: () -> Unit
)

/** Shared action menu; business-specific confirmation remains with the caller. */
@Composable
internal fun UnifiedBatchActions(
    actions: List<ListBatchAction>,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, actions.any { it.enabled }) {
        if (!enabled || actions.none { it.enabled }) expanded = false
    }
    Box {
        AdaptiveTextButton(
            onClick = { expanded = true },
            enabled = enabled && actions.any { it.enabled },
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            AdaptiveText(stringResource(R.string.selection_actions))
            AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
        }
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                AdaptiveDropdownMenuItem(
                    text = { AdaptiveText(stringResource(action.labelRes)) },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        if (enabled && action.enabled) action.run()
                    }
                )
            }
        }
    }
}
