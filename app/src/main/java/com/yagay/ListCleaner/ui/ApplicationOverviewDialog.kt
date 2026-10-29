package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.RootComponentScan

/** Read-only overview: does not write to rules, priorities, or Root components. */
@Composable
internal fun ApplicationOverviewDialog(
    packageName: String,
    state: MainState,
    rootScan: RootComponentScan,
    userId: Int,
    onDismiss: () -> Unit
) {
    val rules = overviewRules(packageName, state.candidates, state.selected,
        state.openTypesExplicit, state.browserLinks)
    val priorities = overviewPriorities(packageName, state.priorities,
        state.openTypesExplicit, state.browserLinks)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_overview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(packageName, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.app_overview_readonly), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.app_overview_rules) + " (" + rules.size + ")",
                    style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(rules, key = { it.id }) { rule ->
                        Text(stringResource(rule.kind.titleRes()) + " · " + rule.className,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.app_overview_priority) + " (" + priorities.size + ")",
                    style = MaterialTheme.typography.titleSmall)
                val rootItems = rootScan.items.filter { it.user == userId && it.component.packageName == packageName }
                Text(stringResource(R.string.app_overview_components), style = MaterialTheme.typography.titleSmall)
                if (rootScan.scannedUserId != userId || rootScan.observedAt == 0L) {
                    Text(stringResource(R.string.app_overview_root_unscanned))
                } else if (rootScan.warning.isNotBlank()) {
                    Text(rootScan.warning)
                } else {
                    Text(stringResource(R.string.app_overview_root_summary,
                        rootItems.count { it.enabled == false }, rootItems.size))
                }
                priorities.forEach { priority ->
                    Text(
                        (priority.kind?.let { stringResource(it.titleRes()) }
                            ?: priority.preset?.name ?: priority.host.orEmpty()) +
                            " · " + stringResource(R.string.app_overview_rank, priority.position),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { AdaptiveTextButton(onClick = onDismiss) { AdaptiveText(stringResource(R.string.common_ok)) } }
    )
}
