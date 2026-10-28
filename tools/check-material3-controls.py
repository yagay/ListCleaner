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
    "AdaptiveWidgets.kt": ["fun AdaptiveFilterChip(", "OutlinedButton(", "TextButton(", "Button(",
                           "Checkbox(", "IconButton(", "FlatUnderlineTab("],
    "MaterialActionColors.kt": ["fun filled()", "fun outlined()", "fun outlinedBorder(",
                                 "fun text()", "fun icon()", "fun navigation()",
                                 "NavigationBarItemDefaults.colors("],
    "UiCard.kt": ["enum class UiCardVariant", "UiCardVariant.SECTION", "UiCardVariant.CATEGORY",
                   "UiCardVariant.LIST_GROUP", "UiCardVariant.STATUS",
                   "Surface(", "RectangleShape", "MaterialFlatPalette.sectionSurface()",
                   "onClick = onClick", "UiCardDetailRow(", "heightIn(min = MaterialUiTokens.detailHeight)"],
    "MaterialDesignSystem.kt": ["MaterialSectionCard(", "FlatRowSurface(", "val groupedDetailIndent get() ="],
    "WorkspaceLayout.kt": ["enum class ListRowPosition", "FlatSectionHeader(title)"],
    "MainControls.kt": ["UiCardVariant.STATUS", "warning = true"],
    "MainActivity.kt": ["NavigationBarItem(", "colors = MaterialActionColors.navigation()",
                        "Destination.SETTINGS -> SettingsTabContent(",
                        "state.destination != Destination.SETTINGS"],
    "RuntimePanel.kt": ["FlatRowSurface(", "SettingsFlatDialog("],
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
                              "ScopeDialog(", "SettingsSection.ABOUT -> AboutSettings()"],
    "SettingsCenterHome.kt": ["enum class SettingsSection(",
                              "SettingsSection.entries.filter { it != SettingsSection.ABOUT }",
                              "SettingsSection.ABOUT.icon",
                              "FlatCategoryRow(", "onSelect(section)",
                              "APPEARANCE(R.string.settings_appearance_section",
                              "LISTS(R.string.settings_list_section",
                              "SCANNING(R.string.settings_scan_section",
                              "COMPATIBILITY(R.string.settings_compat_section",
                              "BACKUP(R.string.dashboard_data_backup",
                              "ADVANCED(R.string.settings_advanced_section",
                              "ABOUT(R.string.lc_settings_about_section"],
    "SettingsAbout.kt": ["fun AboutSettings(", "MaterialSettingsRow(", "SettingsArea(",
                         "BuildConfig.VERSION_NAME", "BuildConfig.VERSION_CODE",
                         "https://github.com/yagay/ListCleaner"],
    "UiPreferences.kt": ["data class UiPreferences(", "UiPreferencesRuntime",
                         "UiPreferencesStore", "fun write(context:", "fun read(context:",
                         "fun rememberFilter(", "fun rememberAppType(", "homeColumns", "openHelpByDefault"],
    "SettingsWidgets.kt": ["fun SettingsArea(", "fun <T> SettingsChoices(",
                           "fun SettingsSlider(", "MaterialSectionCard {",
                           "SettingsFlatDialog(", "SettingsFlatPaletteTabs("],
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
    "CompactCategoryButton.kt": ["fun CompactCategoryButton(", "AdaptiveFilterChip(",
                                  "Arrangement.spacedBy(4.dp)"],
    "ClassicListLayout.kt": ["fun ClassicCleanupTabs(", "WorkspaceControlLabel(",
                             "active == ClassicCleanupMode.ENTRIES", "active == ClassicCleanupMode.COMPONENTS"],
    "ComponentCategoryFilter.kt": ["fun ClassicComponentCategoryFilter(",
                                    "ComponentCategoryPicker("],
    "OpenPresetFilterRow.kt": ["fun OpenPresetFilterMenu(", "WorkspaceControlLabel(",
                               "CompactCategoryButton("],
    "BrowserHostFilterRow.kt": ["fun BrowserHostFilterMenu(", "WorkspaceControlLabel(",
                                 "CompactCategoryButton("],
}
# All app-owned modals and selectors share the same flat rendering path.
popups = (root / "AdaptivePopups.kt").read_text(encoding="utf-8")
widgets = (root / "AdaptiveWidgets.kt").read_text(encoding="utf-8")
compact = (root / "CompactCategoryButton.kt").read_text(encoding="utf-8")
pending = (root / "PendingSelectionDraft.kt").read_text(encoding="utf-8")
for expected in ("shape = RectangleShape", "shadowElevation = 0.dp",
                 "tonalElevation = 0.dp", "border = BorderStroke(",
                 "AdaptiveDropdownMenu(", "FlatPopupMenu(", "FlatPopupHeader(",
                 "FlatPopupFooter(", "PopupPositionProvider", "PopupProperties("):
    if expected not in popups:
        errors.append(f"AdaptivePopups.kt: shared modal geometry missing {expected}")
if "RoundedCornerShape" in popups:
    errors.append("AdaptivePopups.kt: rounded global dialog still active")
if "FlatPopupMenu(" not in popups.split("internal fun FlatWorkspaceDropdownMenu(", 1)[-1]:
    errors.append("AdaptivePopups.kt: divergent menu renderer returned")
if "FlatPopupMenu(" not in popups.split("internal fun AdaptiveDropdownMenu(", 1)[-1].split(
        "internal fun AdaptiveDropdownMenuItem(", 1)[0]:
    errors.append("AdaptivePopups.kt: legacy dropdown still bypasses common popup engine")
if "import androidx.compose.material3.DropdownMenu\n" in popups:
    errors.append("AdaptivePopups.kt: old Material DropdownMenu used")
