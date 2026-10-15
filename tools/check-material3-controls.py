#!/usr/bin/env python3
"""Guard the single Material 3 navigation/button contract against divergent widgets."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1] / "app/src/main/java/com/yagay/ListCleaner/ui"
direct_button = re.compile(
    r"(?<![A-Za-z0-9_])(?:Button|OutlinedButton|TextButton|IconButton|FilterChip)\s*\("
)
errors = []
for file in sorted(root.glob("*.kt")):
    source = file.read_text(encoding="utf-8")
    if "top.yukonga.miuix" in source or "UiThemeStyle" in source:
        errors.append(f"{file.name}: legacy dual-theme code is not allowed")
    if file.name == "AdaptiveWidgets.kt":
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
    "AdaptiveWidgets.kt": ["MaterialUiTokens.buttonHeight", "UiCard(",
                           "MaterialActionColors.filled()", "MaterialActionColors.outlined()",
                           "MaterialActionColors.text()", "MaterialActionColors.icon()",
                           "MaterialActionColors.filter()"],
    "MaterialActionColors.kt": ["fun filled()", "fun outlined()", "fun outlinedBorder(",
                                "fun text()", "fun icon()", "fun filter()", "fun navigation()",
                                "selectedIconColor = MaterialTheme.colorScheme.onPrimary",
                                "selectedTextColor = MaterialTheme.colorScheme.primary",
                                "indicatorColor = MaterialTheme.colorScheme.primary"],
    "UiCard.kt": ["enum class UiCardVariant", "UiCardVariant.SECTION", "UiCardVariant.CATEGORY",
                  "UiCardVariant.LIST_GROUP", "UiCardVariant.STATUS", "position.shape()",
                  "onClick = onClick", "UiCardDetailRow("],
    "MaterialDesignSystem.kt": ["UiCard(", "UiCardVariant.SECTION", "val cardShape get() ="],
    "WorkspaceLayout.kt": ["MaterialUiTokens.cardRadius", "MaterialUiTokens.groupedInnerRadius"],
    "MainControls.kt": ["UiCardVariant.STATUS", "warning = true"],
    "MainActivity.kt": ["NavigationBarItem(", "colors = MaterialActionColors.navigation()",
                        "Destination.SETTINGS -> SettingsTabContent(",
                        "state.destination != Destination.SETTINGS"],
    "RuntimePanel.kt": ["UiCardVariant.STATUS"],
    "WorkspaceAppRow.kt": ["MaterialAppRowBody(", "MaterialUiTokens.listRowHeight"],
    "AppScopePicker.kt": ["MaterialAppRowBody(", "MaterialFullScreenDialog("],
    "ScopeScreen.kt": ["MaterialFullScreenDialog(", "MaterialDialogActions("],
    "MainTabs.kt": ["SettingsTabContent(", "RuntimePanel(state, vm)",
                     "MaterialPageColumn {", "MaterialSectionCard {",
                     "MaterialSettingsRow(", "SettingsAppearanceControls()",
                     "SettingsListControls()", "SettingsScanControls(vm)",
                     "SettingsResetControls(onColorModeChange)"],
    "UiPreferences.kt": ["data class UiPreferences(", "UiPreferencesRuntime",
                         "UiPreferencesStore", "fun write(context:", "fun read(context:"],
    "SettingsPreferencesControls.kt": ["fun SettingsAppearanceControls()",
                                       "fun SettingsListControls()", "fun SettingsScanControls(",
                                       "fun SettingsResetControls(", "SettingsSlider(", "SettingsChoices("],
    "UiAppearance.kt": ["UiPreferencesRuntime.current.accent"],
    "RootComponentsController.kt": ["UiPreferencesRuntime.current.scanMode",
                                    "UiPreferencesRuntime.current.scanCacheMinutes"],
    "ComponentScanRefreshPolicy.kt": ["scanMode: UiScanMode", "freshnessMs: Long"],
    "CandidateGrouping.kt": ["sortMode: UiAppSort", "UiAppSort.SELECTED_FIRST", "UiAppSort.NAME",
                             "UiAppSort.PACKAGE"],
    "MainViewModel.kt": ["SETTINGS(Icons.Rounded.Settings)"],
    "TextResources.kt": ["Destination.SETTINGS -> R.string.nav_settings"],
    "EntryWorkspaceHome.kt": ["MaterialPageColumn {"],
    "RootWorkspaceHome.kt": ["MaterialPageColumn {"],
    "WorkspaceCommon.kt": ["MaterialInlineStatus(", "MaterialUiTokens."],
}
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
settings_source = (root / "MainTabs.kt").read_text(encoding="utf-8")
for symbol in ("RuntimePanel(state, vm)", "dashboard_sync_status", "dashboard_runtime_hits",
               "dashboard_module_status", "dashboard_diagnostics", "ScopeDialog(",
               "onCollectDiagnostics", "onInspectFile"):
    if symbol not in settings_source:
        errors.append(f"MainTabs.kt: Settings missing former Status feature: {symbol}")

for filename in ("AppScopePicker.kt", "ScopeScreen.kt"):
    code = (root / filename).read_text(encoding="utf-8")
    if re.search(r"\bScaffold\s*\(", code):
        errors.append(f"{filename}: use MaterialFullScreenDialog instead of a private Scaffold")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Material 3 shared navigation, selected-state palette, layouts, cards and app rows OK")
