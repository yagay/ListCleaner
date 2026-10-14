package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind

/** Root component kinds are a separate feature family, NOT EntryGroup.DESKTOP. */
@Composable
internal fun RootWorkspaceHome(
    kinds: List<CleanupKind>,
    apps: Int,
    components: Int,
    onChoose: (CleanupKind) -> Unit,
    onAll: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        RootWorkspaceCategorySection(
            kinds = kinds,
            apps = apps,
            components = components,
            onChoose = onChoose,
            onAll = onAll,
            onRefresh = onRefresh
        )
    }
}

/**
 * Root component choices are a different feature family from IntentKind rules.
 * This section is reusable on the combined cleanup home without a nested scroll.
 */
@Composable
internal fun RootWorkspaceCategorySection(
    kinds: List<CleanupKind> = CleanupKind.entries,
    apps: Int? = null,
    components: Int? = null,
    onChoose: (CleanupKind) -> Unit,
    onAll: () -> Unit,
    onRefresh: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WorkspaceSectionTitle(stringResource(R.string.root_screen_title))
                if (apps != null && components != null) {
                    AdaptiveText(
                        stringResource(R.string.root_summary, apps, components),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onRefresh != null) AdaptiveIconButton(onClick = onRefresh) {
                AdaptiveIcon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh))
            }
        }
        kinds.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { kind ->
                    AdaptiveCard(
                        Modifier.weight(1f).heightIn(min = 124.dp)
                            .clickable { onChoose(kind) }
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                AdaptiveIcon(
                                    when (kind) {
                                        CleanupKind.LAUNCHER -> Icons.Rounded.Home
                                        CleanupKind.TILE -> Icons.Rounded.GridView
                                        CleanupKind.SHORTCUT -> Icons.Rounded.Link
                                        CleanupKind.WIDGET -> Icons.Rounded.Widgets
                                    },
                                    null, Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.weight(1f))
                                AdaptiveIcon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                            }
                            AdaptiveText(
                                stringResource(kind.titleRes()),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        AdaptiveOutlinedButton(onClick = onAll, modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(R.string.workspace_browse_all))
        }
    }
}