tab = widgets.split("internal fun FlatUnderlineTab(", 1)[-1].split(
    "internal fun AdaptiveFilterChip(", 1
)[0]
chip = widgets.split("internal fun AdaptiveFilterChip(", 1)[-1].split(
    "internal fun AdaptiveTriStateCheckbox(", 1
)[0]
if "shape = RectangleShape" not in tab or "height(2.dp)" not in tab:
    errors.append("AdaptiveWidgets.kt: shared flat tab geometry is missing")
if "FlatUnderlineTab(" not in chip:
    errors.append("AdaptiveWidgets.kt: filter tab must reuse shared flat renderer")
if "InputChip(" in compact or "AdaptiveFilterChip(" not in compact:
    errors.append("CompactCategoryButton.kt: old pill or duplicated compact selector returned")
if "shadowElevation = 0.dp" not in pending or "tonalElevation = 0.dp" not in pending:
    errors.append("PendingSelectionDraft.kt: pending actions must have no elevation")
if "AdaptiveButton(onClick = { confirmApply = true })" in pending:
    errors.append("PendingSelectionDraft.kt: pending Apply button must be flat")
for file in ("OpenPresetFilterRow.kt", "BrowserHostFilterRow.kt"):
    code = (root / file).read_text(encoding="utf-8")
    for wrong in ("RoundedCornerShape(", "AdaptiveDropdownMenu(", "AdaptiveDropdownMenuItem("):
        if wrong in code:
            errors.append(f"{file}: obsolete secondary-filter control {wrong}")
    if "FlatWorkspaceDropdownMenu(" not in code or "WorkspaceFlatMenuRow(" not in code:
        errors.append(f"{file}: secondary options must be common flat rows")
for file in ("MainControls.kt", "SpaceBackupDialogs.kt", "SystemProbeDialog.kt"):
    code = (root / file).read_text(encoding="utf-8")
    if "AdaptiveDropdownMenu(" in code or "AdaptiveDropdownMenuItem(" in code:
        errors.append(f"{file}: old dropdown content still active")
if "BulkLockContextMenu(" in (root / "AppListRows.kt").read_text(encoding="utf-8"):
    errors.append("AppListRows.kt: obsolete separate long-press lock popup returned")
if "FlatRowSurface(dividerInset = 0.dp)" not in (root / "CustomOpenTypeDialog.kt").read_text(encoding="utf-8"):
    errors.append("CustomOpenTypeDialog.kt: custom OPEN rows not flat")
if "FlatRowSurface(dividerInset = 0.dp)" not in (root / "BrowserHostFilterRow.kt").read_text(encoding="utf-8"):
    errors.append("BrowserHostFilterRow.kt: managed Browser rows not flat")
if "shadowElevation = 2.dp" not in (root / "PriorityDialog.kt").read_text(encoding="utf-8"):
    errors.append("PriorityDialog.kt: reduced drag feedback must remain distinct")

# Ordering must reuse Cleanup's real components instead of locally duplicated layouts.
priority = (root / "PriorityDialog.kt").read_text(encoding="utf-8")
shared_summary = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
for expected in ("WorkspaceListSummary(", "secondaryMaxLines = 2",
                 "WorkspaceHelpSection {", "previewActions = listOf(",
                 "entryKind = kind", "WorkspaceAppSelectionCheckbox(",
                 "WorkspaceDetailActionsMenu(", "onPreview = {",
                 "focusPackage = item.rule.packageName",
                 "focusClass = item.rule.className"):
    if expected not in priority:
        errors.append(f"PriorityDialog.kt: cleanup component reuse missing {expected}")
for forbidden in ("WorkspaceListActions(", "WorkspaceDetailIconAction(", "AdaptiveCheckbox("):
    if forbidden in priority:
        errors.append(f"PriorityDialog.kt: duplicated old UI control {forbidden}")
if "secondaryMaxLines: Int = 1" not in shared_summary or "maxLines = secondaryMaxLines" not in shared_summary:
    errors.append("WorkspaceCommon.kt: shared summary must preserve two-line Ordering help")
for expected in ("detectDragGesturesAfterLongPress(", "moveVisiblePriorityTo(",
                 "moveVisiblePriority(", "PendingChangesBar(",
                 "SystemSelectionRisk.PRIORITY"):
    if expected not in priority:
        errors.append(f"PriorityDialog.kt: ordering-only workflow lost {expected}")

# Bounded popup widths: ordinary menus cannot expand to the whole screen.
popup_width = (root / "AdaptivePopups.kt").read_text(encoding="utf-8")
for term in ("menuWidth: Dp = 224.dp", "Modifier.width(actualMenuWidth)",
             "configuration.screenWidthDp - 16", "menuWidth = menuWidth"):
    if term not in popup_width:
        errors.append(f"AdaptivePopups.kt: explicit width contract missing {term}")
if ".widthIn(min = 196.dp)" in popup_width:
    errors.append("AdaptivePopups.kt: minimum-only popup width returned")
# Test shared width bounding and call routing; never freeze design widths.
for filename in ("WorkspaceCommon.kt", "WorkspaceCategorySelector.kt",
                 "WorkspaceListParts.kt", "OpenPresetFilterRow.kt",
                 "BrowserHostFilterRow.kt"):
    if "FlatWorkspaceDropdownMenu(" not in (root / filename).read_text(encoding="utf-8"):
        errors.append(f"{filename}: common popup entry point absent")
# Unified widgets are validated by geometry and semantics, not old names.
button_src = (root / "AdaptiveWidgets.kt").read_text(encoding="utf-8")
for name in ("AdaptiveTextButton", "AdaptiveOutlinedButton", "AdaptiveButton"):
    body = button_src.split(f"internal fun {name}(", 1)[-1].split("\n@Composable", 1)[0]
    if "shape = RectangleShape" not in body:
        errors.append(f"AdaptiveWidgets.kt: {name} still has a rounded default")
