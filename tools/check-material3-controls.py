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
    "AppearanceSettings.kt": ["fun SettingsAppearanceControls(", "R.string.ui_color_mode_title", "UiColorMode.entries.forEach", "onColorModeChange(mode)",
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
                                    "WorkspaceControlLabel(", "CompactCategoryButton("],
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

# Entry/Root category selectors and Apps/View toolbar use the same scroll/spacing.
for filename in ("MainControls.kt", "WorkspaceCommon.kt", "ComponentCategoryFilter.kt"):
    code = (root / filename).read_text(encoding="utf-8")
    for expected in ("LazyRow(", "Arrangement.spacedBy(MaterialUiTokens.filterChipGap)"):
        if expected not in code:
            errors.append(f"{filename}: inconsistent Material 3 filter row: {expected}")
toolbar = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
for expected in ("appTypeFilter != AppTypeFilter.ALL", "viewFilter != UiFilter.ALL",
                 "lc_filter_help_short"):
    if expected not in toolbar:
        errors.append(f"WorkspaceCommon.kt: filter highlight or text regression: {expected}")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Material 3 shared navigation, selected-state palette, layouts, cards and app rows OK")
