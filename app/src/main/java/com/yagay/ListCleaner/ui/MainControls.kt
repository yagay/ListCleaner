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
) {
    var menu by remember { mutableStateOf(false) }
    var spaceMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (expanded) CompactSearchField(query, onQuery)
            else AdaptiveText(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, maxLines = 1)
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
                            AdaptiveText(spaces.firstOrNull { it.userId == selectedSpace }?.title ?: stringResource(R.string.space_user_title, selectedSpace), maxLines = 1)
                        }
                        AdaptiveDropdownMenu(expanded = spaceMenu, onDismissRequest = { spaceMenu = false }) {
                            spaces.forEach { user ->
                                AdaptiveDropdownMenuItem(
                                    text = { AdaptiveText(stringResource(R.string.space_menu_item, user.title, user.userId)) },
                                    leadingIcon = { if (user.userId == selectedSpace) AdaptiveIcon(Icons.Rounded.Check, null) },
                                    onClick = { spaceMenu = false; onSpaceSelected(user.userId) },
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
                    AdaptiveDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        AdaptiveDropdownMenuItem(text = { AdaptiveText(stringResource(R.string.restore_backup)) }, onClick = { menu = false; onRestore() })
                        AdaptiveDropdownMenuItem(text = { AdaptiveText(stringResource(R.string.export_backup)) }, onClick = { menu = false; onBackup() })
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
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = MaterialUiTokens.pageHorizontal,
                vertical = 4.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntryCategoryPicker(
                selected = state.filter,
                onSelected = onFilter,
                modifier = Modifier.weight(1f),
                includeAllKinds = includeAllKinds,
                availableKinds = availableEntryKinds
            )
            WorkspaceFilterBar(
                appTypeFilter = appTypeFilter,
                onAppTypeFilter = onAppTypeFilter,
                viewFilter = state.uiFilter,
                onViewFilter = onUiFilter,
                viewTitle = viewTitle,
                extraFilter = extraFilter
            )
        }
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
    UiCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UiCardVariant.STATUS,
        warning = !status.connected || status.outdated || status.error != null
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MaterialUiTokens.detailHeight)
                .padding(horizontal = MaterialUiTokens.cardPadding, vertical = MaterialUiTokens.contentGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
            AdaptiveText(stringResource(titleRes), style = MaterialTheme.typography.labelLarge)
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
