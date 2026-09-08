package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.IntentKind

@Composable
internal fun CompactSearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
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
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
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
    onBackup: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (expanded) CompactSearchField(query, onQuery)
            else Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, maxLines = 1)
        },
        navigationIcon = {
            if (expanded) IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close_search))
            }
        },
        actions = {
            if (expanded) {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                }
            } else {
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.common_search)) }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh)) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.common_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.restore_backup)) },
                            onClick = { menu = false; onRestore() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_backup)) },
                            onClick = { menu = false; onBackup() }
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun ListControls(
    state: MainState,
    onFilter: (IntentKind?) -> Unit,
    onUiFilter: (UiFilter) -> Unit,
    includeAllKinds: Boolean = true,
    viewTitle: @Composable (UiFilter) -> String = { stringResource(it.titleRes()) },
    onSelectAll: (() -> Unit)? = null,
    onInvert: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items((if (includeAllKinds) listOf<IntentKind?>(null) else emptyList()) + IntentKind.entries) { kind ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { onFilter(kind) }) {
                        Text(
                            kind?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.common_all),
                            fontWeight = if (state.filter == kind) FontWeight.Bold else FontWeight.Normal,
                            color = if (state.filter == kind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        Modifier.height(2.dp).width(24.dp).background(
                            if (state.filter == kind) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.view_filter), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                TextButton(onClick = { menu = true }) {
                    Text(viewTitle(state.uiFilter))
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    UiFilter.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(viewTitle(mode)) },
                            leadingIcon = { if (mode == state.uiFilter) Icon(Icons.Rounded.Check, null) },
                            onClick = { menu = false; onUiFilter(mode) }
                        )
                    }
                }
            }
            if (onSelectAll != null || onInvert != null) {
                Spacer(Modifier.weight(1f))
                onSelectAll?.let { action ->
                    TextButton(onClick = action) { Text(stringResource(R.string.select_all)) }
                }
                onInvert?.let { action ->
                    TextButton(onClick = action) { Text(stringResource(R.string.invert_selection)) }
                }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        HorizontalDivider()
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge)
            Text(
                if (!compact) state.syncStatus
                else if (state.runtime.ready) stringResource(R.string.module_system_confirmed)
                else stringResource(R.string.module_check_status),
                style = MaterialTheme.typography.labelSmall,
                color = if (status.outdated || !state.runtime.ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Rounded.ExpandMore, stringResource(R.string.module_view_status))
    }
}
