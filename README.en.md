# List Cleaner

[简体中文](README.md) | **English**

Trim Android share, open-with, deep-link, file-picker, and other system entry lists so frequently used apps appear first. With Root access, List Cleaner can also manage app-provided launcher icons, Quick Settings tiles, shortcut-creation entries, and home-screen widgets.

[Download](https://github.com/yagay/ListCleaner/releases/latest) · [Issues](https://github.com/yagay/ListCleaner/issues) · [Telegram](https://t.me/LISTCLEANER)

## Telegram Channel

Follow **@LISTCLEANER** for release updates, usage tips, and related news.

[Join Telegram channel @LISTCLEANER](https://t.me/LISTCLEANER)

## Requirements

- Android 12 or newer.
- **Rule filtering and priority ordering** require a framework supporting **modern libxposed API 102**. Enable the module, configure its scope, and restart as instructed by the framework.
- **Component management** additionally requires Root permission granted to List Cleaner through KernelSU, Magisk, or another Root manager. Enabling the LSPosed module does not grant Root.

Package name: `com.yagay.ListCleaner`. Chinese environments display “列表清理”; other supported environments display “List Cleaner”.

## Features

### Rules: control which apps appear on system entry surfaces

The Rules page handles Share, Multiple Share, Send to, Open with, Browser, Deep links, Dial, Choose content, Take photo, Record video/audio, Text processing, plus system_server-backed **Long-press shortcuts** and **Storage locations**. **Browser and Deep links are separate categories**: Browser handles generic web candidates, while Deep links supports domain-specific App Link rules that inherit the category's generic rules. Search, expand apps and filter categories or states.

On Android 11+, Direct Share contact/conversation suggestions follow normal Share cleanup: when an app is hidden from Share candidates, its Direct Share targets are filtered as well. List Cleaner does not read or store contact names or conversation content.

Long-press shortcuts combine runtime Hook observations with proactive read-only discovery (focused on enabled Manifest/Dynamic items; pinned-only/cached items are not guaranteed). Storage locations use DocumentsUI provider roots. These only filter system candidates; **they do not disable components or delete shortcuts or files**.

| Display mode | Effect |
| --- | --- |
| Hide selected | Hides selected entries from the corresponding system candidate list |
| Show selected only | Keeps only selected entries; if no rule is selected in that category, all entries remain visible |
| Show all | Temporarily disables filtering while preserving saved selections |

An application-row selection applies to the components currently shown for the active category and search, not to the whole application. Expanding an app allows individual components to be selected. Clearing one category does not clear other categories. Configured entries that cannot currently be scanned can still be removed from selected rules.

Use the **three-dot menu** at the end of an app or component row to lock or unlock; **long-pressing the row** opens the same actions. Left/right swipes are no longer used for locks. Full or partial locks skip bulk Select All/Invert operations and prevent direct selection of locked child rows until they are unlocked. Unlocked rows show no lock indicator. Lock state never changes the selected/partial/unselected state or priority order; Locked under All aggregates category locks.

Rules, Ordering and Components share app-type and status filters for All / User apps / System apps and selected / unselected / locked views. Selected filters use the theme highlight. Filtering changes only the current view and bulk-operation targets, **not saved rules, ordering, locks or Root states**. Updated preinstalled apps remain classified as system apps.

Ordinary Resolver rules alter returned candidate lists; they do not uninstall apps or change component enabled state. Long-press shortcuts and storage locations use dedicated system_server query filtering. Unknown or OEM-specific result shapes fail open so unrecognized data is preserved instead of being accidentally removed.

Package-visibility compatibility applies only to ordinary Resolver categories. Long-press shortcuts and storage providers are explicitly excluded, so selecting either surface never makes the owning app disappear wholesale from another caller's package view.

### Ordering: put frequently used apps first

Each category keeps an independent priority order. Deep links may use domain-specific ordering and inherit that category's generic order when none is configured. Long-press shortcuts and storage locations also support app-level priority without changing system component enabled states.

- Select an app to add it to the priority list and place it at the configured position; deselect it to return it to the default alphabetical group.
- Prioritized apps follow the saved order; other apps are sorted by name. The list can be filtered to all, prioritized, non-prioritized, or locked apps, then narrowed to user or system apps. Neither app-type filtering nor locks move apps or rewrite the saved priority order.
- **Long-press the drag handle** on the right of a prioritized app and move it to reorder; the list auto-scrolls at the edges. Long-pressing the app row itself opens the three-dot action menu. Expanded apps still provide move-up/down controls.
- During search, only matching prioritized apps are rearranged; hidden configuration retains its position. Candidates hidden by rules do not appear in the current ordering list, but their saved priority is retained.

Rule selection and priority edits are **staged changes**. Use the bottom Apply action and confirm the dialog or discard the draft. Unconfirmed changes are not published to live rules.

The module applies priority ordering at supported system query and chooser ordering stages. Vendor-customized choosers, application-specific reordering, and custom menus can behave differently.

### Components: manage launcher icons, tiles, shortcut-creation entries, and widgets

The Components page reads actual system state and supports search, disabled-state filtering, and separate user/system app views for bulk management.

| Category | Supported scope |
| --- | --- |
| Launcher icons | Exported `ACTION_MAIN + CATEGORY_LAUNCHER` Activity / Activity Alias entries |
| Tiles | Standard app-provided `TileService` components; system built-in tiles such as Wi-Fi or Bluetooth without an independent service are excluded |
| Shortcuts | Shortcut configuration activities from Android `LauncherApps` plus legacy `ACTION_CREATE_SHORTCUT` entries; actual static/dynamic/pinned shortcuts are handled non-destructively under Rules → Long-press shortcuts |
| Widgets | Providers from the Android `AppWidgetManager` registry, merged with manifest widget receivers as a compatibility fallback |

| Device Controls | App-provided `ControlsProviderService`; disabling may affect controls in the host UI |
| Account authenticators | `AccountAuthenticator` services; disabling may affect account setup or sync |

Component discovery follows a **public-API first, compatibility fallback, fail-open** model. A component can carry multiple discovery sources and duplicates are merged automatically. Diagnostics record sources such as `PACKAGE_MANAGER`, `APP_WIDGET_MANAGER`, and `LAUNCHER_APPS` so missing entries can be traced to the discovery layer. Installing, updating, or removing packages invalidates candidate caches automatically.

A runtime capability handshake protects upgrades. If the APK is newer while system_server still runs an older hook, List Cleaner does not force the new discovery path: widget discovery falls back to the manifest scan, and LauncherApps/AppWidgetManager failures are fail-open rather than fatal. Full AppWidgetManager discovery is enabled again only after the running hook confirms the newer discovery protocol.

**Selected means a pending request to disable; unselected means a pending request to explicitly enable.** It does not restore a previous default state. Root operations run only after Apply changes is confirmed; Root permission is checked and actual system state is read back afterwards. Missing Root permission or authorization timeout produces an error instead of a false successful state.

Component locks use the same three-dot or long-press menu and Locked filter. Expanded apps and selected details use the shared theme highlight; unselected details have a normal background. **Root disabled state is separate from bulk locks**: locks skip bulk actions and prevent direct selection of locked child rows until unlocked; partial locks affect only the matching child.

Real Root disable remains the primary component-management mechanism. List Cleaner also persists the desired disabled policy and adds a second LSPosed/system_server discovery filter. If Android or vendor services temporarily restore a component during startup, protected launcher icons, tiles, shortcut-creation entries, and widget providers are still removed from discovery results. Queries made by List Cleaner itself bypass this filtering so those components remain manageable.

After boot, List Cleaner automatically reconciles persistent disabled state. Delayed checks run after `BOOT_COMPLETED` and user unlock, followed by another settled-startup pass; app install/update events also trigger reconciliation. Only components that should be disabled but are no longer actually `DISABLED` are repaired, so already-correct entries do not receive redundant Root commands.

When multiple Android user spaces are found, the top selector switches the target. Rules, priority ordering, component state and bulk-operation locks are isolated per space; component actions affect **the currently selected space**, subject to Root/system permissions. Core system components, SystemUI, List Cleaner itself and entries of fully disabled apps remain read-only.

Disabling a component can affect places where it is already used, including launcher entries, existing tiles, and widgets. Re-enabling does not guarantee restoration to its former position. Clearing List Cleaner data or uninstalling the module **does not revert component disabled states**; re-enable components as needed before uninstalling.

### Backup and diagnostics

- **v13 multi-space JSON backups** let you select export spaces and map each source space to a destination on import. Legacy v1–v12 rule backups remain readable. Rules, display modes and priority configuration are included.
- Multi-space exports may include **reference records** of Root-disabled components; importing never executes Root commands or restores actual disabled states or protection policies. UI favorites and mixed order, appearance, and list-default preferences are excluded from rule backups. Re-enable components manually before uninstalling as appropriate.
- View **Module status** and sync information at the top of **Controls**; tap it for **Settings → Cleaning & Compatibility** scope details. Export diagnostic ZIPs under **Settings → Advanced & Diagnostics**; review app lists and logs before sharing.

### Controls: cleanup methods, category switches, and favorites

**Controls** includes Module status, the cleanup overview, Quick access, Favorite categories, and Cleanup management. Its **Entry / Component** quick links open the two editors within Cleanup; Diagnostics opens the diagnostics settings. **Open** is an Entry category, while **Root** is an implementation method, not a tab name.

- **Cleanup management → Entry cleanup:** The first item is the **default cleanup method** (Hide selected, Show selected only, Show all). Each Entry category can inherit or override it; its **enabled/disabled cleanup switch** is independent of that method.
- **Cleanup management → Component cleanup:** Independently enable/disable cleanup for Component categories. These switches **do not immediately disable Android components** and do not re-enable components previously disabled through Root. Search, status filters, Enable all / Disable all and confirmation apply to both managers.
- **Favorite categories:** Tap **+** to select multiple Entry and Component categories with the same icons and selection rows as Cleanup. Long-press to remove or drag a favorite across types; only four are shown by default, and the list can expand. Tapping one opens its exact category. Pins and mixed home order are local UI settings and are not part of rule backups.
- **Managed Entry surfaces:** Settings Apps and Notification Channels are managed on the Entry/LSPosed side through presentation filters, not Root component disabling.

### Flat UI and settings

Flat Material 3 lists share app rows, component details, category filters, and three-dot menus. Bottom navigation is **Controls, Cleanup, Settings**; **Cleanup** contains Entry and Component editors. Supported Entry categories open Priority Order via the sort icon. Expanded apps and selected details use theme highlights; the bottom navigation adds no selection underline.

Settings includes **Appearance & Theme, Lists & Display, Scanning & Refresh, Cleanup & Compatibility, Backup & Restore, Advanced & Diagnostics**, and **About**. Theme, sizing, list and scanning options are configurable. About shows version information and GitHub / [Telegram](https://t.me/LISTCLEANER) links.

## Getting Started

1. Download and install the release APK from [Releases](https://github.com/yagay/ListCleaner/releases/latest).
2. Enable List Cleaner in a module manager supporting API 102, configure the recommended system/chooser scope, and restart as instructed by the framework.
3. Check **Module status** on **Controls**, tap for compatibility/scope details, and manage default methods or category switches under **Controls → Cleanup management → Entry cleanup** with confirmation.
4. In **Cleanup → Entry**, select a category and edit rules; use the sort icon and drag handles for supported ordering, then confirm Apply.
5. In **Cleanup → Component**, use Root to manage launcher icons, tiles, shortcut-creation entries, widgets, device controls, and account authenticators. Long-press shortcuts and storage locations instead use LSPosed candidate filtering.

Component management is independent of rule display modes. Root performs the real component disable, while the LSPosed system_server layer provides restore protection and discovery filtering; third-party apps do not need to be added to the module scope. System/vendor caches can still require closing and reopening a menu.

## FAQ

**Nothing changes after selecting a component?**
Check that your Root manager allows this app to use `su`, then retry as prompted. LSPosed authorization and Root authorization are separate. Component state is based on system read-back.

**Why is an app or entry missing from the list?**
Different Intents, package visibility, Launcher permissions, DocumentsUI caches, and vendor implementations affect scan results. Custom share panels, app-private shortcuts, and non-standard components might not be supported. Explicitly targeted calls are also different from system candidate menus.

**Why do the new entry surfaces not work immediately after installing a test build?**
Long-press shortcuts and storage locations add a new system_server hook. An already-running old hook cannot gain that module dynamically, so restart as requested by the module manager after installing the test APK so the new hook generation can load.

**Why does an update report a signature conflict?**
Release builds should update over releases signed with the same publishing key. Debug builds use a debug signature and may not update over a release or a Debug build produced on another machine. Before uninstalling, export rules and check whether disabled components should be re-enabled.

## Build and Development

The project currently uses Java 17, Gradle 9.4.1, AGP 9.2.0, Compile/Target SDK 37, Min SDK 31, and libxposed API/Service 102.0.0. Install the corresponding Android SDK and configure the SDK path locally.

```bash
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:assembleRelease
```

Localization architecture and bilingual maintenance rules are documented in [Localization](docs/LOCALIZATION.en.md).

Signing configuration and automated publishing are documented in [Release build documentation](docs/RELEASE.md). Never commit private keys, signing passwords, or `local.properties`.

Relevant source, documentation and build changes on branches enabled in the Debug workflow (including `lists-audit-fix-20261031`) trigger Debug checks. Release publication is **manual only**: Markdown commits do not publish APKs, sync LSPosed or post to Telegram.


### Ordering support and system hooks

Priority editing is offered only to entry categories with a corresponding runtime consumer. The sort icon appears grey for unsupported categories but remains tappable to explain that the current list does not support ordering. **An editable order is not a guarantee of final UI order**: Android Sharesheet, launchers, role pickers, and vendor UIs may reorder candidates again.

- **Storage locations / Document Root**: Existing DocumentsUI roots and final-sidebar hooks can promote recognized roots while preserving system rows, separators and user/profile slots; OEM forks may differ.
- **Screen savers / Dream**: A Settings `DreamBackend.getDreamInfos()` hook adjusts the returned candidate order without changing the selected/default dream or enabled state; missing methods are safely skipped.
- **Document Provider, Photo Source, Notification Listener, Wallpaper** remain unsupported for priority editing without a verified final-UI consumer. The legacy Launcher Shortcut kind is retired.

Rules and priorities are independent per Android user space. New spaces default to Hide selected, and the default app-type filter is **All apps** (User/System filters are optional). Existing backup priority data is retained. Verify the actual system UI; passing CI is not proof of device compatibility.
