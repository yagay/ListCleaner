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
    onRefresh: () -> Unit,
    counts: Map<CleanupKind, Int>,
    scanned: Boolean,
    warning: String,
    scanning: Boolean
) {
    MaterialPageColumn {
        RootWorkspaceCategorySection(
            kinds = kinds,
            apps = apps,
            components = components,
            onChoose = onChoose,
            onAll = onAll,
            onRefresh = onRefresh,
            scanned = scanned,
            counts = counts,
            warning = warning,
            scanning = scanning
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
    onRefresh: (() -> Unit)? = null,
    scanned: Boolean = false,
    counts: Map<CleanupKind, Int> = emptyMap(),
    warning: String = "",
    scanning: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.sectionGap)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WorkspaceSectionTitle(stringResource(R.string.root_screen_title))
                if (scanning) {
                    WorkspaceProgress()
                }
                if (apps != null && components != null) {
                    AdaptiveText(
                        stringResource(R.string.root_summary, apps, components),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onRefresh != null) AdaptiveIconButton(onClick = onRefresh, enabled = !scanning) {
                AdaptiveIcon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh))
            }
        }
        if (warning.isNotBlank()) WorkspaceStatusMessage(warning, isError = false)
        kinds.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.categoryGap)) {
                pair.forEach { kind ->
                    WorkspaceCategoryCard(
                        icon = when (kind) {
                            CleanupKind.LAUNCHER -> Icons.Rounded.Home
                            CleanupKind.TILE -> Icons.Rounded.GridView
                            CleanupKind.SHORTCUT -> Icons.Rounded.Link
                            CleanupKind.WIDGET -> Icons.Rounded.Widgets
                        },
                        title = stringResource(kind.titleRes()),
                        description = if (scanned) stringResource(
                            R.string.root_category_count, counts[kind] ?: 0
                        ) else stringResource(R.string.root_category_open_to_scan),
                        modifier = Modifier.weight(1f),
                        onClick = { onChoose(kind) }
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        WorkspaceBrowseAllButton(
            label = stringResource(R.string.workspace_browse_components),
            onClick = onAll
        )
    }
}
