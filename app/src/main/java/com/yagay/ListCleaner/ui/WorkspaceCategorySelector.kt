package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
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
 * Anchored Material 3 category menu for Entry, Root and Ordering.
 *
 * Unlike a bottom sheet, the popup opens next to its toolbar trigger.
 * Unlike a horizontal chip list, it never clips adjacent categories or
 * competes with the pinned Filter control for scrolling space.
 */
@Composable
private fun WorkspaceCategoryPicker(
    selectedTitle: String,
    menuTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        UiCard(
            variant = UiCardVariant.SECTION,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
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
                AdaptiveIcon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null, Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 328.dp)
        ) {
            AdaptiveText(
                menuTitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content { expanded = false }
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
    AdaptiveDropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (indented) Spacer(Modifier.width(16.dp))
                AdaptiveText(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2
                )
            }
        },
        trailingIcon = {
            if (selected) AdaptiveIcon(Icons.Rounded.Check, null, Modifier.size(20.dp))
        },
        onClick = onClick
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
    var expandedGroup by remember(selected) { mutableStateOf(selected?.entryGroup()) }
    WorkspaceCategoryPicker(
        selectedTitle = currentTitle,
        menuTitle = stringResource(R.string.lc_category_picker_entry_title),
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
            AdaptiveDropdownMenuItem(
                text = {
                    AdaptiveText(
                        stringResource(group.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2
                    )
                },
                trailingIcon = {
                    AdaptiveIcon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null, Modifier.size(20.dp)
                    )
                },
                onClick = { expandedGroup = if (expanded) null else group }
            )
            if (expanded) {
                group.kinds()
                    .filter { availableKinds == null || it in availableKinds }
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
        menuTitle = stringResource(R.string.lc_category_picker_component_title),
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
