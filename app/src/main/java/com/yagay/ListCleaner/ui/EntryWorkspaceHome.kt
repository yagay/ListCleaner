package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.RootComponentScan
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.kinds
import com.yagay.ListCleaner.domain.entryGroup

/**
 * Rules landing flow: four independent surfaces -> the group's entry kinds ->
 * the existing, fully functional rule editor. No scanning or rule-writing code
 * lives here. Desktop and advanced kinds retain their original classifications.
 */
@Composable
internal fun EntryWorkspaceHome(
    selectedGroup: EntryGroup?,
    onGroup: (EntryGroup) -> Unit,
    onKind: (IntentKind) -> Unit,
    onPreviewKind: (IntentKind) -> Unit,
    onBack: () -> Unit,
    onAllApps: () -> Unit,
    onComponentKind: (CleanupKind) -> Unit,
    onAllComponents: () -> Unit,
    componentScan: RootComponentScan? = null,
    componentScanning: Boolean = false,
    onRefreshComponents: (() -> Unit)? = null,
    status: @Composable () -> Unit,
    availableGroups: List<EntryGroup> = EntryGroup.entries,
    availableKinds: Set<IntentKind> = IntentKind.entries.toSet(),
    showUtilities: Boolean = true,
    showComponentSection: Boolean = true,
) {
    MaterialPageColumn {
        status()
        if (selectedGroup == null) {
            WorkspaceSectionTitle(
                stringResource(
                    if (showComponentSection) R.string.workspace_rule_section
                    else R.string.workspace_choose_group
                )
            )
            // One continuous, single-column list regardless of legacy grid preferences.
            Column(Modifier.fillMaxWidth()) {
                availableGroups.forEach { group ->
                    EntryGroupTile(
                        group = group,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onGroup(group) }
                    )
                }
            }
            if (showUtilities) WorkspaceBrowseAllButton(
                label = stringResource(R.string.workspace_browse_entries),
                onClick = onAllApps
            )
            // Rule and Root component categories share the same landing page.
            // Their editors remain independent to preserve scan/lock semantics.
            if (showComponentSection) {
                RootWorkspaceCategorySection(
                    onChoose = onComponentKind,
                    onAll = onAllComponents,
                    onRefresh = onRefreshComponents,
                    scanned = componentScan?.observedAt?.let { it > 0L } == true,
                    counts = componentScan?.items?.groupingBy { it.kind }?.eachCount().orEmpty(),
                    apps = if (componentScan?.observedAt?.let { it > 0L } == true)
                        componentScan?.items.orEmpty().map { "${it.user}|${it.component.packageName}" }.distinct().size else null,
                    components = if (componentScan?.observedAt?.let { it > 0L } == true)
                        componentScan?.items.orEmpty().size else null,
                    // Scan failures are shown inside the component list, not on the homepage.
                    warning = "",
                    scanning = componentScanning
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AdaptiveBackButton(onClick = onBack)
                Column(Modifier.weight(1f)) {
                    AdaptiveText(
                        stringResource(selectedGroup.titleRes()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    AdaptiveText(
                        stringResource(R.string.workspace_choose_entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val kinds = selectedGroup.kinds().filter { it in availableKinds }
            kinds.forEachIndexed { index, kind ->
                val position = when {
                    kinds.size == 1 -> ListRowPosition.SINGLE
                    index == 0 -> ListRowPosition.FIRST
                    index == kinds.lastIndex -> ListRowPosition.LAST
                    else -> ListRowPosition.MIDDLE
                }
                AdaptiveGroupSurface(Modifier.fillMaxWidth(), position, horizontalInset = 0.dp) {
                    androidx.compose.material3.ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onKind(kind) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        headlineContent = {
                            AdaptiveText(
                                stringResource(kind.titleRes()),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            AdaptiveText(
                                stringResource(kind.descriptionRes()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AdaptiveIconButton(onClick = { onPreviewKind(kind) }) {
                                    AdaptiveIcon(
                                        Icons.Rounded.Visibility,
                                        stringResource(R.string.system_preview_action)
                                    )
                                }
                                AdaptiveIcon(Icons.Rounded.ChevronRight, null)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryGroupTile(group: EntryGroup, modifier: Modifier, onClick: () -> Unit) {
    val icon: ImageVector = when (group) {
        EntryGroup.SHARE -> Icons.Rounded.Share
        EntryGroup.OPEN -> Icons.Rounded.FolderOpen
        EntryGroup.DESKTOP -> Icons.Rounded.Home
        EntryGroup.ADVANCED -> Icons.Rounded.Tune
    }
    WorkspaceCategoryRow(
        icon = icon,
        title = stringResource(group.titleRes()),
        description = stringResource(R.string.workspace_entry_count, group.kinds().size),
        modifier = modifier,
        onClick = onClick
    )
}

/** Breadcrumb for the app editor. It is not another category picker. */
@Composable
internal fun EntryWorkspaceBreadcrumb(
    kind: IntentKind?,
    onHome: () -> Unit,
    onGroup: () -> Unit
) {
    WorkspaceBreadcrumb(
        homeLabel = stringResource(R.string.workspace_groups),
        onHome = onHome,
        groupLabel = kind?.let { stringResource(it.entryGroup().titleRes()) },
        onGroup = if (kind == null) null else onGroup,
        currentLabel = kind?.let { stringResource(it.titleRes()) }
            ?: stringResource(R.string.workspace_browse_entries)
    )
}

