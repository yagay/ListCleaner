package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.entryGroup
import com.yagay.ListCleaner.domain.kinds

/**
 * A shared Material 3 category selector for Entry, Root and Ordering.
 * Only the current selection is shown in the toolbar. Groups and subtypes
 * are displayed in the bottom sheet instead of a row of clipped chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceCategoryPicker(
    selectedTitle: String,
    sheetTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    UiCard(
        variant = UiCardVariant.SECTION,
        onClick = { open = true },
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdaptiveIcon(Icons.Rounded.Layers, null, Modifier.size(18.dp))
            AdaptiveText(
                selectedTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = MaterialUiTokens.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AdaptiveText(
                    sheetTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                content { open = false }
            }
        }
    }
}

@Composable
private fun CategoryChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    indented: Boolean = false,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = if (indented) {
            { Spacer(Modifier.width(16.dp)) }
        } else null,
        headlineContent = {
            AdaptiveText(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        },
        trailingContent = { RadioButton(selected = selected, onClick = null) }
    )
}

@Composable
internal fun EntryCategoryPicker(
    selected: IntentKind?,
    onSelected: (IntentKind?) -> Unit,
    modifier: Modifier = Modifier,
    includeAllKinds: Boolean = true,
    availableKinds: Set<IntentKind>? = null
) {
    val availableGroups = EntryGroup.entries
        .filter { includeAllKinds || it != EntryGroup.DESKTOP }
        .filter { group ->
            availableKinds == null || group.kinds().any { it in availableKinds }
        }
    val currentTitle = if (selected == null) stringResource(R.string.common_all)
        else stringResource(selected.titleRes())
    var expandedGroup by remember { mutableStateOf(selected?.entryGroup()) }
    WorkspaceCategoryPicker(
        selectedTitle = currentTitle,
        sheetTitle = stringResource(R.string.lc_category_picker_entry_title),
        modifier = modifier
    ) { dismiss ->
        if (includeAllKinds) {
            CategoryChoice(
                title = stringResource(R.string.common_all),
                selected = selected == null,
                onClick = { dismiss(); onSelected(null) }
            )
        }
        availableGroups.forEach { group ->
            val expanded = expandedGroup == group
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable {
                    expandedGroup = if (expanded) null else group
                },
                headlineContent = {
                    AdaptiveText(
                        stringResource(group.titleRes()),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                trailingContent = {
                    AdaptiveIcon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null
                    )
                }
            )
            if (expanded) {
                group.kinds().filter { availableKinds == null || it in availableKinds }
                    .forEach { kind ->
                        CategoryChoice(
                            title = stringResource(kind.titleRes()),
                            selected = selected == kind,
                            onClick = { dismiss(); onSelected(kind) },
                            indented = true
                        )
                    }
            }
        }
    }
}

@Composable
internal fun ComponentCategoryPicker(
    selected: CleanupKind?,
    onSelected: (CleanupKind?) -> Unit,
    modifier: Modifier = Modifier
) {
    WorkspaceCategoryPicker(
        selectedTitle = if (selected == null) stringResource(R.string.common_all)
            else stringResource(selected.titleRes()),
        sheetTitle = stringResource(R.string.lc_category_picker_component_title),
        modifier = modifier
    ) { dismiss ->
        ComponentCategoryFilterPolicy.choices.forEach { kind ->
            CategoryChoice(
                title = if (kind == null) stringResource(R.string.common_all)
                    else stringResource(kind.titleRes()),
                selected = selected == kind,
                onClick = { dismiss(); onSelected(kind) }
            )
        }
    }
}
