package com.yagay.ListCleaner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
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
import androidx.compose.material.icons.rounded.Visibility
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
        Box(Modifier.weight(1f)) { category() }
        filter()
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
    count: Int = 0,
    previewActions: List<WorkspaceMenuAction> = emptyList()
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            if (previewActions.isNotEmpty()) {
                WorkspaceCategoryPreviewIcon(previewActions)
            } else {
                AdaptiveIcon(icon, null, Modifier.size(18.dp), tint = color)
            }
            AdaptiveText(
                label,
                modifier = Modifier,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (count > 0) AdaptiveText(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            AdaptiveIcon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null, Modifier.size(18.dp), tint = color
            )
        }
    }
}

/**
 * Category preview is independent from category selection. On All categories
 * the eye opens a flat list of previewable kinds; on one kind it opens directly.
 */
@Composable
private fun WorkspaceCategoryPreviewIcon(actions: List<WorkspaceMenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier.size(18.dp).clickable(
            enabled = actions.any { it.enabled },
            role = Role.Button,
            onClick = {
                if (actions.size == 1) actions.single().onClick()
                else expanded = true
            }
        )
    ) {
        AdaptiveIcon(
            Icons.Rounded.Visibility,
            stringResource(R.string.system_preview_action),
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        if (actions.size > 1) {
            FlatWorkspaceDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 440.dp),
                menuWidth = 280.dp
            ) {
                actions.forEach { action ->
                    WorkspaceFlatMenuRow(
                        title = action.label,
                        selected = false,
                        onClick = {
                            expanded = false
                            if (action.enabled) action.onClick()
                        }
                    )
                }
            }
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
    previewActions: List<WorkspaceMenuAction> = emptyList(),
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
                modifier = Modifier.fillMaxWidth(),
                previewActions = previewActions
            )
        } else {
            // Two independent 48dp targets on the same flat page surface.
            // No isolated rounded card or filled selection background.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable { expanded = true }
                            .padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val color = MaterialTheme.colorScheme.primary
                        if (previewActions.isNotEmpty()) {
                            WorkspaceCategoryPreviewIcon(previewActions)
                        } else {
                            AdaptiveIcon(Icons.Rounded.Layers, null, Modifier.size(18.dp), tint = color)
                        }
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
        FlatWorkspaceDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 440.dp),
            menuWidth = 280.dp,
            header = { FlatPopupHeader(menuTitle) }
        ) {
            content { expanded = false }
        }
    }
}

/** Shared, cardless menu item for categories and filter choices. */
@Composable
internal fun WorkspaceFlatMenuRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    indented: Boolean = false,
    section: Boolean = false,
    compactOption: Boolean = false,
    expanded: Boolean = false,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    // Expansion is a visual state of the header, not a selected cleanup rule.
    // Keep the same 48dp menu row geometry and flat, square surface.
    val sectionExpanded = section && expanded
    val targetColor = if (sectionExpanded) {
        MaterialTheme.colorScheme.primary.copy(
            alpha = if (LocalListCleanerDarkTheme.current) 0.12f else 0.08f
        ).compositeOver(MaterialTheme.colorScheme.surface)
    } else MaterialTheme.colorScheme.surface
    val rowColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 180),
        label = "workspaceCategorySectionColor"
    )
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
        AdaptiveDropdownMenuItem(
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            containerColor = rowColor,
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (indented) {
                        // Reserve the same indentation without decorating every subitem.
                        // Its theme-colored text and checkmark already identify selection.
                        Spacer(Modifier.width(31.dp))
                    } else {
                        if (section) {
                            // Header rail follows expansion, never a child selection.
                            FlatSelectionRail(expanded)
                        } else {
                            // Ordinary choices retain the shared selected rail contract.
                            FlatSelectionRail(selected)
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    AdaptiveText(
                        title,
                        // Category/Filter section titles: 16sp; their options: 14sp.
                        // Other popup actions keep the existing 16sp text.
                        style = when {
                            section -> MaterialTheme.typography.titleMedium
                            indented || compactOption -> MaterialTheme.typography.bodyMedium
                            else -> MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = if (section || selected) FontWeight.SemiBold
                            else FontWeight.Normal,
                        color = when {
                            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            selected || sectionExpanded -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailingIcon = {
                val icon = trailingIcon ?: if (selected) Icons.Rounded.Check else null
                if (icon != null) AdaptiveIcon(
                    icon, null, Modifier.size(20.dp),
                    tint = if (selected || sectionExpanded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onClick
        )
        AdaptiveHorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            thickness = 0.5.dp
        )
    }
}

@Composable
internal fun WorkspaceMenuChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    indented: Boolean = false,
) {
    WorkspaceFlatMenuRow(
        title = title,
        selected = selected,
        onClick = onClick,
        indented = indented,
        compactOption = true
    )
}

@Composable
internal fun WorkspaceMenuSection(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    WorkspaceFlatMenuRow(
        title = title,
        selected = false,
        expanded = expanded,
        onClick = onClick,
        section = true,
        trailingIcon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore
    )
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
    previewActions: List<WorkspaceMenuAction> = emptyList(),
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
        previewActions = previewActions,
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
    modifier: Modifier = Modifier,
    previewActions: List<WorkspaceMenuAction> = emptyList()
) {
    WorkspaceCategoryPicker(
        selectedTitle = if (selected == null) stringResource(R.string.common_all)
            else stringResource(selected.titleRes()),
        menuTitle = stringResource(R.string.lc_category_picker_component_title),
        modifier = modifier,
        previewActions = previewActions
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