modal_src = (root / "AdaptivePopups.kt").read_text(encoding="utf-8")
dialog = modal_src.split("internal fun AdaptiveAlertDialog(", 1)[-1].split(
    "Common anchored Popup engine.", 1
)[0]
for term in ("Dialog(onDismissRequest", "shape = RectangleShape",
             "FlatPopupFooter {", "shadowElevation = 0.dp"):
    if term not in dialog:
        errors.append(f"AdaptivePopups.kt: flat dialog contract missing {term}")
menu_row = modal_src.split("internal fun AdaptiveDropdownMenuItem(", 1)[-1].split(
    "One menu entry point", 1
)[0]
for term in ("Surface(", "heightIn(min = 48.dp)",
             "Alignment.CenterVertically", "enabled = enabled"):
    if term not in menu_row:
        errors.append(f"AdaptivePopups.kt: flat menu item contract missing {term}")
for obsolete in ("    DropdownMenuItem(", "    AlertDialog("):
    if obsolete in modal_src:
        errors.append(f"AdaptivePopups.kt: old material component {obsolete}")
for filename in ("WorkspaceCategorySelector.kt", "SettingsWidgets.kt"):
    if "FlatSelectionRail(selected)" not in (root / filename).read_text(encoding="utf-8"):
        errors.append(f"{filename}: not using shared selected rail")
rail = (root / "FlatListDesign.kt").read_text(encoding="utf-8")
if "fun FlatSelectionRail(" not in rail or "Modifier.width(3.dp).height(height)" not in rail:
    errors.append("FlatListDesign.kt: shared accent rail missing")
details = (root / "WorkspaceListParts.kt").read_text(encoding="utf-8")
if "UiListStyle.GROUPED" in details or "UiPreferencesRuntime.current.listStyle" in details or "WorkspaceDetailIconAction(" in details:
    errors.append("WorkspaceListParts.kt: dormant Grouped rendering branch still active")
geometry = (root / "MaterialDesignSystem.kt").read_text(encoding="utf-8")
for obsolete in ("val cardShape get()", "val listCardRadius get()",
                 "val groupedInnerRadius get()", "val categoryHeight get()"):
    if obsolete in geometry:
        errors.append(f"MaterialDesignSystem.kt: old unused geometry {obsolete}")
colors = (root / "MaterialActionColors.kt").read_text(encoding="utf-8")
for obsolete in ("fun segmented()", "fun filter()", "FilterChipDefaults", "SegmentedButtonDefaults"):
    if obsolete in colors:
        errors.append(f"MaterialActionColors.kt: legacy palette {obsolete}")
action_body = (root / "SettingsWidgets.kt").read_text(encoding="utf-8").split(
    "internal fun SettingsActionButton(", 1
)[-1].split("internal fun SettingsFlatDialog(", 1)[0]
if "vertical = 8.dp" in action_body:
    errors.append("SettingsWidgets.kt: extra vertical spacing returned to settings actions")

# Single renderer and semantic color contract for all sliders and switches.
control_widgets = (root / "AdaptiveWidgets.kt").read_text(encoding="utf-8")
control_palette = (root / "MaterialActionColors.kt").read_text(encoding="utf-8")
settings_controls = (root / "SettingsWidgets.kt").read_text(encoding="utf-8")
appearance_controls = (root / "AppearanceSettings.kt").read_text(encoding="utf-8")
for token in ("fun AdaptiveSlider(", "SliderDefaults.Track(", "trackInsideCornerSize = 0.dp",
              "thumbTrackGapSize = 0.dp", "Modifier.width(10.dp).height(24.dp)",
              "onValueChangeFinished = onValueChangeFinished", "colors = MaterialActionColors.slider()"):
    if token not in control_widgets:
        errors.append(f"AdaptiveWidgets.kt: shared flat Slider contract missing {token}")
for token in ("colors = MaterialActionColors.switch()", "onCheckedChange = onCheckedChange"):
    if token not in control_widgets:
        errors.append(f"AdaptiveWidgets.kt: native Switch handling missing {token}")
for token in ("fun slider() = SliderDefaults.colors(", "fun switch() = SwitchDefaults.colors("):
    if token not in control_palette:
        errors.append(f"MaterialActionColors.kt: common slider/switch palette missing {token}")
if "fun FlatSliderField(" not in settings_controls or "AdaptiveSlider(" not in settings_controls:
    errors.append("SettingsWidgets.kt: flat slider label/value field not centralized")
if "FlatSliderField(" not in appearance_controls:
    errors.append("AppearanceSettings.kt: RGB channels must share Settings slider renderer")
if re.search(r"(?<![A-Za-z0-9_])Slider\s*\(", appearance_controls) or re.search(
    r"(?<![A-Za-z0-9_])Slider\s*\(", settings_controls
):
    errors.append("Settings: direct native Slider bypasses flat shared renderer")
