package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
    onBack: () -> Unit,
    onAllApps: () -> Unit,
    onComponentKind: (CleanupKind) -> Unit,
    onAllComponents: () -> Unit,
    componentScan: RootComponentScan? = null,
    componentScanning: Boolean = false,
    onRefreshComponents: (() -> Unit)? = null,
    status: @Composable () -> Unit,
    availableGroups: List<EntryGroup> = EntryGroup.entries,
    showUtilities: Boolean = true,
    showComponentSection: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(WorkspaceUiTokens.sectionGap)
    ) {
        status()
        if (selectedGroup == null) {
            WorkspaceSectionTitle(
                stringResource(
                    if (showComponentSection) R.string.workspace_rule_section
                    else R.string.workspace_choose_group
                )
            )
            val groups = availableGroups
            groups.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WorkspaceUiTokens.categoryGap)) {
                    pair.forEach { group ->
                        EntryGroupTile(
                            group = group,
                            modifier = Modifier.weight(1f),
                            onClick = { onGroup(group) }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
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
                AdaptiveIconButton(onClick = onBack) {
                    AdaptiveIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                }
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
            val kinds = selectedGroup.kinds()
            kinds.forEachIndexed { index, kind ->
                val position = when {
                    kinds.size == 1 -> ListRowPosition.SINGLE
                    index == 0 -> ListRowPosition.FIRST
                    index == kinds.lastIndex -> ListRowPosition.LAST
                    else -> ListRowPosition.MIDDLE
                }
                AdaptiveGroupSurface(Modifier.fillMaxWidth(), position) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onKind(kind) }
                            .heightIn(min = 58.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AdaptiveText(
                                stringResource(kind.titleRes()),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            AdaptiveText(
                                stringResource(kind.descriptionRes()),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AdaptiveIcon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp))
                    }
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
    WorkspaceCategoryCard(
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
