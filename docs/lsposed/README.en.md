# List Cleaner

[简体中文](https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/blob/main/README.md) | **English**

<!-- section:intro -->
Trim Android Share, Open with, Browser/Deep links, long-press shortcut and file-picker candidates, with priority ordering and Android multi-space support. Root mode manages launcher icons, Quick Settings tiles, shortcut-creation entries and widgets.

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

- **System entry cleanup:** Manage Share, Multiple Share, Send to, Open with, Browser, Deep links, Dial, content/media pickers, Text processing, Long-press shortcuts and Storage Access Framework locations. Browser and Deep links are distinct; Deep links supports domain rules.
- **Filtering and actions:** Search, expand apps and filter categories or user/system apps. A three-dot menu (also available by long-press) locks or unlocks app/component rows. Bulk actions skip locked items and locked child rows must be unlocked before direct selection. Rule changes require Apply confirmation.
- **Priority ordering:** Save independent ordering per category, drag the handle on prioritized apps or move up/down, and confirm Apply. Long-pressing the app row opens its action menu.
- **Root components:** Manage launcher icons, TileService tiles, LauncherApps/legacy shortcut-creation activities and AppWidgetManager widgets. Prefer public Android APIs with safe fallbacks. Root disable is backed by LSPosed discovery filtering and boot/update reconciliation; system read-back determines actual state.
- **Android user spaces:** Isolate rules, priority, component state and bulk locks per user; switch spaces, export selected spaces in v13 and map each source to an import destination.
- **Settings and diagnostics:** Unified flat Material 3 lists; centralized Appearance, Lists, Scanning, Compatibility, Backup, Advanced Diagnostics and About pages with GitHub / Telegram links.

<!-- section:usage -->
## Usage Notes

Bottom navigation contains **Cleanup, Ordering and Settings**; Cleanup switches between **Rule cleanup (LSPosed)** and **Component cleanup (Root)**. Rules filter system candidates without disabling components, while Component cleanup changes actual enabled state through Root. The three-dot or long-press menu controls **bulk locks**, which are not disabled state. Rule, ordering and Root component changes require Apply confirmation at the bottom before Root commands can run.

When multiple Android user spaces are detected, choose the target space at the top. v13 backups support selected spaces and explicit source-to-target mapping; v1–v12 rule backups remain readable. Root-disabled component records can be exported as **reference information**, but importing **never runs Root commands or restores actual disabled states/protection policies**. Uninstalling or clearing app data also does not automatically re-enable previously disabled components.

OEM choosers, private app menus and some dynamic shortcuts may differ. Discovery uses public Android APIs with safe compatibility fallbacks. An updated APK may degrade discovery safely while an older system_server hook remains loaded; restart as required by the framework. Check Settings → Cleanup & Compatibility / Advanced & Diagnostics, and review logs before sharing.

<!-- section:release -->
Official builds in the author repository and the LSPosed repository use the same APK and pinned release signature. Debug builds may use a different signature, so export your rules and review component states before uninstalling.

<!-- section:feedback -->
When reporting an issue, include the Android version, device model, framework version, and reproduction steps. Review the exported diagnostic package before sharing it because it may contain app lists and logs.