if "onCheckedChange = null" not in settings_controls.split("internal fun SettingsSwitchRow(", 1)[-1].split(
        "internal enum class SettingsActionStyle", 1)[0]:
    errors.append("SettingsWidgets.kt: switch and parent row cannot both handle toggle")
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
                                 "ContinuousDetailSurface(", "MaterialFlatPalette.surface()",
                                 "MaterialFlatPalette.selectionHighlightSurface()"),
    "WorkspaceAppRow.kt": ("ContinuousApplicationSurface(expanded = expanded) { appContent() }",
                            "MaterialAppRowBody(", "heightIn(min = MaterialUiTokens.listRowHeight)"),
    "WorkspaceCommon.kt": ("WorkspaceCategoryRow(", "FlatCategoryRow(",
                            "WorkspaceDetailContainer(position, selected = false)",
                            "WorkspaceDetailContainer(position, checked, contentBlock)",
                            "trailing(menuExpanded) { menuExpanded = it }"),
    "SettingsWidgets.kt": ("FlatSectionHeader(", "MaterialSectionCard { content() }",
                           "FlatRowSurface(dividerInset = 0.dp)", "FlatRowSurface("),
    "SettingsCenterHome.kt": ("FlatCategoryRow(", "onSelect(section)",
                              "MaterialPageColumn(fullBleed = true)"),
    "AdaptiveWidgets.kt": ("fun FlatUnderlineTab(", "fun AdaptiveFilterChip("),
    "AppScopePicker.kt": ("FlatRowSurface(Modifier.fillMaxWidth())",),
    "WorkspaceCategorySelector.kt": ("filter()", "Box(Modifier.weight(1f)) { category() }",
                                      "WorkspaceFlatMenuRow(", "shape = RectangleShape"),
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

# Eye preview is the independent leading Category control, never next to Help.
# Individual Entry/Root items retain their precise preview in overflow.
selector_source = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
for needle in ("fun WorkspaceCategoryPreviewIcon(", "Icons.Rounded.Visibility",
               "if (previewActions.isNotEmpty())", "previewActions = previewActions"):
    if needle not in selector_source:
        errors.append(f"WorkspaceCategorySelector.kt: category eye missing: {needle}")
picker_source = selector_source.split("private fun WorkspaceCategoryPicker(", 1)[-1].split(
    "/** Shared, cardless menu item", 1
)[0]
if picker_source.count("WorkspaceCategoryPreviewIcon(previewActions)") != 1:
    errors.append("WorkspaceCategorySelector.kt: subtype picker must swap the existing category icon")
picker_button = selector_source.split("internal fun WorkspacePickerButton(", 1)[-1].split(
    "private fun WorkspaceCategoryPreviewIcon(", 1
)[0]
if "WorkspaceCategoryPreviewIcon(previewActions)" not in picker_button:
    errors.append("WorkspaceCategorySelector.kt: standard picker must replace the existing icon")
if "showLeadingIcon" in selector_source:
    errors.append("WorkspaceCategorySelector.kt: obsolete separate-eye geometry remains")
eye_source = selector_source.split("private fun WorkspaceCategoryPreviewIcon(", 1)[-1].split(
    "/**", 1
)[0]
if "Modifier.size(18.dp).clickable(" not in eye_source or "AdaptiveIconButton(" in eye_source:
    errors.append("WorkspaceCategorySelector.kt: eye must fit original icon slot; no added button")
summary_source = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8").split(
    "internal fun WorkspaceListActions(", 1
)[-1].split("internal fun WorkspaceListSummary(", 1)[0]
if "Icons.Rounded.Visibility" in summary_source or "previewActions" in summary_source:
    errors.append("WorkspaceCommon.kt: duplicate preview icon beside Help")
for filename, target in (("MainTabs.kt", "entryKind = previewKind"),
                         ("RootComponentsScreen.kt", "componentKind = previewKind")):
    code = (root / filename).read_text(encoding="utf-8")
    if "previewActions =" not in code or target not in code:
        errors.append(f"{filename}: header category eye not wired to preview")
if "onPreview = onPreview" not in (root / "AppListRows.kt").read_text(encoding="utf-8"):
    errors.append("AppListRows.kt: item-specific preview must remain in overflow")

# Dropdown menus and bottom navigation must stay visually flat.
popup_source = (root / "AdaptivePopups.kt").read_text(encoding="utf-8")
for token in ("fun FlatWorkspaceDropdownMenu(", "shape = RectangleShape",
              "shadowElevation = 0.dp", "tonalElevation = 0.dp"):
    if token not in popup_source:
        errors.append(f"AdaptivePopups.kt: flat popup contract missing: {token}")
menu_choice_source = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
if "MaterialFlatPalette.selectedSurface()" in menu_choice_source.split(
        "internal fun WorkspaceFlatMenuRow(", 1)[-1].split(
        "internal fun EntryCategoryPicker(", 1)[0]:
    errors.append("WorkspaceCategorySelector.kt: rounded selection cards returned to menu")
nav_source = (root / "MaterialActionColors.kt").read_text(encoding="utf-8")
nav_palette = nav_source.split("fun navigation()", 1)[-1].split("}", 1)[0]
if "indicatorColor = Color.Transparent" not in nav_palette:
    errors.append("MaterialActionColors.kt: old pill indicator still active")
nav_activity = (root / "MainActivity.kt").read_text(encoding="utf-8")
# Compact flat navigation: selected text/icon use the theme accent, not an
# underline or capsule. System navigation padding remains outside the 64dp bar.
for symbol in ("NavigationBarItem(", "colors = MaterialActionColors.navigation()",
               "modifier = Modifier.height(64.dp)", "windowInsets = WindowInsets(0, 0, 0, 0)",
               ".navigationBarsPadding()", "tonalElevation = 0.dp"):
    if symbol not in nav_activity:
        errors.append(f"MainActivity.kt: compact bottom navigation missing {symbol}")
if "Modifier.drawWithContent" in nav_activity or "val underlineColor =" in nav_activity:
    errors.append("MainActivity.kt: bottom navigation must use selected color, not underline")
if 'UiColorRoles.resolve("navigationText", MaterialTheme.colorScheme.primary)' not in nav_source:
    errors.append("MaterialActionColors.kt: selected navigation color must follow theme accent")


