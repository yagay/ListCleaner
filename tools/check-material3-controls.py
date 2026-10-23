#!/usr/bin/env python3
"""Guard the single Material 3 navigation/button contract against divergent widgets."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1] / "app/src/main/java/com/yagay/ListCleaner/ui"
direct_button = re.compile(
    r"(?<![A-Za-z0-9_])(?:Button|OutlinedButton|TextButton|IconButton|FilterChip|InputChip)\s*\("
)
errors = []
for file in sorted(root.glob("*.kt")):
    source = file.read_text(encoding="utf-8")
    if "top.yukonga.miuix" in source or "UiThemeStyle" in source:
        errors.append(f"{file.name}: legacy dual-theme code is not allowed")
    if file.name in ("AdaptiveWidgets.kt", "CompactCategoryButton.kt"):
        continue
    for line_no, line in enumerate(source.splitlines(), 1):
        code = line.split("//", 1)[0]
        if direct_button.search(code):
            errors.append(f"{file.name}:{line_no}: use the shared Adaptive material control")
        if "Icons.AutoMirrored.Rounded.ArrowBack" in code:
            errors.append(f"{file.name}:{line_no}: use AdaptiveBackButton")

# Six Settings sections use one Material 3 settings control system.
settings_core = (root / "SettingsWidgets.kt").read_text(encoding="utf-8")
for required in ("fun SettingsArea(", "fun <T> SettingsChoices(",
                 "fun SettingsSwitchRow(", "fun SettingsActionButton(",
                 "fun SettingsHint(", "fun SettingsSlider(",
                 "MaterialSettingsRow(", "selectedLabel",
                 "ListItemDefaults.colors(containerColor = Color.Transparent)"):
    if required not in settings_core:
        errors.append(f"SettingsWidgets.kt: unified settings component missing {required}")
if "AdaptiveFilterChip(" in settings_core or "options.size <= 2" in settings_core:
    errors.append("SettingsWidgets.kt: avoid different layout patterns based on arbitrary choice count")
row = (root / "MaterialDesignSystem.kt").read_text(encoding="utf-8")
settings_row = row.split("internal fun MaterialSettingsRow(", 1)[1].split(
    "/** All full-screen picker", 1
)[0]
for required in ("heightIn(min = MaterialUiTokens.settingsRowHeight)",
                 "Modifier.clickable(onClick = onClick)",
                 "MaterialUiTokens.settingsRowGap",
                 "MaterialTheme.typography.bodyLarge"):
    if required not in settings_row:
        errors.append(f"MaterialDesignSystem.kt: settings row geometry missing {required}")
if "ListItem(" in settings_row:
    errors.append("MaterialDesignSystem.kt: nested M3 ListItem adds duplicated settings card padding/color")
for file in ("AppearanceSettings.kt", "ListSettings.kt", "ScanSettings.kt", "SettingsSections.kt"):
    content = (root / file).read_text(encoding="utf-8")
    if "AdaptiveSwitch(" in content or "AdaptiveFilterChip(" in content:
        errors.append(f"{file}: migrate independent switch/chip layout to shared settings controls")
    if "SettingsSwitchRow(" not in content:
        errors.append(f"{file}: missing shared SettingsSwitchRow")
for file in ("ScanSettings.kt", "SettingsSections.kt", "SettingsReset.kt", "RuntimePanel.kt"):
    content = (root / file).read_text(encoding="utf-8")
    if "AdaptiveOutlinedButton(" in content or "AdaptiveButton(" in content:
        errors.append(f"{file}: use SettingsActionButton for consistent action dimensions")
    if "SettingsActionButton(" not in content:
        errors.append(f"{file}: missing unified settings action")
if "SettingsActionStyle.DANGER" not in (root / "SettingsReset.kt").read_text(encoding="utf-8"):
    errors.append("SettingsReset.kt: Reset must have clear semantic danger treatment")
palette = (root / "UiAppearance.kt").read_text(encoding="utf-8")
for required in ("secondary = primary", "tertiary = primary", "onSecondaryContainer = primary"):
    if required not in palette:
        errors.append(f"UiAppearance.kt: custom accent palette not harmonized: {required}")

# Keep action palette definitions in exactly one file.
for file in sorted(root.glob("*.kt")):
    if file.name == "MaterialActionColors.kt":
        continue
    for line_no, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
        code = line.split("//", 1)[0]
        if "ButtonDefaults.TextButtonContentPadding" in code:
            # Standard Material 3 content padding is not a custom color override.
            continue
        if re.search(r"\b(?:ButtonDefaults|IconButtonDefaults|FilterChipDefaults|NavigationBarItemDefaults)\s*\.", code):
            errors.append(f"{file.name}:{line_no}: use MaterialActionColors for action colors")

# Guard the complete visual system, not just the original button wrappers.
# Card implementations must only exist in UiCard.kt; feature wrappers delegate to it.
direct_card = re.compile(r"(?<![A-Za-z0-9_])(?:Card|OutlinedCard|ElevatedCard)\s*\(")
for file in sorted(root.glob("*.kt")):
    source = file.read_text(encoding="utf-8")
    if file.name != "UiCard.kt":
        for line_no, line in enumerate(source.splitlines(), 1):
            code = line.split("//", 1)[0]
            if direct_card.search(code):
                errors.append(f"{file.name}:{line_no}: use UiCard for Material 3 cards")
    for obsolete in ("WorkspaceUiTokens", "MaterialControlMetrics", "AdaptiveCard("):
        if obsolete in source:
            errors.append(f"{file.name}: obsolete layout tokens; use MaterialUiTokens")

required = {
    "AdaptiveWidgets.kt": ["FilterChip(", "OutlinedButton(", "TextButton(", "Button(",
                           "Checkbox(", "IconButton(", "UiCard("],
    "MaterialActionColors.kt": ["fun filled()", "fun outlined()", "fun outlinedBorder(",
                                 "fun text()", "fun icon()", "fun filter()", "fun navigation()",
                                 "NavigationBarItemDefaults.colors()", "FilterChipDefaults.filterChipColors()"],
    "UiCard.kt": ["enum class UiCardVariant", "UiCardVariant.SECTION", "UiCardVariant.CATEGORY",
                   "UiCardVariant.LIST_GROUP", "UiCardVariant.STATUS", "MaterialTheme.shapes.medium",
                   "onClick = onClick", "UiCardDetailRow(", "ListItem("],
    "MaterialDesignSystem.kt": ["UiCard(", "UiCardVariant.SECTION", "val cardShape get() ="],
    "WorkspaceLayout.kt": ["enum class ListRowPosition", "MaterialTheme.typography.titleMedium"],
    "MainControls.kt": ["UiCardVariant.STATUS", "warning = true"],
    "MainActivity.kt": ["NavigationBarItem(", "colors = MaterialActionColors.navigation()",
                        "Destination.SETTINGS -> SettingsTabContent(",
                        "state.destination != Destination.SETTINGS"],
    "RuntimePanel.kt": ["UiCardVariant.STATUS"],
    "WorkspaceAppRow.kt": ["MaterialAppRowBody(", "ListItem(", "ListItemDefaults.colors("],
    "AppScopePicker.kt": ["MaterialAppRowBody(", "MaterialFullScreenDialog("],
    "ScopeScreen.kt": ["MaterialFullScreenDialog(", "MaterialDialogActions("],
    "MainTabs.kt": ["fun RulesTab(", "fun PriorityTab(", "LazyColumn("],
    "SettingsNavigation.kt": ["fun SettingsTabContent(", "SettingsCenterHome(",
                              "TopAppBar(", "LazyColumn(", "SettingsAppearanceControls(colorMode, onColorModeChange)",
                              "SettingsListControls()", "SettingsScanControls(vm)",
                              "SettingsSection.APPEARANCE ->", "SettingsSection.LISTS ->",
                              "SettingsSection.SCANNING ->", "SettingsSection.COMPATIBILITY ->",
                              "SettingsSection.BACKUP ->", "SettingsSection.ADVANCED ->",
                              "ScopeDialog("],
    "SettingsCenterHome.kt": ["enum class SettingsSection(",
                              "SettingsSection.entries.chunked(2)",
                              "UiCardVariant.LIST_GROUP", "onSelect(section)",
                              "APPEARANCE(R.string.settings_appearance_section",
                              "LISTS(R.string.settings_list_section",
                              "SCANNING(R.string.settings_scan_section",
                              "COMPATIBILITY(R.string.settings_compat_section",
                              "BACKUP(R.string.dashboard_data_backup",
                              "ADVANCED(R.string.settings_advanced_section"],
    "UiPreferences.kt": ["data class UiPreferences(", "UiPreferencesRuntime",
                         "UiPreferencesStore", "fun write(context:", "fun read(context:",
                         "fun rememberFilter(", "fun rememberAppType(", "homeColumns", "openHelpByDefault"],
    "SettingsWidgets.kt": ["fun SettingsArea(", "fun <T> SettingsChoices(",
                           "fun SettingsSlider(", "MaterialSectionCard {",
                           "AdaptiveAlertDialog("],
    "AppearanceSettings.kt": ["fun SettingsAppearanceControls(", "R.string.ui_color_mode_title", "UiColorMode.entries.map", "onSelected = onColorModeChange",
                              "fun SettingsLanguageControls()", "settings_icon_size",
                              "lc_layout_spacing"],
    "ListSettings.kt": ["fun SettingsListControls()", "settings_home_columns",
                        "settings_remember_filters", "settings_help_expanded"],
    "ScanSettings.kt": ["fun SettingsScanControls(", "settings_scan_mode",
                       "lc_proactive_scan_mode", "lc_scan_all_now"],
    "SettingsReset.kt": ["fun SettingsResetControls("],
    "SettingsSections.kt": ["fun CompatibilitySettings(", "fun BackupSettings(",
                            "fun DiagnosticsSettings(", "RuntimePanel(state, vm)",
                            "SettingsResetControls(onColorModeChange)",
                            "ModuleStatusRow(state", "dashboard_query_hits"],
    "UiAppearance.kt": ["UiPreferencesRuntime.current.accent"],
    "RootComponentsController.kt": ["UiPreferencesRuntime.current.scanMode",
                                    "UiPreferencesRuntime.current.scanCacheMinutes"],
    "ComponentScanRefreshPolicy.kt": ["scanMode: UiScanMode", "freshnessMs: Long"],
    "CandidateGrouping.kt": ["sortMode: UiAppSort", "UiAppSort.SELECTED_FIRST", "UiAppSort.NAME",
                             "UiAppSort.PACKAGE"],
    "EntryWorkspaceHome.kt": ["UiPreferencesRuntime.current.homeColumns"],
    "RootWorkspaceHome.kt": ["UiPreferencesRuntime.current.homeColumns"],
    "MainViewModel.kt": ["SETTINGS(Icons.Rounded.Settings)"],
    "TextResources.kt": ["Destination.SETTINGS -> R.string.nav_settings"],
    "EntryWorkspaceHome.kt": ["MaterialPageColumn {"],
    "RootWorkspaceHome.kt": ["MaterialPageColumn {"],
    "WorkspaceCommon.kt": ["MaterialInlineStatus(", "MaterialUiTokens.",
                           "WorkspaceControlLabel(", "CompactCategoryButton("],
    "WorkspaceControlLabel.kt": ["fun WorkspaceControlLabel(", "MaterialTheme.typography.labelLarge"],
    "CompactCategoryButton.kt": ["fun CompactCategoryButton(", "InputChip(",
                                  "MaterialTheme.colorScheme.onSecondaryContainer"],
    "ClassicListLayout.kt": ["fun ClassicCleanupTabs(", "WorkspaceControlLabel(",
                             "active == ClassicCleanupMode.ENTRIES", "active == ClassicCleanupMode.COMPONENTS"],
    "ComponentCategoryFilter.kt": ["fun ClassicComponentCategoryFilter(",
                                    "ComponentCategoryPicker("],
    "OpenPresetFilterRow.kt": ["fun OpenPresetFilterMenu(", "WorkspaceControlLabel(",
                               "CompactCategoryButton("],
    "BrowserHostFilterRow.kt": ["fun BrowserHostFilterMenu(", "WorkspaceControlLabel(",
                                 "CompactCategoryButton("],
}
# Never regress back to bespoke paints for list rows and selection surfaces.
native_checks = {
    "MainControls.kt": ("OutlinedTextField(",),
    "SettingsCenterHome.kt": ("ListItem(",),
    "WorkspaceCommon.kt": ("WorkspaceCategoryCard(", "UiCard(", "minLines = 1", "maxLines = 2"),
    "EntryWorkspaceHome.kt": ("ListItem(",),
}
for filename, symbols in native_checks.items():
    content = (root / filename).read_text(encoding="utf-8")
    for symbol in symbols:
        if symbol not in content:
            errors.append(f"{filename}: expected Material 3 native component: {symbol}")
if "BasicTextField(" in (root / "MainControls.kt").read_text(encoding="utf-8"):
    errors.append("MainControls.kt: obsolete custom-drawn search input")
if "ComponentSelectionMark(" in (root / "AppListRows.kt").read_text(encoding="utf-8"):
    errors.append("AppListRows.kt: obsolete hand-drawn selection circle")
# Grid cards are Material 3 Card surfaces, not ListItem rows with 3 horizontal slots.
workspace_cards = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
card_source = workspace_cards.split("internal fun WorkspaceCategoryCard(", 1)[1].split(
    "/** Identical action in both cleanup groups", 1
)[0]
if "ListItem(" in card_source:
    errors.append("WorkspaceCommon.kt: M3 ListItem crowds two-column home card; use M3 Card content")
if "minLines = 2" in card_source:
    errors.append("WorkspaceCommon.kt: home card titles must not reserve a blank second line")
tokens = (root / "MaterialDesignSystem.kt").read_text(encoding="utf-8")
for expected in ("val categoryHeight get() = (88f * density).dp",
                 "val categoryPadding get() = (8f * density).dp",
                 "val categoryContentGap get() = (3f * density).dp"):
    if expected not in tokens:
        errors.append(f"MaterialDesignSystem.kt: compact home-card token missing: {expected}")
for page in ("EntryWorkspaceHome.kt", "RootWorkspaceHome.kt"):
    content = (root / page).read_text(encoding="utf-8")
    if "height(IntrinsicSize.Max)" not in content or "fillMaxHeight()" not in content:
        errors.append(f"{page}: home grid must align card heights within each row")


for filename, symbols in required.items():
    content = (root / filename).read_text(encoding="utf-8")
    for symbol in symbols:
        if symbol not in content:
            errors.append(f"{filename}: missing shared Material 3 component: {symbol}")

# Status and diagnostics must live inside Settings, not in a separate navigation destination.
for filename in ("MainActivity.kt", "MainTabs.kt", "MainViewModel.kt", "TextResources.kt",
                 "EntryWorkspaceHome.kt", "PriorityDialog.kt"):
    source = (root / filename).read_text(encoding="utf-8")
    if "Destination.DASHBOARD" in source or "DashboardTabContent(" in source:
        errors.append(f"{filename}: legacy Status destination remains; open Settings instead")
# Sort workspace status was moved into Settings; prevent duplicate status cards.
sorting_source = (root / "PriorityDialog.kt").read_text(encoding="utf-8")
for obsolete in ("ModuleStatusRow(", "RuntimePanel(", 'item(key = "status")'):
    if obsolete in sorting_source:
        errors.append(f"PriorityDialog.kt: obsolete sort-page status: {obsolete}")

settings_source = (
    (root / "SettingsNavigation.kt").read_text(encoding="utf-8") +
    (root / "SettingsSections.kt").read_text(encoding="utf-8")
)
for symbol in ("RuntimePanel(state, vm)", "dashboard_sync_status", "dashboard_query_hits",
               "dashboard_module_status", "dashboard_diagnostics", "ScopeDialog(",
               "onCollectDiagnostics", "onInspectFile"):
    if symbol not in settings_source:
        errors.append(f"Settings refactor: missing former Status feature: {symbol}")

for filename in ("AppScopePicker.kt", "ScopeScreen.kt"):
    code = (root / filename).read_text(encoding="utf-8")
    if re.search(r"\bScaffold\s*\(", code):
        errors.append(f"{filename}: use MaterialFullScreenDialog instead of a private Scaffold")

# Cleanup modes use a single-choice segmented control, not oversized FilterChips.
classic = (root / "ClassicListLayout.kt").read_text(encoding="utf-8")
for expected in ("SingleChoiceSegmentedButtonRow(", "SegmentedButton(",
                 "lc_cleanup_mode_entry_short", "lc_cleanup_mode_component_short",
                 "maxLines = 1"):
    if expected not in classic:
        errors.append(f"ClassicListLayout.kt: missing compact segmented navigation: {expected}")
if "minLines = 2" in classic:
    errors.append("ClassicListLayout.kt: full-size two-line mode tabs must not return")
if "Modifier.weight(1f)" in classic or "modifier = Modifier.fillMaxWidth()," in classic:
    errors.append("ClassicListLayout.kt: segmented borders must size to labels, not half the screen")
source = (root / "MaterialDesignSystem.kt").read_text(encoding="utf-8")
if "val filterChipGap get() = (6f * density).dp" not in source:
    errors.append("MaterialDesignSystem.kt: compact filter spacing token missing")
compact = (root / "CompactCategoryButton.kt").read_text(encoding="utf-8")
if "Arrangement.spacedBy(4.dp)" not in compact:
    errors.append("CompactCategoryButton.kt: 4dp label/icon spacing must be consistent")

# Categories use an anchored, scrollable Material 3 dropdown, not a distant bottom sheet.
category = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
for expected in ("DropdownMenu(", "UiCard(", "EntryCategoryPicker(",
                 "ComponentCategoryPicker(", "expandedGroup", "group.kinds()",
                 "availableKinds", "onSelected(null)", "widthIn(min = 240.dp",
                 "AdaptiveDropdownMenuItem("):
    if expected not in category:
        errors.append(f"WorkspaceCategorySelector.kt: anchored category picker regression: {expected}")
if "ModalBottomSheet(" in category or "LazyRow(" in category:
    errors.append("WorkspaceCategorySelector.kt: category selection must stay in an anchored popup")
picker_body = category.split("private fun WorkspaceCategoryPicker(", 1)[1].split(
    "@Composable\\nprivate fun CategoryChoice", 1
)[0]
for expected in ("variant = UiCardVariant.CATEGORY", "heightIn(min = 48.dp)",
                 "verticalAlignment = Alignment.CenterVertically",
                 "lc_category_picker_current", "FontWeight.SemiBold"):
    if expected not in picker_body:
        errors.append(f"WorkspaceCategorySelector.kt: centered/visible category label missing: {expected}")
if "modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)" in picker_body:
    errors.append("WorkspaceCategorySelector.kt: minimum height on Card rather than Row shifts label upward")
for filename in ("MainControls.kt", "ComponentCategoryFilter.kt"):
    code = (root / filename).read_text(encoding="utf-8")
    if "LazyRow(" in code:
        errors.append(f"{filename}: old horizontal category chips returned")
    if "EntryCategoryPicker(" not in code and "ComponentCategoryPicker(" not in code:
        errors.append(f"{filename}: missing shared category picker")
sheet = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
filter_popup = sheet.split("internal fun WorkspaceFilterBar(", 1)[1].split(
    "/** Batch operations", 1
)[0]
for expected in ("DropdownMenu(", "onDismissRequest = { expanded = false }",
                 "draftAppType", "draftView", "lc_filter_sheet_apply",
                 "lc_filter_sheet_reset", "Badge {", "extraFilter()",
                 "widthIn(min = 240.dp", "FilterMenuSection.APPS",
                 "FilterMenuSection.VIEW", "if (appsOpen)", "if (viewOpen)",
                 "openSection = null"):
    if expected not in filter_popup:
        errors.append(f"WorkspaceCommon.kt: anchored M3 Filter menu regression: {expected}")
if "ModalBottomSheet(" in filter_popup or "LazyRow(" in filter_popup:
    errors.append("WorkspaceCommon.kt: Filter must open near its top button, not as a sheet")
if "heightIn(max = 480.dp)" in filter_popup or "widthIn(min = 270.dp" in filter_popup:
    errors.append("WorkspaceCommon.kt: Filter must use Category-scale dropdown dimensions")
if filter_popup.count("AdaptiveDropdownMenuItem(") < 4:
    errors.append("WorkspaceCommon.kt: Filter group rows and choices must use native M3 menu items")
for expected in ("fun WorkspaceListActions(",
                 "WorkspaceListActions(actions, helpExpanded, onHelpToggle, primarySelection)"):
    if expected not in sheet:
        errors.append(f"WorkspaceCommon.kt: list actions regression: {expected}")
if "AdaptiveDropdownMenu(expanded = appMenu" in sheet:
    errors.append("WorkspaceCommon.kt: obsolete Apps dropdown must not return")
# Both cleanup modes must remain fully legible at typical font scales.
if "widthIn(min = 350.dp)" not in classic:
    errors.append("ClassicListLayout.kt: segmented row can clip Component/Root label")
# The pinned filter must have the same density and touch affordance as the
# adjacent category chips; neither list should paint into the pinned control.
if "CompactCategoryButton(" not in sheet.split("internal fun WorkspaceFilterBar(", 1)[1].split(
    "/** Batch operations", 1
)[0]:
    errors.append("WorkspaceCommon.kt: pinned filter must use Material 3 category chip")
# Fixed Filter stays beside the full-width category selector.
for file in ("MainControls.kt", "RootComponentsScreen.kt"):
    code = (root / file).read_text(encoding="utf-8")
    if "WorkspaceFilterBar(" not in code:
        errors.append(f"{file}: fixed filter must remain beside category selector")
# Entry/Ordering and Component must share their *parent Row* geometry too.
# Previously the pickers were identical but only Entry provided a symmetric
# page inset, 8dp gap and 4dp vertical padding.
category_layout = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
shared_row = category_layout.split("internal fun WorkspaceCategoryFilterRow(", 1)
if len(shared_row) != 2:
    errors.append("WorkspaceCategorySelector.kt: shared Category/Filter toolbar missing")
else:
    shared_row_body = shared_row[1].split("/**", 1)[0]
    for expected in ("MaterialUiTokens.pageHorizontal", "vertical = 4.dp",
                     "Arrangement.spacedBy(8.dp)", "Box(Modifier.weight(1f))"):
        if expected not in shared_row_body:
            errors.append(f"WorkspaceCategorySelector.kt: shared toolbar geometry missing {expected}")
for file in ("MainControls.kt", "RootComponentsScreen.kt"):
    code = (root / file).read_text(encoding="utf-8")
    if "WorkspaceCategoryFilterRow(" not in code:
        errors.append(f"{file}: category and Filter must share the same Row geometry")
    if "modifier = Modifier.padding(end = MaterialUiTokens.pageHorizontal)" in code:
        errors.append(f"{file}: legacy asymmetric Filter padding must not return")

entry = (root / "MainTabs.kt").read_text(encoding="utf-8")
entry_help = entry.split("if (ruleHelpExpanded) WorkspaceHelpSection", 1)
if len(entry_help) < 2 or "system_preview_explanation" not in entry_help[1].split(
    "if (openPreset == null", 1
)[0]:
    errors.append("MainTabs.kt: preview explanation belongs in expandable help")

for filename in ("MainTabs.kt", "RootComponentsScreen.kt", "PriorityDialog.kt"):
    code = (root / filename).read_text(encoding="utf-8")
    if "WorkspaceListActions(" not in code and "actions = listOf(" not in code:
        errors.append(f"{filename}: bulk actions must live beside the list summary")
# The list summary must share the same pinned LazyColumn header as Category/Filter.
# Two distinct stickyHeader calls cannot stack: the later one replaces the earlier.
for filename in ("MainTabs.kt", "RootComponentsScreen.kt", "PriorityDialog.kt"):
    code = (root / filename).read_text(encoding="utf-8")
    start = code.find('stickyHeader(key = "list-controls")') if filename == "MainTabs.kt" else code.find('stickyHeader(key = "controls")')
    summary_item = code.find('item(key = "list-summary")') if filename == "MainTabs.kt" else code.find('item(key = "summary")')
    sticky = code[start:summary_item] if start >= 0 and summary_item > start else ""
    expected = "SummaryRow(" if filename == "MainTabs.kt" else "WorkspaceListSummary("
    if expected not in sticky:
        errors.append(f"{filename}: list selection must share the pinned category header")
    if "item(key = " + ('"list-summary"' if filename == "MainTabs.kt" else '"summary"') not in code:
        errors.append(f"{filename}: explanatory list content must remain scrollable")
common = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
for expected in ("data class WorkspacePrimarySelection(", "AdaptiveTriStateCheckbox(",
                 "primarySelection: WorkspacePrimarySelection? = null",
                 "selection_actions"):
    if expected not in common:
        errors.append(f"WorkspaceCommon.kt: visible bulk selection contract missing: {expected}")
entry_source = (root / "MainTabs.kt").read_text(encoding="utf-8")
priority_source = (root / "PriorityDialog.kt").read_text(encoding="utf-8")
root_source = (root / "RootComponentsScreen.kt").read_text(encoding="utf-8")
for filename, source in (("MainTabs.kt", entry_source), ("PriorityDialog.kt", priority_source)):
    if "WorkspacePrimarySelection(" not in source or "ToggleableState.Indeterminate" not in source:
        errors.append(f"{filename}: tri-state select-all must reflect visible editable items")
for expected in ("WorkspacePrimarySelection(", "ToggleableState.Indeterminate",
                 "editableVisibleComponents", "if (visibleBulkState == ToggleableState.On) true else false",
                 "root_batch_action_invert", "PendingRootBatch("):
    if expected not in root_source:
        errors.append(f"RootComponentsScreen.kt: Entry-style Root selection missing: {expected}")
if "prominentActions = true" in root_source or "lc_bulk_actions_label" in common:
    errors.append("RootComponentsScreen.kt: obsolete Root-only bulk action button must not return")

# Entry/Root list internals must use exactly one render-only implementation.
# Their LSPosed and Root callbacks are intentionally *not* shared.
parts = (root / "WorkspaceListParts.kt").read_text(encoding="utf-8")
for expected in ("fun WorkspaceAppSelectionCheckbox(", "fun WorkspaceAppSummaryText(",
                 "fun WorkspaceAppNote(", "fun WorkspaceDetailHeading(",
                 "fun WorkspaceDetailCaption(", "fun WorkspaceDetailStatus(",
                 "fun WorkspaceDetailIconAction(", "detailKindMaxWidth",
                 "maxLines = 2", "Modifier.size(18.dp)"):
    if expected not in parts:
        errors.append(f"WorkspaceListParts.kt: shared Entry/Root primitive missing: {expected}")
for filename in ("AppListRows.kt", "RootComponentsScreen.kt"):
    source = (root / filename).read_text(encoding="utf-8")
    for expected in ("WorkspaceAppSelectionCheckbox(", "WorkspaceAppSummaryText(",
                     "WorkspaceAppNote(", "WorkspaceDetailHeading(",
                     "WorkspaceDetailCaption(", "WorkspaceDetailStatus(",
                     "WorkspaceDetailIconAction("):
        if expected not in source:
            errors.append(f"{filename}: Entry/Root detail presentation divergence: {expected}")
root_rows = (root / "RootComponentsScreen.kt").read_text(encoding="utf-8")
for expected in ('item(key = "app|$expansionKey", contentType = "app")',
                 'contentType = { _, _ -> "component" }'):
    if expected not in root_rows:
        errors.append(f"RootComponentsScreen.kt: lazy list row reuse must match Entry: {expected}")
tokens = (root / "MaterialDesignSystem.kt").read_text(encoding="utf-8")
if "val detailKindMaxWidth get() =" not in tokens:
    errors.append("MaterialDesignSystem.kt: shared detail kind-width token missing")

toolbar = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
for expected in ("appTypeFilter != AppTypeFilter.ALL", "viewFilter != UiFilter.ALL",
                 "lc_filter_help_short"):
    if expected not in toolbar:
        errors.append(f"WorkspaceCommon.kt: filter highlight or text regression: {expected}")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Material 3 shared navigation, selected-state palette, layouts, cards and app rows OK")
