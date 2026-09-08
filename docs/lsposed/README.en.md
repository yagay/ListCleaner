# List Cleaner

[简体中文](https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/blob/main/README.md) | **English**

<!-- section:intro -->
Trim Android share, open-with, browser, and text-processing menus so frequently used apps appear first. With Root access, List Cleaner can also manage app-provided Quick Settings tiles, shortcut creation entries, and home-screen widgets.

[Download module](https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/releases/latest) · [Source and full documentation](https://github.com/yagay/ListCleaner) · [Report an issue](https://github.com/yagay/ListCleaner/issues) · [Telegram channel](https://t.me/LISTCLEANER)

<!-- section:telegram -->
## Telegram Channel

Follow **@LISTCLEANER** for release updates, usage tips, and related news.

[Join Telegram channel @LISTCLEANER](https://t.me/LISTCLEANER)

<p align="center">
  <a href="https://t.me/LISTCLEANER">
    <img src="https://raw.githubusercontent.com/yagay/ListCleaner/main/docs/telegram-channel.jpg" alt="List Cleaner Telegram channel QR code" width="360">
  </a>
</p>

<!-- section:requirements -->
## Requirements

- Android 12 or newer.
- **Rule filtering and ordering** require a framework supporting **modern libxposed API 102**. Enable the module, configure the recommended scope, and restart as required by the framework.
- **Component management** additionally requires Root permission through KernelSU, Magisk, or another Root manager. Enabling the LSPosed module does not grant Root access.

<!-- section:features -->
## Features

- **Rule filtering**: Configure Share, Multiple Share, Open With, Browser, and Text Processing separately. Supports app search and per-app component expansion. Choose Hide selected, Show only selected, or Show all; when a category has no selected rules, everything is shown.
- **Priority ordering**: Save a separate app order for each category. Selecting an app adds it to the priority list, while deselecting returns it to the default name order. Supports long-press drag-and-drop as well as move up/down controls.
- **Component management**: Manage standard TileService entries, ACTION_CREATE_SHORTCUT creation entries, and home-screen widgets. Selected means disabled, and deselected means explicitly enabled. Root permission is checked before changes, and the actual system state is read back afterward.
- **Backup and diagnostics**: Import and export JSON backups for rules and ordering, view module status, and export diagnostic logs.

<!-- section:usage -->
## Usage Notes

Selections on the Rules page apply only to the components shown for the current category and search conditions; they do not disable the entire app. The Components page changes the actual enabled state of Android components, so the two features serve different purposes.

Component operations affect only the Android user where List Cleaner is installed. Disabling a component can affect tiles or widgets that are already added, and re-enabling it does not guarantee that its previous position will be restored. Uninstalling the module or clearing app data does not revert disabled component states, and rule backups do not include those states.

Vendor-customized choosers, apps that reorder candidates themselves, or custom in-app menus may not be supported. Shortcut management does not cover every dynamic, pinned, or private shortcut, and built-in system tiles without a standalone service are outside the managed scope.

<!-- section:release -->
Official builds in the author repository and the LSPosed repository use the same APK and pinned release signature. Debug builds may use a different signature, so export your rules and review component states before uninstalling.

<!-- section:feedback -->
When reporting an issue, include the Android version, device model, framework version, and reproduction steps. Review the exported diagnostic package before sharing it because it may contain app lists and logs.
