package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Layers
import com.yagay.ListCleaner.data.UserSpace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.entryGroup
import com.yagay.ListCleaner.domain.kinds

@Composable
internal fun CompactSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = { AdaptiveText(stringResource(R.string.search_apps_components_packages)) },
        leadingIcon = { AdaptiveIcon(Icons.Rounded.Search, contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainToolbar(
    query: String,
    expanded: Boolean,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: () -> Unit,
    onBackup: () -> Unit,
    showSearch: Boolean = true,
    spaces: List<UserSpace> = emptyList(),
    selectedSpace: Int = 0,
    onSpaceSelected: (Int) -> Unit = {},
    titleRes: Int = R.string.app_name,
) {
    var menu by remember { mutableStateOf(false) }
    var spaceMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (expanded) CompactSearchField(query, onQuery)
            else AdaptiveText(stringResource(titleRes), fontWeight = FontWeight.Bold, maxLines = 1)
        },
        navigationIcon = {
            if (expanded) AdaptiveBackButton(onClick = onClose, contentDescription = stringResource(R.string.close_search))
        },
        actions = {
            if (expanded) {
                if (query.isNotEmpty()) AdaptiveIconButton(onClick = { onQuery("") }) {
                    AdaptiveIcon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                }
            } else {
                if (spaces.size > 1) {
                    Box {
                        AdaptiveTextButton(onClick = { spaceMenu = true }) {
                            AdaptiveIcon(Icons.Rounded.Layers, stringResource(R.string.space_switch), Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            AdaptiveText(
                                spaces.firstOrNull { it.userId == selectedSpace }?.title
                                    ?: stringResource(R.string.space_user_title, selectedSpace),
                                maxLines = 1
                            )
                            // Explicit disclosure cue for the multi-space picker.
                            Spacer(Modifier.width(2.dp))
                            AdaptiveIcon(
                                if (spaceMenu) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                stringResource(R.string.space_switch),
                                Modifier.size(18.dp)
                            )
                        }
                        FlatWorkspaceDropdownMenu(expanded = spaceMenu, onDismissRequest = { spaceMenu = false }) {
                            spaces.forEach { user ->
                                WorkspaceFlatMenuRow(
                                    title = stringResource(R.string.space_menu_item, user.title, user.userId),
                                    selected = user.userId == selectedSpace,
                                    onClick = { spaceMenu = false; onSpaceSelected(user.userId) }
                                )
                            }
                        }
                    }
                }
                if (showSearch) AdaptiveIconButton(onClick = onSearch) {
                    AdaptiveIcon(Icons.Rounded.Search, stringResource(R.string.common_search))
                }
                AdaptiveIconButton(onClick = onRefresh) { AdaptiveIcon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh)) }
                Box {
                    AdaptiveIconButton(onClick = { menu = true }) { AdaptiveIcon(Icons.Rounded.MoreVert, stringResource(R.string.common_more)) }
                    FlatWorkspaceDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        WorkspaceFlatMenuRow(
                            title = stringResource(R.string.restore_backup), selected = false,
                            onClick = { menu = false; onRestore() }
                        )
                        WorkspaceFlatMenuRow(
                            title = stringResource(R.string.export_backup), selected = false,
                            onClick = { menu = false; onBackup() }
                        )
                    }
                }
            }
        }
    )
}

