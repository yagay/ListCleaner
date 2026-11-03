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
- **Controls and diagnostics:** Controls shows Module status, default/per-category cleanup methods, category enabled/disabled switches, mixed Entry/Component favorites, and quick links. Settings retains Appearance, Lists, Scanning, Compatibility/Scope, Backup, Advanced Diagnostics and About.

<!-- section:usage -->
## Usage Notes

Bottom navigation contains **Controls, Cleanup and Settings**. **Cleanup** has **Entry / Component** editors; supported Entry kinds open Priority Order via the sort icon, while Component manages actual states through Root. **Controls → Cleanup management → Entry cleanup** starts with the default method; categories may inherit or override it. Component category enabled/disabled switches do not immediately disable or restore Android components. **Favorite categories** combines Entry/Component pins, original category icons, long-press ordering/removal and expansion. Rule, ordering, switch and Root operations require confirmation; bulk locks remain separate.

When multiple Android user spaces are detected, choose the target space at the top. v13 backups support selected spaces and explicit source-to-target mapping; v1–v12 rule backups remain readable. Root-disabled component records can be exported as **reference information**, but importing **never runs Root commands or restores actual disabled states/protection policies**. Uninstalling or clearing app data also does not automatically re-enable previously disabled components.

OEM choosers, private app menus and some dynamic shortcuts may differ. Discovery uses public Android APIs with safe fallbacks; an updated APK may still require hooked processes to restart. Check **Controls → Module status** before **Settings → Cleaning & Compatibility / Advanced & Diagnostics**; review logs before sharing.



### Ordering support and system hooks

Priority editing is offered only to entry categories with a corresponding runtime consumer. The sort icon appears grey for unsupported categories but remains tappable to explain that the current list does not support ordering. **An editable order is not a guarantee of final UI order**: Android Sharesheet, launchers, role pickers, and vendor UIs may reorder candidates again.

- **Storage locations / Document Root**: Existing DocumentsUI roots and final-sidebar hooks can promote recognized roots while preserving system rows, separators and user/profile slots; OEM forks may differ.
- **Screen savers / Dream**: A Settings `DreamBackend.getDreamInfos()` hook adjusts the returned candidate order without changing the selected/default dream or enabled state; missing methods are safely skipped.
- **Document Provider, Photo Source, Notification Listener, Wallpaper** remain unsupported for priority editing without a verified final-UI consumer. The legacy Launcher Shortcut kind is retired.

Rules and priorities are independent per Android user space. New spaces default to Hide selected; the default app-type filter is **All apps**. Existing backup priority data is retained. Validate the actual host UI; passing CI does not prove device compatibility.

<!-- section:release -->
Official builds in the author repository and the LSPosed repository use the same APK and pinned release signature. Debug builds may use a different signature, so export your rules and review component states before uninstalling.

<!-- section:feedback -->
When reporting an issue, include the Android version, device model, framework version, and reproduction steps. Review the exported diagnostic package before sharing it because it may contain app lists and logs.
