# List Cleaner

**English** | [简体中文](README.zh-CN.md)

Trim Android share, open-with, browser, and text-processing menus so the apps you use most appear first. With Root access, List Cleaner can also manage app-provided Quick Settings tiles, shortcut creation entries, and home-screen widgets.

[Download release](https://github.com/yagay/ListCleaner/releases/latest) · [Report an issue](https://github.com/yagay/ListCleaner/issues) · [Telegram channel](https://t.me/LISTCLEANER)

## Telegram channel

Follow **@LISTCLEANER** for release updates, usage tips, and related announcements.

[Join @LISTCLEANER on Telegram](https://t.me/LISTCLEANER)

<p align="center">
  <a href="https://t.me/LISTCLEANER">
    <img src="docs/telegram-channel.jpg" alt="List Cleaner Telegram channel QR code" width="360">
  </a>
</p>

## Requirements

- Android 12 or newer.
- **Rule filtering and priority ordering:** a framework that supports **modern libxposed API 102**, with the module enabled and its scope configured. Legacy Xposed APIs and frameworks that only support older APIs are not supported.
- **Component management:** grant List Cleaner Root access through KernelSU, Magisk, or another Root manager. Enabling the LSPosed module does not grant Root access. Browsing the component list itself does not require Root.

Package name: `com.yagay.ListCleaner`. The app is displayed as “列表清理” in Chinese and “List Cleaner” in other languages.

## Features

### Rules: choose which apps appear in system menus

Manage five candidate-list categories independently: Share, Multi-share, Open with, Browser, and Text processing. You can search apps, expand them to inspect components, and filter the list by all, selected, or unselected items.

| Display mode | Effect of selecting an item |
| --- | --- |
| Hide selected | Hide selected items from the matching system candidate list |
| Show selected only | Keep only selected items; if no rules are selected for the category, show everything |
| Show all | Pause filtering while keeping saved selections |

**Selecting an app row applies only to components currently visible under the active category and search filter.** It does not disable the whole app. Expand an app to select individual components. Cleaning one category does not automatically affect another category. Configured entries that cannot currently be scanned can still be removed from the selected-rules view.

Rules adjust candidate lists returned by the system. They do not uninstall apps or change Android component enabled states. The scan catalog is a configuration entry point and does not imply that every file type will expose the same candidates.

### Ordering: put frequently used apps first

Each of the five categories stores its own app-level priority order.

- Select an app to add it to the priority list and place it at the corresponding position. Deselect it to return it to the normal alphabetical group.
- Prioritized apps are shown first in the saved order; non-prioritized apps follow alphabetically. You can filter by all, prioritized, or non-prioritized apps.
- **Long-press prioritized apps to drag and reorder them.** The list auto-scrolls near the edges and saves on release. Expanded rows also provide move-up and move-down controls.
- While searching, only visible prioritized apps are reordered; hidden configuration retains its position. Candidates hidden by rules are omitted from the current ordering list, but their saved order is preserved.

The module applies priority ordering in supported system query and selector-ordering paths. OEM selectors, app-side reordering, or fully custom menus may behave differently.

### Components: manage tiles, shortcuts, and widgets

The Components page reads actual Android component state and supports search plus disabled-state filtering.

| Category | Supported scope |
| --- | --- |
| Tiles | Standard app-provided `TileService` components; built-in system tiles without standalone services, such as Wi-Fi or Bluetooth, are not included |
| Shortcuts | Standard `ACTION_CREATE_SHORTCUT` creation entries; not every dynamic, pinned, or private app shortcut is covered |
| Widgets | Standard home-screen widget receivers that declare widget metadata |

**Selected means disabled; deselected means explicitly enabled.** It does not restore a previous default state. Root permission is checked before changes, and the resulting system state is read back afterward. If authorization is missing or times out, the app reports that instead of showing a false success state.

Component operations apply only to the Android user in which List Cleaner is installed. Core system components, SystemUI, List Cleaner itself, and components belonging to apps that are globally disabled are shown as read-only.

Disabling a component can affect existing placements such as added tiles or widgets. Re-enabling it does not guarantee restoration to its former position. Clearing List Cleaner data or uninstalling the module **does not revert component disabled states**; re-enable anything you need before uninstalling.

### Backup and diagnostics

- Import and export JSON backups containing rules, display modes, and priority order. Backup formats v1–v4 are supported.
- Rule backups **do not save or restore actual Root-managed component enabled states**. Legacy tile configuration is read only for compatibility and is not automatically converted into component disable operations.
- The Status page shows module connection, scope, and configuration synchronization state. Diagnostic ZIP export can help troubleshoot filtering, ordering, and Root operations. Diagnostics may contain app lists and logs, so review them before sharing.

## Getting started

1. Download and install the release APK from [Releases](https://github.com/yagay/ListCleaner/releases/latest).
2. Enable List Cleaner in an API 102-capable module manager, configure the recommended system and actual selector scopes, then restart as instructed by the framework.
3. Open the app, confirm module and configuration synchronization on the Status page, then configure categories and display mode on the Rules page.
4. To change app ordering, select and drag frequently used apps on the Ordering page.
5. To manage tiles, shortcut creation entries, or widgets, open the Components page and grant Root permission in your Root manager.

Component management is independent of rule display mode and does not require a SystemUI hook. System or OEM caches may require closing and reopening the relevant menu; some devices may require restarting the affected process or the system before changes become visible.

## FAQ

**Nothing changed after I selected a component.**
Check whether your Root manager allows this app to use `su`, then retry based on the in-app message. LSPosed authorization and Root authorization are separate. The component list reflects the state read back from Android.

**Why is an app or entry missing from the list?**
Different Intents, package visibility rules, and OEM implementations affect scan results. Custom share panels, private shortcuts, and non-standard component declarations may not be supported. Explicitly targeted Intents are also different from system candidate menus.

**Why do I get a signature conflict when updating?**
Official releases use the same release certificate for in-place upgrades. Debug builds use debug certificates and may not replace an official release or a Debug build produced on another machine. Before uninstalling, export your rules and check whether any disabled components should be re-enabled.

## Build and development

The project currently uses Java 17, Gradle 9.4.1, AGP 9.2.0, Compile/Target SDK 37, Min SDK 31, and libxposed API/Service 102.0.0. Install the corresponding Android SDK and configure your local SDK path.

```bash
# Build Debug
bash ./gradlew :app:assembleDebug

# Run unit tests
bash ./gradlew :app:testDebugUnitTest

# Build Release after configuring signing
bash ./gradlew :app:assembleRelease
```

See [Release and signing](docs/RELEASE.md) for signing and automated publishing details. Do not commit private keys, signing passwords, or `local.properties`.

Source or build-configuration changes on `main` automatically build Debug and run unit tests, and the workflow can also be started manually from Actions. Debug APKs are available from the corresponding workflow artifacts. Use the Release workflow for formal releases; existing versions are never overwritten, and new releases require both `versionName` and `versionCode` to be incremented.

Main source locations:

- `app/src/main/java/com/yagay/ListCleaner/ui`: UI and interaction.
- `app/src/main/java/com/yagay/ListCleaner/data`: catalog scanning, configuration storage, Root operations, and diagnostics.
- `app/src/main/java/com/yagay/ListCleaner/domain`: rules, ordering, and validation logic.
- `app/src/main/java/com/yagay/ListCleaner/xposed`: module entry points and system hooks.
- `app/src/test`, `tools/*Check.java`: unit tests and host-side Java regression checks.

When reporting an issue, include Android version, device model, framework version, affected category, and reproduction steps. Attach a reviewed diagnostic log when needed.
