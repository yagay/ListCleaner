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
                           "Checkbox(", "IconButton(", "FlatRowSurface("],
    "MaterialActionColors.kt": ["fun filled()", "fun outlined()", "fun outlinedBorder(",
                                 "fun text()", "fun icon()", "fun filter()", "fun navigation()",
                                 "NavigationBarItemDefaults.colors(", "FilterChipDefaults.filterChipColors("],
    "UiCard.kt": ["enum class UiCardVariant", "UiCardVariant.SECTION", "UiCardVariant.CATEGORY",
                   "UiCardVariant.LIST_GROUP", "UiCardVariant.STATUS",
                   "Surface(", "RectangleShape", "MaterialFlatPalette.sectionSurface()",
                   "onClick = onClick", "UiCardDetailRow(", "heightIn(min = MaterialUiTokens.detailHeight)"],
    "MaterialDesignSystem.kt": ["MaterialSectionCard(", "FlatRowSurface(", "val cardShape get() ="],
    "WorkspaceLayout.kt": ["enum class ListRowPosition", "FlatSectionHeader(title)"],
    "MainControls.kt": ["UiCardVariant.STATUS", "warning = true"],
    "MainActivity.kt": ["NavigationBarItem(", "colors = MaterialActionColors.navigation()",
                        "Destination.SETTINGS -> SettingsTabContent(",
                        "state.destination != Destination.SETTINGS"],
    "RuntimePanel.kt": ["UiCardVariant.STATUS"],
    "WorkspaceAppRow.kt": ["MaterialAppRowBody(", "heightIn(min = MaterialUiTokens.listRowHeight)",
                           "AppIcon(icon, title)", "Row("],
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
                              "FlatCategoryRow(", "onSelect(section)",
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
    "ListSettings.kt": ["fun SettingsListControls()",
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
    "MainViewModel.kt": ["SETTINGS(Icons.Rounded.Settings)"],
    "TextResources.kt": ["Destination.SETTINGS -> R.string.nav_settings"],
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
# Unified List First renderer across Cleanup, Ordering, Settings and details.
flat_visual_checks = {
    "FlatListDesign.kt": ("fun FlatRowSurface(", "fun FlatCategoryRow(",
                          "fun FlatSectionHeader(", "drawWithContent", "ListItem(",
                          "RectangleShape", "onClick = onClick"),
    "MaterialActionColors.kt": ("object MaterialFlatPalette",
                                 "fun detailSurface()", "fun selectedSurface()"),
    "MaterialDesignSystem.kt": ("FlatRowSurface(", "fun MaterialSettingsRow(",
                                "fun MaterialSectionCard(", "shadowElevation = 0.dp"),
    "WorkspaceGroupedRows.kt": ("FlatRowSurface(", "ContinuousApplicationSurface(",
                                 "ContinuousDetailSurface(", "MaterialFlatPalette.detailSurface()"),
    "WorkspaceAppRow.kt": ("ContinuousApplicationSurface { appContent() }",
                            "MaterialAppRowBody(", "heightIn(min = MaterialUiTokens.listRowHeight)"),
    "WorkspaceCommon.kt": ("WorkspaceCategoryRow(", "FlatCategoryRow(",
                            "WorkspaceDetailContainer(position, selected = false)",
                            "WorkspaceDetailContainer(position, checked, contentBlock)",
                            "UiCardDetailRow(modifier = clickModifier, content = rowContents)"),
    "SettingsWidgets.kt": ("FlatSectionHeader(title)", "MaterialSectionCard { content() }",
                           "FlatRowSurface(dividerInset = 0.dp)", "FlatRowSurface("),
    "SettingsCenterHome.kt": ("FlatCategoryRow(", "onSelect(section)",
                              "MaterialPageColumn(fullBleed = true)"),
    "AdaptiveWidgets.kt": ("fun AdaptiveGroupSurface(", "= FlatRowSurface("),
    "AppScopePicker.kt": ("FlatRowSurface(Modifier.fillMaxWidth())",),
    "WorkspaceCategorySelector.kt": ("filter()", "Box(Modifier.weight(1f)) { category() }",
                                      "MaterialFlatPalette.selectedSurface()"),
}
for filename, symbols in flat_visual_checks.items():
    content = (root / filename).read_text(encoding="utf-8")
    for symbol in symbols:
        if symbol not in content:
            errors.append(f"{filename}: missing shared List First visual contract: {symbol}")

# Category is the primary, left-hand control on every direct-list editor.
# A prior refactor accidentally reversed the UI despite the documented contract.
picker_source = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
picker_row = picker_source.split("internal fun WorkspaceCategoryFilterRow(", 1)[-1].split(
    "/** Unified top-row trigger", 1
)[0]
category_position = picker_row.find("Box(Modifier.weight(1f)) { category() }")
filter_position = picker_row.find("        filter()")
if category_position < 0 or filter_position < 0 or category_position >= filter_position:
    errors.append("WorkspaceCategorySelector.kt: Category must precede Filter in the top toolbar")
toolbar_source = (root / "MainActivity.kt").read_text(encoding="utf-8")
if "else -> R.string.app_name" not in toolbar_source:
    errors.append("MainActivity.kt: Cleanup top toolbar must retain the List Cleaner app name")

# The pinned Entry/Root controls must stay flat: no filled category box,
# pill borders or status badges after layout refactors.
if "border = BorderStroke(" in picker_body or "shape = RoundedCornerShape(" in picker_body:
    errors.append("WorkspaceCategorySelector.kt: category/filter triggers must remain flat")
if "androidx.compose.material3.Badge {" in picker_body:
    errors.append("WorkspaceCategorySelector.kt: pill badge returned to the flat toolbar")
category_ui = category.split("private fun WorkspaceCategoryPicker(", 1)[1].split(
    "/** Single selected-category treatment", 1
)[0]
if "shape = RectangleShape" not in category_ui:
    errors.append("WorkspaceCategorySelector.kt: subtype picker must share flat styling")

# Expansion controls and Root/LSPosed actions must not be dropped.
details = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
if "WorkspaceDetailSelectionIndicator(" not in details or "shape = androidx.compose.foundation.shape.CircleShape" not in details:
    errors.append("WorkspaceCommon.kt: expanded Entry/Root rows must use a circular detail indicator")
detail_row = details.split("internal fun WorkspaceDetailRow(", 1)[-1]
if "AdaptiveCheckbox(" in detail_row or "WorkspaceDetailSelectionIndicator(" not in detail_row:
    errors.append("WorkspaceCommon.kt: detail rows must not fall back to a square checkbox")
if "toggleableState = if (checked)" not in detail_row:
    errors.append("WorkspaceCommon.kt: detail checkbox selection semantics missing")
for symbol in ("WorkspaceReadOnlyDetailRow(", "WorkspaceDetailRow(",
               "UiCardDetailRow(modifier = clickModifier, content = rowContents)",
               "ContinuousDetailSurface(content)", "BulkLockContextMenu("):
    if symbol not in details:
        errors.append(f"WorkspaceCommon.kt: expanded component contract missing: {symbol}")

# Preview tabs, result list and choice dialogs share Material3 list affordances.
if "AdaptiveFilterChip(" not in (root / "SystemProbeDialog.kt").read_text(encoding="utf-8"):
    errors.append("SystemProbeDialog.kt: preview filter tabs lack visible selected state")
if "AdaptiveAlertDialog(" not in (root / "AppearanceSettings.kt").read_text(encoding="utf-8"):
    errors.append("AppearanceSettings.kt: custom color editor bypassed shared dialog")

# One runtime List First mode; legacy settings cannot restore category-home routing.
prefs_source = (root / "UiPreferences.kt").read_text(encoding="utf-8")
for token in ("val homeLayout: UiHomeLayout = UiHomeLayout.CLASSIC_LIST",
              "homeLayout = UiHomeLayout.CLASSIC_LIST",
              "upgradedListLayout(savedLayout, migrated)"):
    if token not in prefs_source:
        errors.append(f"UiPreferences.kt: List First default missing: {token}")
app_row = (root / "WorkspaceAppRow.kt").read_text(encoding="utf-8")
if "androidx.compose.material3.ListItem(" in app_row:
    errors.append("WorkspaceAppRow.kt: two-line ListItem padding makes list too tall")

# The only active workspace routing uses one list, not the old category-home.
for filename in ("MainTabs.kt", "RootComponentsScreen.kt", "PriorityDialog.kt"):
    code = (root / filename).read_text(encoding="utf-8")
    for forbidden in ("EntryWorkspaceHome(", "RootWorkspaceHome(", "EntryWorkspaceBreadcrumb(",
                      "HomeLayoutPolicy.showCategoryHome(", "classicLayout",
                      "sortingGroup", "selectedGroup"):
        if forbidden in code:
            errors.append(f"{filename}: obsolete alternative route: {forbidden}")
if "R.string.lc_home_layout_title" in (root / "AppearanceSettings.kt").read_text(encoding="utf-8"):
    errors.append("AppearanceSettings.kt: remove duplicate layout mode setting")

# Prevent duplicate app/detail renderers and verify preview rows.
for filename in ("WorkspaceAppRow.kt", "WorkspaceCommon.kt", "RootComponentsScreen.kt"):
    if "if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED)" in (root / filename).read_text(encoding="utf-8"):
        errors.append(f"{filename}: legacy Grouped style still changes row rendering")
if "FlatRowSurface(dividerInset = 0.dp)" not in (root / "SystemProbeDialog.kt").read_text(encoding="utf-8"):
    errors.append("SystemProbeDialog.kt: runtime candidate rows are not flat list rows")

# Only one editor presentation remains; legacy category landing files are removed.
for filename in ("EntryWorkspaceHome.kt", "RootWorkspaceHome.kt"):
    if (root / filename).exists():
        errors.append(f"{filename}: obsolete category-home renderer must not return")
if "settings_home_columns" in (root / "ListSettings.kt").read_text(encoding="utf-8"):
    errors.append("ListSettings.kt: irrelevant legacy grid control is still visible")

# Reject Material Card reintroduction in the shared renderer.
cardless_source = (root / "UiCard.kt").read_text(encoding="utf-8")
if direct_card.search(cardless_source):
    errors.append("UiCard.kt: standalone card returned; use shared cardless Surface")

# Never regress back to bespoke paints for list rows and selection surfaces.
native_checks = {
    "MainControls.kt": ("OutlinedTextField(",),
    "FlatListDesign.kt": ("ListItem(",),
    "SettingsCenterHome.kt": ("FlatCategoryRow(",),
    "WorkspaceCommon.kt": ("WorkspaceCategoryRow(", "FlatCategoryRow("),
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
# A category row must retain multiline names and minimum touch height.
category_source = (root / "FlatListDesign.kt").read_text(encoding="utf-8")
for expected in ("maxLines = 2", "heightIn(min = 56.dp)", "FlatRowSurface("):
    if expected not in category_source:
        errors.append(f"FlatListDesign.kt: category row invariant missing: {expected}")


for filename, symbols in required.items():
    content = (root / filename).read_text(encoding="utf-8")
    for symbol in symbols:
        if symbol not in content:
            errors.append(f"{filename}: missing shared Material 3 component: {symbol}")

# Status and diagnostics must live inside Settings, not in a separate navigation destination.
for filename in ("MainActivity.kt", "MainTabs.kt", "MainViewModel.kt", "TextResources.kt",
                 "PriorityDialog.kt"):
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

# Cleanup switches use two flat text tabs with a bottom indicator, not a capsule.
classic = (root / "ClassicListLayout.kt").read_text(encoding="utf-8")
for expected in ("fun FlatCleanupModeTab(", "lc_cleanup_mode_entry_short",
                 "lc_cleanup_mode_component_short", "RectangleShape",
                 "heightIn(min = 48.dp)", "height(2.dp)", "Modifier.weight(1f)"):
    if expected not in classic:
        errors.append(f"ClassicListLayout.kt: flat cleanup tabs contract missing: {expected}")
for forbidden in ("SingleChoiceSegmentedButtonRow(", "SegmentedButton(",
                  "RoundedCornerShape(", "FilterChip("):
    if forbidden in classic:
        errors.append(f"ClassicListLayout.kt: capsule-like tabs returned: {forbidden}")
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
for expected in ("Surface(", "shape = RectangleShape", "heightIn(min = 48.dp)",
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
if "WorkspaceControlLabel(" not in classic or "textAlign = TextAlign.Center" not in classic:
    errors.append("ClassicListLayout.kt: flat tab labels must remain readable and centered")
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
print("Material 3 List First rows, navigation, details and settings OK")