# Edge-to-edge immersive system bars must respect app-enforced day/night modes,
# keep navigation contrast clear even with three-button navigation, and consume
# safe drawing insets exactly once (including the nested Settings toolbar).
bars = (root / "MainActivity.kt").read_text(encoding="utf-8")
for symbol in ("SystemBarStyle.dark(transparent)", "SystemBarStyle.light(transparent, transparent)",
               "enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)",
               "SideEffect { syncSystemBars(dark) }",
               "window.isNavigationBarContrastEnforced = false",
               "window.isStatusBarContrastEnforced = false",
               "window.statusBarColor = android.graphics.Color.TRANSPARENT",
               "window.navigationBarColor = android.graphics.Color.TRANSPARENT",
               "isAppearanceLightStatusBars = !dark",
               "isAppearanceLightNavigationBars = !dark",
               "color = MaterialTheme.colorScheme.surface",
               "contentWindowInsets = WindowInsets.safeDrawing",
               "consumeWindowInsets(padding)"):
    if symbol not in bars:
        errors.append(f"MainActivity.kt: day/night immersive system bars missing {symbol}")
theme_file = root.parents[4] / "res/values/themes.xml"
# root is app/src/main/java/.../ui, hence use app/src/main/res directly.
theme_file = Path(__file__).resolve().parents[1] / "app/src/main/res/values/themes.xml"
theme_text = theme_file.read_text(encoding="utf-8")
for symbol in ("android:windowLightNavigationBar", "android:statusBarColor",
               "android:navigationBarColor", "android:enforceStatusBarContrast",
               "android:enforceNavigationBarContrast"):
    if symbol not in theme_text:
        errors.append(f"themes.xml: immersive system bar theme flag missing {symbol}")

# The toolbar's multi-space picker must advertise its expand/collapse state.
space_toolbar = (root / "MainControls.kt").read_text(encoding="utf-8")
for symbol in ("if (spaces.size > 1)", "if (spaceMenu) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore",
               "stringResource(R.string.space_switch)", "FlatWorkspaceDropdownMenu(expanded = spaceMenu"):
    if symbol not in space_toolbar:
        errors.append(f"MainControls.kt: multi-space expand indicator missing {symbol}")
about_content = (root / "SettingsAbout.kt").read_text(encoding="utf-8")
if 'stringResource(R.string.lc_about_telegram)' not in about_content or 'https://t.me/LISTCLEANER' not in about_content:
    errors.append("SettingsAbout.kt: Telegram project channel missing")

# App and expanded-detail overflow menus must share the same anchored flat renderer.
app_actions = (root / "WorkspaceAppRow.kt").read_text(encoding="utf-8")
detail_actions = (root / "WorkspaceListParts.kt").read_text(encoding="utf-8")
menu_choices = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
detail_body = (root / "UiCard.kt").read_text(encoding="utf-8")
visual_tokens = (root / "MaterialDesignSystem.kt").read_text(encoding="utf-8")
if "WorkspaceFlatOverflowMenu(" not in app_actions or "WorkspaceFlatOverflowMenu(" not in detail_actions:
    errors.append("Workspace: app and detail must reuse the same anchored overflow menu")
if "BulkLockContextMenu(" in app_actions:
    errors.append("WorkspaceAppRow.kt: app dropdown must not be separate from the overflow icon")
if "Icons.Rounded.MoreVert" not in detail_actions.split("internal fun WorkspaceFlatOverflowMenu(", 1)[-1].split(
        "/**", 1)[0]:
    errors.append("WorkspaceListParts.kt: shared three-dot glyph is missing")
for expected in ("FlatWorkspaceDropdownMenu(", "WorkspaceFlatMenuRow(",
                 "enabled = action.enabled", "onExpandedChange(false)"):
    if expected not in detail_actions.split("internal fun WorkspaceFlatOverflowMenu(", 1)[-1].split(
            "/**", 1)[0]:
        errors.append(f"WorkspaceListParts.kt: unified flat menu missing {expected}")
if "enabled: Boolean = true" not in menu_choices.split("internal fun WorkspaceFlatMenuRow(", 1)[-1].split(
        "internal fun WorkspaceMenuChoice(", 1)[0]:
    errors.append("WorkspaceCategorySelector.kt: disabled app lock options must remain supported")
if "onLongClick = { menuExpanded = true }" not in app_actions:
    errors.append("WorkspaceAppRow.kt: long-press app menu must open its three-dot menu")
detail_row_long_press = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8").split(
    "internal fun WorkspaceDetailRow(", 1
)[-1]
detail_overflow = detail_actions.split("internal fun WorkspaceDetailActionsMenu(", 1)[-1]
if "onLongClick = { menuExpanded = true }" not in detail_row_long_press:
    errors.append("WorkspaceCommon.kt: component long-press must open shared overflow")
if "trailing(menuExpanded) { menuExpanded = it }" not in detail_row_long_press:
    errors.append("WorkspaceCommon.kt: component long-press and overflow must share state")
if "BulkLockContextMenu(" in detail_row_long_press:
    errors.append("WorkspaceCommon.kt: old separate long-press menu remains")
for symbol in ("expanded: Boolean", "onExpandedChange: (Boolean) -> Unit",
               "expanded = expanded,", "onExpandedChange = onExpandedChange,"):
    if symbol not in detail_overflow:
        errors.append(f"WorkspaceListParts.kt: controlled component overflow missing {symbol}")
for filename in ("AppListRows.kt", "RootComponentsScreen.kt"):
    row = (root / filename).read_text(encoding="utf-8")
    if "trailing = { menuExpanded, onMenuExpandedChange ->" not in row or "expanded = menuExpanded" not in row:
        errors.append(f"{filename}: component detail menu not bound to long-press state")
