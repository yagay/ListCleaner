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

Manage candidates separately for Share, multi-file Share, Open with, Browser, and Text processing. Search apps, expand their components, and filter by all, selected, or unselected entries.

| Display mode | Effect |
| --- | --- |
| Hide selected | Hides selected entries from the corresponding system candidate list |
| Show selected only | Keeps only selected entries; if no rule is selected in that category, all entries remain visible |
| Show all | Temporarily disables filtering while preserving saved selections |

An application-row selection applies to the components currently shown for the active category and search, not to the whole application. Expanding an app allows individual components to be selected. Clearing one category does not clear other categories. Configured entries that cannot currently be scanned can still be removed from selected rules.

Rules alter returned candidate lists. They do not uninstall applications or change component enabled state. The scan catalog is a configuration aid and does not imply that every file exposes the same candidates.

### Ordering: put frequently used apps first

Each of the five categories stores its own app priority order.

- Select an app to add it to the priority list and place it at the configured position; deselect it to return it to the default alphabetical group.
- Prioritized apps follow the saved order; other apps are sorted by name. The list can be filtered to all, prioritized, or non-prioritized apps.
- **Long-press a prioritized app to drag it.** The list auto-scrolls near its edges and saves when released. Move-up and move-down controls are also available after expansion.
- During search, only matching prioritized apps are rearranged; hidden configuration retains its position. Candidates hidden by rules do not appear in the current ordering list, but their saved priority is retained.

The module applies priority ordering at supported system query and chooser ordering stages. Vendor-customized choosers, application-specific reordering, and custom menus can behave differently.

### Components: manage tiles, shortcuts, and widgets

The Components page reads actual system state and supports search and disabled-state filtering.

| Category | Supported scope |
| --- | --- |
| Tiles | Standard app-provided `TileService` components; system built-in tiles such as Wi-Fi or Bluetooth without an independent service are excluded |
| Shortcuts | Standard `ACTION_CREATE_SHORTCUT` creation entries; not every dynamic, pinned, or private shortcut is included |
| Widgets | Standard home-screen widget receivers declaring widget metadata |

**Selected means disabled; unselected means explicitly enabled.** It does not restore a previous default state. Root is checked before an operation and system state is read back afterwards. Missing Root permission or authorization timeout produces an error instead of a false successful state.

Component operations apply only to the Android user running List Cleaner. Core system components, SystemUI, List Cleaner itself, and components belonging to an application that is disabled as a whole are displayed but cannot be changed.

Disabling a component can affect places where it is already used, including existing tiles and widgets. Re-enabling does not guarantee restoration to its former position. Clearing List Cleaner data or uninstalling the module **does not revert component disabled states**; re-enable components as needed before uninstalling.

### Backup and diagnostics

- Import and export JSON rule backups containing rules, display modes, and priority ordering, compatible with backup formats v1–v4.
- Rule backups **do not save or restore actual Root component enabled states**. Legacy tile configuration remains readable for compatibility but is not automatically converted into component-disable operations.
- The Status page shows module connection, scope, and configuration synchronization state. A diagnostic ZIP can be exported to troubleshoot filtering, ordering, and Root operations. Diagnostics can contain application lists and logs; review them before sharing.

## Getting Started

1. Download and install the release APK from [Releases](https://github.com/yagay/ListCleaner/releases/latest).
2. Enable List Cleaner in a module manager supporting API 102, configure the recommended system/chooser scope, and restart as instructed by the framework.
3. Open List Cleaner, confirm module and configuration synchronization on the Status page, then configure a category and display mode on the Rules page.
4. To change app ordering, select and drag frequently used apps on the Ordering page.
5. To manage tiles, shortcuts, or widgets, open Components and grant Root in your Root manager. Return to List Cleaner and retry after authorization.

Component management is independent of rule display modes and does not require a SystemUI hook. System/vendor caches can require closing and reopening a menu; some devices require restarting the affected process or the system.

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
