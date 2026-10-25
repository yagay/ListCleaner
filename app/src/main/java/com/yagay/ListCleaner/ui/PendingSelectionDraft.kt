package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

/** UI-only draft. Neither hooks nor repositories are called before Apply. */
internal class PendingSelectionDraft<K> {
    private val overrides = mutableStateMapOf<K, Boolean>()
    val changes: Map<K, Boolean> get() = overrides.toMap()
    val count: Int get() = overrides.size

    fun selected(key: K, original: Boolean): Boolean = overrides[key] ?: original

    fun set(key: K, original: Boolean, desired: Boolean) {
        if (original == desired) overrides.remove(key) else overrides[key] = desired
    }

    fun discard() = overrides.clear()

    /** Drop only mutations verified by the backing service after Apply. */
    fun acknowledge(keys: Collection<K>) { keys.forEach(overrides::remove) }
}

/** Holds the entire draft priority ordering, including add/remove and drag actions. */
internal class PendingPriorityOrder {
    private var pending by mutableStateOf<List<String>?>(null)
    fun effective(original: List<String>): List<String> = pending ?: original
    fun stage(original: List<String>, desired: List<String>) {
        pending = desired.distinct().take(200).takeUnless { it == original }
    }
    fun hasChanges(): Boolean = pending != null
    fun discard() { pending = null }
}

@Composable
internal fun PendingChangesBar(
    count: Int,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count == 0) return
    var confirmApply by remember { mutableStateOf(false) }
    if (confirmApply) {
        AdaptiveAlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { AdaptiveText(stringResource(R.string.lc_apply_changes)) },
            text = { AdaptiveText(stringResource(R.string.lc_pending_apply_confirm, count)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    confirmApply = false
                    onApply()
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirmApply = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp, shadowElevation = 0.dp
    ) {
        Column {
            AdaptiveHorizontalDivider(thickness = 0.5.dp)
            Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdaptiveText(
                text = stringResource(R.string.lc_pending_changes_count, count),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            AdaptiveTextButton(onClick = onDiscard) {
                AdaptiveText(stringResource(R.string.lc_discard_changes))
            }
            AdaptiveTextButton(onClick = { confirmApply = true }) {
                AdaptiveText(stringResource(R.string.lc_apply_changes))
            }
            }
        }
    }
}