order = (root / "PriorityDialog.kt").read_text(encoding="utf-8")
if "onExpandedChange = { menuExpanded = it }" not in order:
    errors.append("PriorityDialog.kt: read-only overflow lacks local state")

if "size(MaterialUiTokens.appExpandIndicatorWidth)" not in app_actions:
    errors.append("WorkspaceAppRow.kt: app expand arrow must use shared alignment token")
if "end = MaterialUiTokens.pageHorizontal + MaterialUiTokens.appExpandIndicatorWidth" not in detail_body:
    errors.append("UiCard.kt: detail overflow column not aligned with app overflow")
if "val appExpandIndicatorWidth = 18.dp" not in visual_tokens:
    errors.append("MaterialDesignSystem.kt: missing shared overflow alignment width")

# Application headers use a single borderless three-state mark, never the
# Material square checkbox. Native 48dp hit area and tri-state semantics survive.
app_select = (root / "WorkspaceListParts.kt").read_text(encoding="utf-8").split(
    "internal fun WorkspaceAppSelectionCheckbox(", 1
)[-1].split("internal fun WorkspaceAppSummaryText(", 1)[0]
for token in ("triStateToggleable(", "state = state", "enabled = enabled",
              "role = Role.Checkbox", "onClick = onClick",
              "MaterialUiTokens.iconTouchTarget",
              "ToggleableState.Off -> Icons.Rounded.Add",
              "ToggleableState.Indeterminate -> Icons.Rounded.Remove",
              "ToggleableState.On -> Icons.Rounded.Check",
              "MaterialTheme.colorScheme.primary",
              "MaterialTheme.colorScheme.onSurfaceVariant"):
    if token not in app_select:
        errors.append(f"WorkspaceListParts.kt: borderless three-state app selection missing {token}")
if "AdaptiveTriStateCheckbox(" in app_select or "TriStateCheckbox(" in app_select:
    errors.append("WorkspaceListParts.kt: old square app checkbox returned")
# Application-row content owns the global left inset; the glyph itself is
# centered in the full-size touch target with no artificial visual offset.
if "modifier = Modifier.size(18.dp)," not in app_select or ".offset(x =" in app_select:
    errors.append("WorkspaceListParts.kt: tri-state glyph must remain centered")
app_row_geometry = (root / "WorkspaceAppRow.kt").read_text(encoding="utf-8")
if "selectionToIconGap = 0.dp" not in app_row_geometry or "Spacer(Modifier.width(selectionToIconGap))" not in app_row_geometry:
    errors.append("WorkspaceAppRow.kt: shared application selection gap not reduced")
if "selectionToIconGap: Dp = MaterialUiTokens.appLabelInset" not in app_row_geometry:
    errors.append("WorkspaceAppRow.kt: non-workspace selection gap default not preserved")
if "contentStartInset = 0.dp" not in app_row_geometry or "contentStartInset: Dp = MaterialUiTokens.pageHorizontal" not in app_row_geometry:
    errors.append("WorkspaceAppRow.kt: app content must move left without affecting other screens")
if "start = contentStartInset" not in app_row_geometry or "end = MaterialUiTokens.pageHorizontal" not in app_row_geometry:
    errors.append("WorkspaceAppRow.kt: app content asymmetric layout contract missing")

# App expansion and component selection must reuse the category-menu highlight.
details = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
detail_row = details.split("internal fun WorkspaceDetailRow(", 1)[-1]
for expected in ("toggleableState = if (checked)", "trailing(menuExpanded) { menuExpanded = it }",
                 "leadingInset = 0.dp", "WorkspaceDetailContainer(position, checked, contentBlock)"):
    if expected not in detail_row:
        errors.append(f"WorkspaceCommon.kt: component highlight/selection contract missing {expected}")
if "WorkspaceDetailSelectionCheck(" in details or "trailingAccessoryWidth = 20.dp" in detail_row:
    errors.append("WorkspaceCommon.kt: obsolete selected check or its trailing gap returned")
if "AdaptiveCheckbox(" in detail_row:
    errors.append("WorkspaceCommon.kt: square component checkbox returned")
detail_layout = (root / "UiCard.kt").read_text(encoding="utf-8")
if "end = MaterialUiTokens.pageHorizontal + MaterialUiTokens.appExpandIndicatorWidth -" not in detail_layout:
    errors.append("UiCard.kt: expanded detail check must share the overflow alignment allowance")
if "Spacer(Modifier.width(15.dp))" in detail_row:
    errors.append("WorkspaceCommon.kt: obsolete leading rail space remains")
if "leadingInset: Dp = MaterialUiTokens.pageHorizontal" not in detail_layout or "start = leadingInset," not in detail_layout:
    errors.append("UiCard.kt: read-only inset and selectable lock-slot alignment missing")
if "leadingInset = 0.dp" not in detail_row:
    errors.append("WorkspaceCommon.kt: component lock-slot must start at app-row left edge")
if "Spacer(Modifier.width(MaterialUiTokens.iconTouchTarget))" not in detail_row:
    errors.append("WorkspaceCommon.kt: component text must retain 48dp alignment with app icon")
if "trailingAccessoryWidth = MaterialUiTokens.appExpandIndicatorWidth" not in detail_row:
    errors.append("WorkspaceCommon.kt: lock must use app expand-arrow space without shifting overflow")
if "Modifier.size(MaterialUiTokens.appExpandIndicatorWidth)" not in detail_row:
    errors.append("WorkspaceCommon.kt: fixed-width lock indicator slot missing")
if detail_row.count("Icons.Rounded.Lock") != 1:
    errors.append("WorkspaceCommon.kt: exactly one lock icon required")
