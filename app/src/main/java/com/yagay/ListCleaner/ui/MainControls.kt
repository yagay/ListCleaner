package com.yagay.ListCleaner.ui

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import top.yukonga.miuix.kmp.basic.TextField as NativeMiuixTextField
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.entryGroup
import com.yagay.ListCleaner.domain.kinds

@Composable
internal fun CompactSearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    if (LocalUiThemeStyle.current == UiThemeStyle.MIUIX_STYLE) {
        NativeMiuixTextField(
            value = query, onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).heightIn(min = 48.dp),
            label = stringResource(R.string.search_apps_components_packages),
            useLabelAsPlaceholder = true, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
        )
    } else BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            .heightIn(min = 48.dp)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
        decorationBox = { innerTextField ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdaptiveIcon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        AdaptiveText(
                            stringResource(R.string.search_apps_components_packages),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        }
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
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (expanded) CompactSearchField(query, onQuery)
            else AdaptiveText(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, maxLines = 1)
        },
        navigationIcon = {
            if (expanded) AdaptiveIconButton(onClick = onClose) {
                AdaptiveIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close_search))
            }
        },
        actions = {
            if (expanded) {
                if (query.isNotEmpty()) AdaptiveIconButton(onClick = { onQuery("") }) {
                    AdaptiveIcon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                }
            } else {
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

@Composable
private fun EntryGroupButton(
    group: EntryGroup,
    activeKind: IntentKind?,
    onFilter: (IntentKind?) -> Unit,
) {
    var expanded by remember(group) { mutableStateOf(false) }
    val active = activeKind?.entryGroup() == group
    Box {
        AdaptiveTextButton(onClick = { expanded = true }) {
            AdaptiveText(
                stringResource(group.titleRes()),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
        }
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            group.kinds().forEach { kind ->
                AdaptiveDropdownMenuItem(
                    text = {
                        AdaptiveText(
                            stringResource(kind.titleRes())
                        )
                    },
                    leadingIcon = { if (kind == activeKind) AdaptiveIcon(Icons.Rounded.Check, null) },
                    onClick = { expanded = false; onFilter(kind) },
                )
            }
        }
    }
}

@Composable
internal fun ListControls(
    state: MainState,
    onFilter: (IntentKind?) -> Unit,
    onUiFilter: (UiFilter) -> Unit,
    appTypeFilter: AppTypeFilter = AppTypeFilter.ALL,
    onAppTypeFilter: (AppTypeFilter) -> Unit = {},
    includeAllKinds: Boolean = true,
    showEntryGroups: Boolean = true,
    viewTitle: @Composable (UiFilter) -> String = { stringResource(it.titleRes()) },
    extraFilter: (@Composable () -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onSelectNone: (() -> Unit)? = null,
    onInvert: (() -> Unit)? = null,
    batchCount: Int? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var appTypeMenu by remember { mutableStateOf(false) }
    var selectionMenu by remember { mutableStateOf(false) }
    var helpExpanded by remember(state.filter) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(
        if (LocalUiThemeStyle.current == UiThemeStyle.MIUIX_STYLE)
            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surface
    )) {
        if (showEntryGroups) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (includeAllKinds) {
                    AdaptiveFilterChip(
                        selected = state.filter == null,
                        onClick = { onFilter(null) },
                        label = { AdaptiveText(stringResource(R.string.common_all)) }
                    )
                }
                EntryGroup.entries.filter { includeAllKinds || it != EntryGroup.DESKTOP }.forEach { group ->
                    EntryGroupButton(group, state.filter, onFilter)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AdaptiveTextButton(onClick = { appTypeMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    AdaptiveText(stringResource(R.string.compact_filter_format, stringResource(R.string.app_type_filter), stringResource(appTypeFilter.titleRes())))
                    AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
                }
                AdaptiveDropdownMenu(expanded = appTypeMenu, onDismissRequest = { appTypeMenu = false }) {
                    AppTypeFilter.entries.forEach { mode ->
                        AdaptiveDropdownMenuItem(
                            text = { AdaptiveText(stringResource(mode.titleRes())) },
                            leadingIcon = { if (mode == appTypeFilter) AdaptiveIcon(Icons.Rounded.Check, null) },
                            onClick = { appTypeMenu = false; onAppTypeFilter(mode) }
                        )
                    }
                }
            }
            Box {
                AdaptiveTextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    AdaptiveText(stringResource(R.string.compact_filter_format, stringResource(R.string.view_filter), viewTitle(state.uiFilter)))
                    AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
                }
                AdaptiveDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    UiFilter.entries.forEach { mode ->
                        AdaptiveDropdownMenuItem(
                            text = { AdaptiveText(viewTitle(mode)) },
                            leadingIcon = { if (mode == state.uiFilter) AdaptiveIcon(Icons.Rounded.Check, null) },
                            onClick = { menu = false; onUiFilter(mode) }
                        )
                    }
                }
            }
            extraFilter?.invoke()
            if (onSelectAll != null || onSelectNone != null || onInvert != null) {
                Box {
                    AdaptiveTextButton(
                        onClick = { selectionMenu = true },
                        enabled = batchCount == null || batchCount > 0,
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        AdaptiveText(stringResource(R.string.selection_actions))
                        AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
                    }
                    AdaptiveDropdownMenu(expanded = selectionMenu, onDismissRequest = { selectionMenu = false }) {
                        onSelectAll?.let { action ->
                            AdaptiveDropdownMenuItem(text = { AdaptiveText(stringResource(R.string.select_all)) }, onClick = { selectionMenu = false; action() })
                        }
                        onSelectNone?.let { action ->
                            AdaptiveDropdownMenuItem(text = { AdaptiveText(stringResource(R.string.select_none)) }, onClick = { selectionMenu = false; action() })
                        }
                        onInvert?.let { action ->
                            AdaptiveDropdownMenuItem(text = { AdaptiveText(stringResource(R.string.invert_selection)) }, onClick = { selectionMenu = false; action() })
                        }
                    }
                }
            }
            AdaptiveTextButton(
                onClick = { helpExpanded = !helpExpanded },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
            ) {
                AdaptiveText(stringResource(R.string.entry_help_title), maxLines = 1)
                AdaptiveIcon(
                    if (helpExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        val selectedKind = state.filter
        if (helpExpanded) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                AdaptiveText(
                    if (selectedKind == null) stringResource(R.string.entry_rules_general_help)
                    else stringResource(selectedKind.descriptionRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedKind?.entryGroup() == EntryGroup.ADVANCED) {
                    Spacer(Modifier.height(4.dp))
                    AdaptiveText(
                        stringResource(R.string.entry_advanced_safety_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.loading) AdaptiveLinearProgressIndicator(Modifier.fillMaxWidth())
        AdaptiveHorizontalDivider()
    }
}

@Composable
internal fun ModuleStatusRow(state: MainState, compact: Boolean = false, onClick: () -> Unit) {
    val status = state.module
    val titleRes = when {
        !status.connected -> R.string.module_lsposed_disconnected
        status.outdated -> R.string.module_old_running
        status.error != null || (status.scopeKnown && status.missingScope.isNotEmpty()) -> R.string.module_needs_attention
        status.resolverLoaded -> R.string.module_loaded
        else -> R.string.module_lsposed_connected
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
