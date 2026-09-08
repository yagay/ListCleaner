# List Cleaner

**English** | [简体中文](README.zh-CN.md)

Trim Android share, open-with, browser, and text-processing menus so frequently used apps appear first. With Root access, List Cleaner can also manage app-provided Quick Settings tiles, shortcut creation entries, and home-screen widgets.

[Download module](https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/releases/latest) · [Source and full documentation](https://github.com/yagay/ListCleaner) · [Report an issue](https://github.com/yagay/ListCleaner/issues) · [Telegram channel](https://t.me/LISTCLEANER)

## Telegram channel

Follow **@LISTCLEANER** for release updates, usage tips, and related announcements.

[Join @LISTCLEANER on Telegram](https://t.me/LISTCLEANER)

<p align="center">
  <a href="https://t.me/LISTCLEANER">
    <img src="https://raw.githubusercontent.com/yagay/ListCleaner/main/docs/telegram-channel.jpg" alt="List Cleaner Telegram channel QR code" width="360">
  </a>
</p>

## Requirements

- Android 12 or newer.
- **Rule filtering and ordering** require a framework that supports **modern libxposed API 102**. Enable the module, configure the recommended scope, and restart as instructed by the framework.
- **Component management** additionally requires Root permission through KernelSU, Magisk, or another Root manager. Enabling the LSPosed module does not grant Root access.

## Features

- **Rule filtering:** configure Share, Multi-share, Open with, Browser, and Text processing independently. Search apps, expand components, and choose Hide selected, Show selected only, or Show all. If a category has no selected rules, everything is shown.
- **Priority ordering:** save app order separately by category. Select an app to prioritize it, deselect it to return to normal alphabetical order, and use drag-and-drop or move-up/move-down controls.
- **Component management:** manage standard `TileService`, `ACTION_CREATE_SHORTCUT` creation entries, and home-screen widgets. Selected means disabled; deselected means explicitly enabled. Root is checked before changes and Android state is read back afterward.
- **Backup and diagnostics:** import/export JSON backups for rules and ordering, inspect module state, and export diagnostic logs.

## Notes

App selection on the Rules page only applies to components visible under the current category and search filter; it does not disable an entire app. The Components page changes actual Android component enabled state, so these two features have different effects.

Component operations apply only to the Android user where List Cleaner is installed. Disabling a component may affect already-added tiles or widgets, and re-enabling it does not guarantee restoration to its previous position. Uninstalling the module or clearing its data does not revert component disabled states, and rule backups do not include those states.

OEM selectors, app-side reordering, or custom menus may not be supported. Shortcut management does not cover every dynamic, pinned, or private app shortcut, and built-in system tiles without standalone services are outside the managed scope.

Official releases use the same APK and fixed signing certificate as the source repository. Debug builds may use a different certificate; export your rules and review component state before uninstalling.

When reporting a problem, include Android version, device model, framework version, and reproduction steps. Review app-list and log contents before sharing diagnostics.