if not (detail_row.index("Column(Modifier.weight(1f)") <
        detail_row.index("trailing(menuExpanded)") <
        detail_row.index("Icons.Rounded.Lock")):
    errors.append("WorkspaceCommon.kt: lock must appear after the component three-dot menu")
if detail_row.index("if (locked)") < detail_row.index("trailing(menuExpanded)"):
    errors.append("WorkspaceCommon.kt: obsolete leading lock icon remains")
if "ContinuousDetailSurface(selected = selected, content = content)" not in details:
    errors.append("WorkspaceCommon.kt: selected detail background not forwarded to shared surface")
grouped = (root / "WorkspaceGroupedRows.kt").read_text(encoding="utf-8")
if "if (selected) MaterialFlatPalette.selectionHighlightSurface()" not in grouped:
    errors.append("WorkspaceGroupedRows.kt: selected detail background missing")
if "else MaterialFlatPalette.surface()" not in grouped:
    errors.append("WorkspaceGroupedRows.kt: unselected component/app rows must use plain surface")
if "if (expanded) MaterialFlatPalette.selectionHighlightSurface()" not in grouped:
    errors.append("WorkspaceGroupedRows.kt: expanded app background missing")
if "dividerInset = MaterialUiTokens.pageHorizontal," not in grouped.split("internal fun ContinuousDetailSurface(", 1)[-1]:
    errors.append("WorkspaceGroupedRows.kt: component dividers must align with application rows")
palette = (root / "MaterialActionColors.kt").read_text(encoding="utf-8")
if "fun selectionHighlightSurface()" not in palette or "MaterialTheme.colorScheme.primary.copy(" not in palette:
    errors.append("MaterialActionColors.kt: shared category/row theme highlight missing")
menu_color = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
if "if (sectionExpanded || selected)" not in menu_color or "MaterialFlatPalette.selectionHighlightSurface()" not in menu_color:
    errors.append("WorkspaceCategorySelector.kt: category selected highlight must share the list color")
for symbol in ("WorkspaceReadOnlyDetailRow(", "WorkspaceDetailRow(",
               "ContinuousDetailSurface(selected = selected, content = content)"):
    if symbol not in details:
        errors.append(f"WorkspaceCommon.kt: expanded detail surface contract missing {symbol}")

# Preview tabs, result list and choice dialogs share Material3 list affordances.
if "AdaptiveFilterChip(" not in (root / "SystemProbeDialog.kt").read_text(encoding="utf-8"):
    errors.append("SystemProbeDialog.kt: preview filter tabs lack visible selected state")
if "SettingsFlatDialog(" not in (root / "AppearanceSettings.kt").read_text(encoding="utf-8"):
    errors.append("AppearanceSettings.kt: custom color editor bypassed shared flat dialog")

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
# OPEN formats such as PDF/Word/Excel are metadata, never a separate chip/card.
open_indicator = (root / "AppListRows.kt").read_text(encoding="utf-8").split(
    "private fun OpenWithTypeIndicator(", 1
)[-1].split("@OptIn(ExperimentalFoundationApi::class)", 1)[0]
for forbidden in ("Surface(", "BorderStroke(", "RoundedCornerShape(", "MaterialFlatPalette.sectionSurface()"):
    if forbidden in open_indicator:
        errors.append(f"AppListRows.kt: OPEN type label must be flat, found {forbidden}")
for expected in ("AdaptiveText(", "MaterialTheme.typography.labelSmall",
                 "MaterialTheme.colorScheme.onSurfaceVariant",
                 "maxLines = if (expanded) Int.MAX_VALUE else 1"):
    if expected not in open_indicator:
        errors.append(f"AppListRows.kt: OPEN format metadata contract missing: {expected}")

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
for filename in ("WorkspaceAppRow.kt", "WorkspaceCommon.kt", "RootComponentsScreen.kt", "WorkspaceListParts.kt"):
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

# All Settings pages use one flat visual contract.
settings_sources = {
    name: (root / name).read_text(encoding="utf-8")
    for name in ("SettingsWidgets.kt", "SettingsNavigation.kt", "AppearanceSettings.kt",
                 "ListSettings.kt", "ScanSettings.kt", "SettingsSections.kt",
                 "SettingsReset.kt", "RuntimePanel.kt")
}
for token in ("FlatUnderlineTab(", "fun SettingsFlatDialog(",
              "fun SettingsFlatPaletteTabs(", "FlatRowSurface(",
              "fun SettingsFlatChoiceRow("):
    if token not in settings_sources["SettingsWidgets.kt"]:
        errors.append(f"SettingsWidgets.kt: flat control contract missing: {token}")
for forbidden in ("AdaptiveOutlinedButton(", "AdaptiveButton(",
                  "SingleChoiceSegmentedButtonRow", "SegmentedButton("):
    for name in ("SettingsWidgets.kt", "AppearanceSettings.kt"):
        if forbidden in settings_sources[name]:
            errors.append(f"{name}: raised/capsule settings control: {forbidden}")
for name in ("AppearanceSettings.kt", "SettingsReset.kt", "RuntimePanel.kt"):
    if "AdaptiveAlertDialog(" in settings_sources[name]:
        errors.append(f"{name}: rounded settings dialog still active")
if "shape = MaterialTheme.shapes." in settings_sources["AppearanceSettings.kt"]:
    errors.append("AppearanceSettings.kt: palette swatch still rounded")
if "PaddingValues(vertical = MaterialUiTokens.pageVertical)" not in settings_sources["SettingsNavigation.kt"]:
    errors.append("SettingsNavigation.kt: settings pages must use full-width rows")
if "FlatRowSurface(" not in (root / "MainControls.kt").read_text(encoding="utf-8"):
    errors.append("MainControls.kt: module status must be flat")

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
if "Arrangement.spacedBy(4.dp)" not in (root / "AdaptiveWidgets.kt").read_text(encoding="utf-8"):
    errors.append("AdaptiveWidgets.kt: shared flat tab spacing missing")
