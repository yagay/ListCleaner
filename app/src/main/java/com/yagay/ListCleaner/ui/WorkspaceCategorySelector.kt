package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
 * Shared category/Filter toolbar geometry for Entry, Component and Ordering.
 *
 * Category and Filter intentionally use one Row measure contract: the category
 * takes all remaining space while Filter stays content-sized. Keeping insets
 * and gap here prevents screens from accidentally displaying differently sized
 * category cards even though they use the same picker.
 */
@Composable
internal fun WorkspaceCategoryFilterRow(
    category: @Composable () -> Unit,
    filter: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = MaterialUiTokens.pageHorizontal,
            vertical = 4.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filter()
        Box(Modifier.weight(1f)) { category() }
    }
}

/** Unified top-row trigger used by category and filter. */
@Composable
internal fun WorkspacePickerButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 0
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(MaterialUiTokens.listCardRadius),
        color = if (selected) MaterialFlatPalette.selectedSurface()
            else MaterialFlatPalette.surface(),
        contentColor = if (selected) MaterialSelectionPalette.activeText()
            else MaterialSelectionPalette.text(),
        border = BorderStroke(0.75.dp, MaterialFlatPalette.outline())
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val color = if (selected) MaterialSelectionPalette.activeText()
                else MaterialSelectionPalette.text()
            AdaptiveIcon(icon, null, Modifier.size(18.dp), tint = color)
            AdaptiveText(
                label,
                modifier = Modifier,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (count > 0) androidx.compose.material3.Badge {
                AdaptiveText(count.toString())
            }
            AdaptiveIcon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null, Modifier.size(18.dp), tint = color
            )
        }
    }
}

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
    trailingSelector: (@Composable () -> Unit)? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (trailingSelector == null) {
            WorkspacePickerButton(
                label = stringResource(R.string.lc_category_picker_current, selectedTitle),
                icon = Icons.Rounded.Layers,
                selected = true,
                expanded = expanded,
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // One shared outline, with two independent 48dp touch targets:
            // category on the left, format/domain selector on the right.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MaterialUiTokens.listCardRadius),
                color = MaterialFlatPalette.selectedSurface(),
                contentColor = MaterialSelectionPalette.activeText(),
                border = BorderStroke(0.75.dp, MaterialFlatPalette.outline()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable { expanded = true }
                            .padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val color = MaterialSelectionPalette.activeText()
                        AdaptiveIcon(Icons.Rounded.Layers, null, Modifier.size(18.dp), tint = color)
                        AdaptiveText(
                            stringResource(R.string.lc_category_picker_current, selectedTitle),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AdaptiveIcon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null, Modifier.size(18.dp), tint = color
                        )
                    }
                    VerticalDivider(modifier = Modifier.height(24.dp))
                    trailingSelector()
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 328.dp).heightIn(max = 440.dp)
        ) {
            AdaptiveText(
                menuTitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialSelectionPalette.text()
            )
            content { expanded = false }
        }
    }
}

/** Single selected-category treatment for Entry, Component and Ordering. */
@Composable
internal fun WorkspaceMenuChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    indented: Boolean = false,
) {
    Box(
        Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            .background(
                if (selected) MaterialFlatPalette.selectedSurface()
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                RoundedCornerShape(MaterialUiTokens.listCardRadius)
            )
    ) {
        AdaptiveDropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (indented) Spacer(Modifier.width(16.dp))
                    AdaptiveText(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
            },
            trailingIcon = {
                if (selected) AdaptiveIcon(
                    Icons.Rounded.Check, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            },
            onClick = onClick
        )
    }
}

/** Same inset, selected background, typography and arrow geometry as choice rows. */
@Composable
internal fun WorkspaceMenuSection(
    title: String,
    active: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            .background(
                if (active) MaterialFlatPalette.selectedSurface()
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                RoundedCornerShape(MaterialUiTokens.listCardRadius)
            )
    ) {
        AdaptiveDropdownMenuItem(
            text = {
                AdaptiveText(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingIcon = {
                AdaptiveIcon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null, Modifier.size(20.dp),
                    tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onClick
        )
    }
}

@Composable
internal fun EntryCategoryPicker(
    selected: IntentKind?,
    onSelected: (IntentKind?) -> Unit,
    modifier: Modifier = Modifier,
    includeAllKinds: Boolean = true,
    availableKinds: Set<IntentKind>? = null,
    subtypeTitle: String? = null,
    subtypeSelector: (@Composable () -> Unit)? = null,
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
        selectedTitle = if (subtypeTitle == null) currentTitle else "$currentTitle · $subtypeTitle",
        menuTitle = stringResource(R.string.lc_category_picker_entry_title),
        modifier = modifier,
        trailingSelector = subtypeSelector,
    ) { dismiss ->
        if (includeAllKinds) {
            WorkspaceMenuChoice(
                title = stringResource(R.string.common_all),
                selected = selected == null,
                onClick = { dismiss(); onSelected(null) }
            )
        }
        availableGroups.forEach { group ->
            val expanded = expandedGroup == group
            WorkspaceMenuSection(
                title = stringResource(group.titleRes()),
                active = selected?.entryGroup() == group,
                expanded = expanded,
                onClick = { expandedGroup = if (expanded) null else group }
            )
            if (expanded) {
                group.kinds()
                    .filter { availableKinds == null || it in availableKinds }
                    .forEach { kind ->
                        WorkspaceMenuChoice(
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
            WorkspaceMenuChoice(
                title = if (kind == null) stringResource(R.string.common_all)
                    else stringResource(kind.titleRes()),
                selected = selected == kind,
                onClick = { dismiss(); onSelected(kind) }
            )
        }
    }
}