/** The category choice is now a single full-width Material 3 picker. */
@Composable
internal fun ListControls(
    state: MainState,
    onFilter: (IntentKind?) -> Unit,
    onUiFilter: (UiFilter) -> Unit,
    appTypeFilter: AppTypeFilter = AppTypeFilter.ALL,
    onAppTypeFilter: (AppTypeFilter) -> Unit = {},
    includeAllKinds: Boolean = true,
    showEntryGroups: Boolean = true,
    availableEntryKinds: Set<IntentKind>? = null,
    viewTitle: @Composable (UiFilter) -> String = { stringResource(it.titleRes()) },
    extraFilter: (@Composable () -> Unit)? = null,
    subtypeTitle: String? = null,
    subtypeAvailable: Boolean = true,
    previewActions: List<WorkspaceMenuAction> = emptyList(),
    modeAction: (@Composable () -> Unit)? = null,
    selectionActions: List<WorkspaceMenuAction> = emptyList(),
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        WorkspaceCategoryFilterRow(
            category = {
                EntryCategoryPicker(
                    selected = state.filter,
                    onSelected = onFilter,
                    modifier = Modifier.fillMaxWidth(),
                    includeAllKinds = includeAllKinds,
                    availableKinds = availableEntryKinds,
                    subtypeTitle = subtypeTitle,
                    previewActions = previewActions,
                    subtypeSelector = if (
                        subtypeAvailable &&
                        (state.filter == IntentKind.OPEN || state.filter == IntentKind.DEEP_LINK)
                    ) extraFilter else null
                )
            },
            beforeFilter = modeAction,
            afterFilter = { WorkspaceListActions(actions = selectionActions) },
            filter = {
                WorkspaceFilterBar(
                    appTypeFilter = appTypeFilter,
                    onAppTypeFilter = onAppTypeFilter,
                    viewFilter = state.uiFilter,
                    onViewFilter = onUiFilter,
                    viewTitle = viewTitle
                )
            }
        )
        if (state.loading) WorkspaceProgress()
        AdaptiveHorizontalDivider()
    }
}

@Composable
internal fun ModuleStatusRow(state: MainState, compact: Boolean = false, onClick: () -> Unit) {
    val status = state.module
    val titleRes = when {
        !status.connected -> R.string.module_lsposed_disconnected
        status.outdated -> R.string.module_old_running
        status.error != null -> R.string.module_needs_attention
        status.scopeKnown && status.missingScope.isNotEmpty() -> R.string.module_scope_partial
        status.resolverLoaded -> R.string.module_loaded
        else -> R.string.module_lsposed_connected
    }
    FlatRowSurface(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MaterialUiTokens.detailHeight)
                .padding(horizontal = MaterialUiTokens.cardPadding, vertical = MaterialUiTokens.contentGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
            AdaptiveText(
                stringResource(titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = if (!status.connected || status.outdated || status.error != null)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            AdaptiveText(
                if (!compact) state.syncStatus
                else if (state.runtime.ready) stringResource(R.string.module_system_confirmed)
                else stringResource(R.string.module_check_status),
                style = MaterialTheme.typography.labelSmall,
                color = if (status.outdated || !state.runtime.ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
            AdaptiveTextButton(onClick = onClick) {
                AdaptiveIcon(Icons.Rounded.ExpandMore, stringResource(R.string.module_view_status))
            }
        }
    }
}

/**
 * Compact cleanup-home alert. Healthy modules show no status card at all.
 * Only verified LSPosed problems or a recovery decision surface here.
 * Full diagnostics and recovery actions remain in the Status tab.
 */
@Composable
internal fun CleanupStatusAlert(state: MainState, onOpenStatus: () -> Unit) {
    val module = state.module
    if (!module.checked) return
    val issueRes = when {
        !module.connected -> R.string.module_lsposed_disconnected
        module.outdated -> R.string.module_old_running
        module.error != null -> R.string.module_needs_attention
        state.runtime.needsDecision -> R.string.cleanup_runtime_needs_attention
        else -> null
    } ?: return

    UiCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UiCardVariant.STATUS,
        warning = true,
        onClick = onOpenStatus
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = MaterialUiTokens.detailHeight)
                .padding(horizontal = MaterialUiTokens.cardPadding, vertical = MaterialUiTokens.contentGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdaptiveIcon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
            AdaptiveText(
                stringResource(issueRes),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            AdaptiveIcon(
                Icons.Rounded.ChevronRight,
                stringResource(R.string.module_view_status),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