compact = (root / "CompactCategoryButton.kt").read_text(encoding="utf-8")
if "AdaptiveFilterChip(" not in compact:
    errors.append("CompactCategoryButton.kt: compact picker must use shared tab")

# Categories use an anchored, scrollable Material 3 dropdown, not a distant bottom sheet.
category = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
for expected in ("FlatWorkspaceDropdownMenu(", "Surface(", "EntryCategoryPicker(",
                 "ComponentCategoryPicker(", "expandedGroup", "group.kinds()",
                 "availableKinds", "onSelected(null)", "menuWidth = 280.dp",
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
for expected in ("FlatWorkspaceDropdownMenu(", "onDismissRequest = { expanded = false }",
                 "draftAppType", "draftView", "lc_filter_sheet_apply",
                 "lc_filter_sheet_reset", "WorkspacePickerButton(", "extraFilter()",
                 "menuWidth = 264.dp", "WorkspaceFilterChoiceGroup(",
                 "AppTypeFilter.entries", "UiFilter.entries",
                 "header = { FlatPopupHeader(", "footer = {", "FlatPopupFooter {"):
    if expected not in filter_popup:
        errors.append(f"WorkspaceCommon.kt: shared filter popup regression: {expected}")
if "ModalBottomSheet(" in filter_popup or "LazyRow(" in filter_popup:
    errors.append("WorkspaceCommon.kt: filter must stay anchored beside Category")
if ".verticalScroll(rememberScrollState())" in filter_popup:
    errors.append("WorkspaceCommon.kt: nested filter scroll returned; popup engine owns viewport")
if "// Fixed footer:" in filter_popup:
    errors.append("WorkspaceCommon.kt: old hand-measured footer returned")
footer_start = filter_popup.index("footer = {") if "footer = {" in filter_popup else -1
body_start = filter_popup.find("// Options alone scroll")
if footer_start < 0 or body_start <= footer_start:
    errors.append("WorkspaceCommon.kt: footer must be a separate common popup slot")
else:
    footer = filter_popup[footer_start:body_start]
    if footer.count("WorkspaceFilterFooterAction(") != 2:
        errors.append("WorkspaceCommon.kt: Reset and Apply not in common footer slot")
    if "heightIn(min = 48.dp)" in footer or "AdaptiveTextButton(" in footer:
        errors.append("WorkspaceCommon.kt: footer has redundant button height")
flat_footer = popups.split("internal fun FlatPopupFooter(", 1)[-1].split(
    "internal fun AdaptiveDropdownMenu(", 1
)[0]
if "heightIn(min = 48.dp)" not in flat_footer or "verticalAlignment = Alignment.CenterVertically" not in flat_footer:
    errors.append("AdaptivePopups.kt: common footer lacks vertically centered 48dp row")
footer_action = sheet.split("private fun WorkspaceFilterFooterAction(", 1)[-1].split(
    "/** Filter selections are single-column", 1
)[0]
for expected in ("heightIn(min = 48.dp)", "clickable(role = Role.Button",
                 "contentAlignment = Alignment.Center"):
    if expected not in footer_action:
        errors.append(f"WorkspaceCommon.kt: footer touch target missing {expected}")
if "heightIn(max = 480.dp)" in filter_popup or "widthIn(min = 270.dp" in filter_popup:
    errors.append("WorkspaceCommon.kt: filter must keep category-scale popup width")
# Category and Filter use one flat row style inside anchored menus.
menu_shared = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
if "fun WorkspacePickerButton(" not in menu_shared:
    errors.append("WorkspaceCategorySelector.kt: shared category/filter trigger missing")
if "fun <T> WorkspaceFilterChoiceGroup(" not in sheet or "WorkspaceFlatMenuRow(" not in sheet:
    errors.append("WorkspaceCommon.kt: shared flat filter rows missing")
for expected in ("fun WorkspaceListActions(",
                 "WorkspaceListActions(if (showSelectionActions) actions else emptyList(), helpExpanded, onHelpToggle)"):
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

# Bulk selection has moved into the pinned toolbar after Filter.
# Verify all three workspaces provide actions there and suppress the old summary trigger.
for filename in ("MainTabs.kt", "RootComponentsScreen.kt", "PriorityDialog.kt"):
    code = (root / filename).read_text(encoding="utf-8")
    if "val selectionActions = listOf(" not in code:
        errors.append(f"{filename}: bulk actions definition missing")
    if "showSelectionActions = false" not in code:
        errors.append(f"{filename}: old list-summary bulk trigger still enabled")
    if filename == "RootComponentsScreen.kt":
        if "afterFilter = { WorkspaceListActions(actions = selectionActions) }" not in code:
            errors.append(f"{filename}: selection actions must follow Filter")
    elif "selectionActions = selectionActions" not in code:
        errors.append(f"{filename}: selection actions must be passed to pinned toolbar")
picker = (root / "WorkspaceCategorySelector.kt").read_text(encoding="utf-8")
if not re.search(r"filter\(\)\s+afterFilter\?\.invoke\(\)", picker):
    errors.append("WorkspaceCategorySelector.kt: selection control must follow Filter")
toolbar = (root / "WorkspaceCommon.kt").read_text(encoding="utf-8")
for expected in ("appTypeFilter != AppTypeFilter.ALL", "viewFilter != UiFilter.ALL",
                 "lc_filter_help_short"):
    if expected not in toolbar:
        errors.append(f"WorkspaceCommon.kt: filter highlight or text regression: {expected}")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Material 3 List First rows, navigation, details and settings OK")
