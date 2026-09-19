# List Cleaner

[简体中文](README.md) | **English**

Trim Android share, open-with, browser, and text-processing menus so frequently used apps appear first. With Root access, List Cleaner can also manage app-provided Quick Settings tiles, shortcut creation entries, and home-screen widgets.

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

### Rules: control which apps appear in menus

Manage candidates separately for Share, multi-file Share, Open with, Browser, and Text processing. Search apps, expand their components, and filter by all, selected, unselected, or locked entries.

| Display mode | Effect |
| --- | --- |
| Hide selected | Hides selected entries from the corresponding system candidate list |
| Show selected only | Keeps only selected entries; if no rule is selected in that category, all entries remain visible |
| Show all | Temporarily disables filtering while preserving saved selections |

An application-row selection applies to the components currently shown for the active category and search, not to the whole application. Expanding an app allows individual components to be selected. Clearing one category does not clear other categories. Configured entries that cannot currently be scanned can still be removed from selected rules.

Each page can independently apply a **bulk-operation lock**. **Swipe right to lock and swipe left to unlock** an app or child entry. Unlocked rows show no lock icon; the icon appears only after locking. A full lock protects an entire app from Select All/Invert-style operations, while a partial lock protects only selected child entries. Locked entries remain manually editable. Lock state never participates in ordering or changes Selected / Partially selected / Unselected calculations; Locked under All aggregates locks from the individual categories.

Rules, Ordering, and Components all use the same compact **Apps: All / User apps / System apps** dropdown, which can be combined with the existing View filter. App type only controls what is currently shown and what bulk actions target; it **never participates in ordering or changes selection, lock, or Root state**. Updated preinstalled system apps remain classified as system apps.

Rules alter returned candidate lists. They do not uninstall applications or change component enabled state. The scan catalog is a configuration aid and does not imply that every file exposes the same candidates.

### Ordering: put frequently used apps first

Each of the five categories stores its own app priority order.

- Select an app to add it to the priority list and place it at the configured position; deselect it to return it to the default alphabetical group.
- Prioritized apps follow the saved order; other apps are sorted by name. The list can be filtered to all, prioritized, non-prioritized, or locked apps, then narrowed to user or system apps. Neither app-type filtering nor locks move apps or rewrite the saved priority order.
- **Long-press a prioritized app to drag it.** The list auto-scrolls near its edges and saves when released. Move-up and move-down controls are also available after expansion.
- During search, only matching prioritized apps are rearranged; hidden configuration retains its position. Candidates hidden by rules do not appear in the current ordering list, but their saved priority is retained.

The module applies priority ordering at supported system query and chooser ordering stages. Vendor-customized choosers, application-specific reordering, and custom menus can behave differently.

### Components: manage tiles, shortcuts, and widgets

The Components page reads actual system state and supports search, disabled-state filtering, and separate user/system app views for bulk management.

| Category | Supported scope |
| --- | --- |
| Tiles | Standard app-provided `TileService` components; system built-in tiles such as Wi-Fi or Bluetooth without an independent service are excluded |
| Shortcuts | Shortcut configuration activities from Android `LauncherApps` plus legacy `ACTION_CREATE_SHORTCUT` entries; dynamic/pinned shortcut instances requiring launcher-host access and private entries are not included |
| Widgets | Providers from the Android `AppWidgetManager` registry, merged with manifest widget receivers as a compatibility fallback |

Component discovery follows a **public-API first, compatibility fallback, fail-open** model. A component can carry multiple discovery sources and duplicates are merged automatically. Diagnostics record sources such as `PACKAGE_MANAGER`, `APP_WIDGET_MANAGER`, and `LAUNCHER_APPS` so missing entries can be traced to the discovery layer. Installing, updating, or removing packages invalidates candidate caches automatically.

A runtime capability handshake protects upgrades. If the APK is newer while system_server still runs an older hook, List Cleaner does not force the new discovery path: widget discovery falls back to the manifest scan, and LauncherApps/AppWidgetManager failures are fail-open rather than fatal. Full AppWidgetManager discovery is enabled again only after the running hook confirms the newer discovery protocol.

**Selected means disabled; unselected means explicitly enabled.** It does not restore a previous default state. Root is checked before an operation and system state is read back afterwards. Missing Root permission or authorization timeout produces an error instead of a false successful state.

The Components page also supports bulk-operation locks and a Locked filter. **Swipe right to lock and left to unlock**; unlocked rows show no lock icon, while locked rows show a status icon. A full lock protects the whole app and a partial lock protects only selected child entries. Locked under All aggregates locks from Tile, Shortcut, and Widget categories. Locks only protect Select All/Invert-style operations: they are **not Root disabled state, do not participate in ordering, and do not change selected/partial/unselected state**. Locked entries remain manually editable.

Real Root disable remains the primary component-management mechanism. List Cleaner also persists the desired disabled policy and adds a second LSPosed/system_server discovery filter. If Android or vendor services temporarily restore a component during startup, protected tiles, shortcut entries, and widget providers are still removed from discovery results. Queries made by List Cleaner itself bypass this filtering so those components remain manageable.

After boot, List Cleaner automatically reconciles persistent disabled state. Delayed checks run after `BOOT_COMPLETED` and user unlock, followed by another settled-startup pass; app install/update events also trigger reconciliation. Only components that should be disabled but are no longer actually `DISABLED` are repaired, so already-correct entries do not receive redundant Root commands.

Component operations apply only to the Android user running List Cleaner. Core system components, SystemUI, List Cleaner itself, and components belonging to an application that is disabled as a whole are displayed but cannot be changed.

Disabling a component can affect places where it is already used, including existing tiles and widgets. Re-enabling does not guarantee restoration to its former position. Clearing List Cleaner data or uninstalling the module **does not revert component disabled states**; re-enable components as needed before uninstalling.

### Backup and diagnostics

- Import and export JSON rule backups containing rules, display modes, and priority ordering, compatible with backup formats v1–v9.
- Rule backups **do not save or restore actual Root component enabled states**. Legacy tile configuration remains readable for compatibility but is not automatically converted into component-disable operations.
- The Status page shows module connection, scope, and configuration synchronization state. A diagnostic ZIP can be exported to troubleshoot filtering, ordering, and Root operations. Diagnostics can contain application lists and logs; review them before sharing.

## Getting Started

1. Download and install the release APK from [Releases](https://github.com/yagay/ListCleaner/releases/latest).
2. Enable List Cleaner in a module manager supporting API 102, configure the recommended system/chooser scope, and restart as instructed by the framework.
3. Open List Cleaner, confirm module and configuration synchronization on the Status page, then configure a category and display mode on the Rules page.
4. To change app ordering, select and drag frequently used apps on the Ordering page.
5. To manage tiles, shortcuts, or widgets, open Components and grant Root in your Root manager. Return to List Cleaner and retry after authorization.

Component management is independent of rule display modes. Root performs the real component disable, while the LSPosed system_server layer provides restore protection and discovery filtering; third-party apps do not need to be added to the module scope. System/vendor caches can still require closing and reopening a menu.

## FAQ

**Nothing changes after selecting a component?**
Check that your Root manager allows this app to use `su`, then retry as prompted. LSPosed authorization and Root authorization are separate. Component state is based on system read-back.

**Why is an app or entry missing from the list?**
Different Intents, package visibility, and vendor implementations affect scan results. Custom share panels, private shortcuts, and non-standard components might not be supported. Explicitly targeted calls are also different from system candidate menus.

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

Source/build changes on `main` automatically build Debug and run unit tests. Formal releases use the Release workflow; existing versions are not republished, and a new release requires both version name and version code updates.
