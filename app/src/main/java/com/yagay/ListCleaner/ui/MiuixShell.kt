package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem as MiuixNavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

/** Separate native Miuix app chrome; dispatches to the shared business ViewModel. */
@Composable
internal fun MiuixMainShell(
    query: String,
    searchExpanded: Boolean,
    showSearch: Boolean,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: () -> Unit,
    onBackup: () -> Unit,
    destination: Destination,
    onDestination: (Destination) -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val destinations = Destination.entries
    val navigationItems = destinations.map {
        MiuixNavigationItem(label = stringResource(it.labelRes()), icon = it.icon)
    }
    MiuixScaffold(
        snackbarHost = snackbarHost,
        topBar = {
            Column {
                var moreExpanded by remember { mutableStateOf(false) }
                SmallTopAppBar(
                    title = stringResource(R.string.app_name),
                    navigationIcon = {
                        if (searchExpanded) {
                            MiuixIconButton(onClick = onCloseSearch) {
                                AdaptiveIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close_search))
                            }
                        }
                    },
                    actions = {
                        if (searchExpanded) {
                            MiuixIconButton(onClick = { onQuery("") }) {
                                AdaptiveIcon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                            }
                        } else {
                            if (showSearch) MiuixIconButton(onClick = onSearch) {
                                AdaptiveIcon(Icons.Rounded.Search, stringResource(R.string.common_search))
                            }
                            MiuixIconButton(onClick = onRefresh) {
                                AdaptiveIcon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh))
                            }
                            Box {
                                MiuixIconButton(onClick = { moreExpanded = true }) {
                                    AdaptiveIcon(Icons.Rounded.MoreVert, stringResource(R.string.common_more))
                                }
                                AdaptiveDropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                                    AdaptiveDropdownMenuItem(
                                        text = { AdaptiveText(stringResource(R.string.restore_backup)) },
                                        onClick = { moreExpanded = false; onRestore() }
                                    )
                                    AdaptiveDropdownMenuItem(
                                        text = { AdaptiveText(stringResource(R.string.export_backup)) },
                                        onClick = { moreExpanded = false; onBackup() }
                                    )
                                }
                            }
                        }
                    }
                )
                if (searchExpanded) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        CompactSearchField(query, onQuery)
                    }
                }
            }
        },
        bottomBar = {
            MiuixNavigationBar(
                items = navigationItems,
                selected = destinations.indexOf(destination).coerceAtLeast(0),
                onClick = { index -> destinations.getOrNull(index)?.let(onDestination) }
            )
        },
        content = content,
    )
}
