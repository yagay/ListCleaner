package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter

/** Consistent filter interactions shared by rules, priorities and Root components. */
@Composable
internal fun UnifiedListFilters(
    appType: AppTypeFilter,
    onAppTypeChange: (AppTypeFilter) -> Unit,
    view: UiFilter,
    onViewChange: (UiFilter) -> Unit,
    viewTitle: @Composable (UiFilter) -> String = { stringResource(it.titleRes()) },
) {
    var appMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            AdaptiveTextButton(
                onClick = { appMenu = true },
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                AdaptiveText(
                    stringResource(R.string.compact_filter_format,
                        stringResource(R.string.app_type_filter), stringResource(appType.titleRes())),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
            }
            AdaptiveDropdownMenu(expanded = appMenu, onDismissRequest = { appMenu = false }) {
                AppTypeFilter.entries.forEach { option ->
                    AdaptiveDropdownMenuItem(
                        text = { AdaptiveText(stringResource(option.titleRes())) },
                        leadingIcon = { if (appType == option) AdaptiveIcon(Icons.Rounded.Check, null) },
                        onClick = { appMenu = false; onAppTypeChange(option) }
                    )
                }
            }
        }
        Box {
            AdaptiveTextButton(
                onClick = { viewMenu = true },
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                AdaptiveText(
                    stringResource(R.string.compact_filter_format,
                        stringResource(R.string.view_filter), viewTitle(view)),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
            }
            AdaptiveDropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                UiFilter.entries.forEach { option ->
                    AdaptiveDropdownMenuItem(
                        text = { AdaptiveText(viewTitle(option)) },
                        leadingIcon = { if (view == option) AdaptiveIcon(Icons.Rounded.Check, null) },
                        onClick = { viewMenu = false; onViewChange(option) }
                    )
                }
            }
        }
    }
}
