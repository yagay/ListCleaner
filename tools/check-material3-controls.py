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
# Shared section surfaces are centralized in UiCard.kt, now using cardless Material 3 Surface.
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
                                 "NavigationBarItemDefaults.colors(", "FilterChipDefaults.filterChipColors("],
    "UiCard.kt": ["enum class UiCardVariant", "UiCardVariant.SECTION", "UiCardVariant.CATEGORY",
                   "UiCardVariant.LIST_GROUP", "UiCardVariant.STATUS",
                   "Surface(", "RectangleShape", "MaterialFlatPalette.sectionSurface()",
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
                                  "MaterialSelectionPalette.activeText()"],
    "ClassicListLayout.kt": ["fun ClassicCleanupTabs(", "WorkspaceControlLabel(",
                             "active == ClassicCleanupMode.ENTRIES", "active == ClassicCleanupMode.COMPONENTS"],
    "ComponentCategoryFilter.kt": ["fun ClassicComponentCategoryFilter(",
                                    "ComponentCategoryPicker("],
    "OpenPresetFilterRow.kt": ["fun OpenPresetFilterMenu(", "WorkspaceControlLabel(",
                               "CompactCategoryButton("],
    "BrowserHostFilterRow.kt": ["fun BrowserHostFilterMenu(", "WorkspaceControlLabel(",
                                 "CompactCategoryButton("],
}
# The flat visual system may change colors/shapes, but must never change
# the existing list/group geometry or replace selectable Material 3 rows.
flat_visual_checks = {
    "MaterialActionColors.kt": ("object MaterialFlatPalette", "fun selectedSurface()",
                                  "MaterialSelectionPalette.activeBackground()"),
    "UiCard.kt": ("variant == UiCardVariant.LIST_GROUP || variant == UiCardVariant.CATEGORY",
                  "horizontal = horizontalInset", "drawWithContent",
                  "shape = RectangleShape", "Column(content = content)"),
    "WorkspaceGroupedRows.kt": ("padding(horizontal = MaterialUiTokens.groupHorizontal)",
                                "padding(top = MaterialUiTokens.groupedOuterGap)",
                                "WorkspaceListPalette.surface()"),
    "WorkspaceAppRow.kt": ("GroupedApplicationSurface(expanded, appContent)", "AdaptiveGroupSurface(",
                           "WorkspaceListPalette.surface()"),
    "WorkspaceCategorySelector.kt": ("filter()", "Box(Modifier.weight(1f)) { category() }",
                                      "MaterialFlatPalette.selectedSurface()"),
}
for filename, symbols in flat_visual_checks.items():
    content = (root / filename).read_text(encoding="utf-8")
    for symbol in symbols:
        if symbol not in content:
            errors.append(f"{filename}: flat visual contract/unchanged geometry missing: {symbol}")

# Reject Material Card reintroduction in the shared renderer.
cardless_source = (root / "UiCard.kt").read_text(encoding="utf-8")
if direct_card.search(cardless_source):
    errors.append("UiCard.kt: standalone card returned; use shared cardless Surface")

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
for expected in ("DropdownMenu(", "Surface(", "EntryCategoryPicker(",
                 "ComponentCategoryPicker(", "expandedGroup", "group.kinds()",
                 "availableKinds", "onSelected(null)", "widthIn(min = 240.dp",
                 "AdaptiveDropdownMenuItem("):
    if expected not in category:
        errors.append(f"WorkspaceCategorySelector.kt: anchored category picker regression: {expected}")
if "ModalBottomSheet(" in category or "LazyRow(" in category:
    errors.append("WorkspaceCategorySelector.kt: category selection must stay in an anchored popup")
picker_body = category.split("internal fun WorkspacePickerButton(", 1)[1].split(
    "/**\n * Anchored Material 3 category menu", 1
)[0]
for expected in ("Surface(", "MaterialFlatPalette.selectedSurface()", "heightIn(min = 48.dp)",
                 "verticalAlignment = Alignment.CenterVertically",
                 "FontWeight.SemiBold"):
    if expected not in picker_body:
        errors.append(f"WorkspaceCategorySelector.kt: shared trigger geometry missing: {expected}")
if "WorkspacePickerButton(" not in category.split("private fun WorkspaceCategoryPicker(", 1)[1]:
    errors.append("WorkspaceCategorySelector.kt: category must use shared picker")
if "lc_category_picker_current" not in category:
    errors.append("WorkspaceCategorySelector.kt: category current label missing")
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
                 "lc_filter_sheet_reset", "WorkspacePickerButton(", "extraFilter()",
                 "widthIn(min = 240.dp", "WorkspaceFilterChoiceGroup(",
                 "AppTypeFilter.entries", "UiFilter.entries"):
    if expected not in filter_popup:
        errors.append(f"WorkspaceCommon.kt: compact direct-choice Filter regression: {expected}")
if "ModalBottomSheet(" in filter_popup or "LazyRow(" in filter_popup:
    errors.append("WorkspaceCommon.kt: Filter must open near its top button, not as a sheet")
if "heightIn(max = 480.dp)" in filter_popup or "widthIn(min = 270.dp" in filter_popup:
    errors.append("WorkspaceCommon.kt: Filter must use Category-scale dropdown dimensions")
# Category uses nested menu rows, but Filter uses direct M3 choice chips.
# Both keep a shared top-level picker trigger and the same anchored popup.
menu_shared = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
if "fun WorkspacePickerButton(" not in menu_shared:
    errors.append("WorkspaceCategorySelector.kt: shared category/filter trigger missing")
if "fun <T> WorkspaceFilterChoiceGroup(" not in sheet or "AdaptiveFilterChip(" not in sheet or "FlowRow(" not in sheet:
    errors.append("WorkspaceCommon.kt: direct-choice Material3 filter group missing")
for expected in ("fun WorkspaceListActions(",
                 "WorkspaceListActions(actions, helpExpanded, onHelpToggle)"):
    if expected not in sheet:
        errors.append(f"WorkspaceCommon.kt: list actions regression: {expected}")
if "AdaptiveDropdownMenu(expanded = appMenu" in sheet:
    errors.append("WorkspaceCommon.kt: obsolete Apps dropdown must not return")
# Both cleanup modes must remain fully legible at typical font scales.
if "widthIn(min = 350.dp)" not in classic:
    errors.append("ClassicListLayout.kt: segmented row can clip Component/Root label")
# The pinned filter must have the same density and touch affordance as the
# adjacent category chips; neither list should paint into the pinned control.
if "WorkspacePickerButton(" not in sheet.split("internal fun WorkspaceFilterBar(", 1)[1].split(
    "/** Batch operations", 1
)[0]:
    errors.append("WorkspaceCommon.kt: pinned filter must use shared category/filter trigger")
# Fixed Filter stays beside the full-width category selector.
for file in ("MainControls.kt", "RootComponentsScreen.kt"):
    code = (root / file).read_text(encoding="utf-8")
    if "WorkspaceFilterBar(" not in code:
        errors.append(f"{file}: fixed filter must remain beside category selector")
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
toolbar = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
for expected in ("appTypeFilter != AppTypeFilter.ALL", "viewFilter != UiFilter.ALL",
                 "lc_filter_help_short"):
    if expected not in toolbar:
        errors.append(f"WorkspaceCommon.kt: filter highlight or text regression: {expected}")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Material 3 shared navigation, selected-state palette, layouts, cards and app rows OK")
