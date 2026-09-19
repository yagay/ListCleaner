# 列表清理 / List Cleaner 1.6.11

版本码 / Version code: 36

## 中文

- 新增统一的系统应用 / 用户应用筛选：规则、排序和组件页都使用节省空间的“应用：全部 / 用户应用 / 系统应用”紧凑下拉，可与现有“查看”筛选组合使用。
- 应用类型在扫描时一次性识别并随候选数据保存，列表过滤不会反复查询 PackageManager；预装系统应用即使后续更新，仍按系统应用处理。
- 应用类型筛选只控制当前显示范围和全选/反选等批量操作目标，不参与现有排序，不改变已选/半选/未选状态，也不改变批量操作锁或 Root 组件状态。
- 组件页可分别查看和批量管理用户应用、系统应用；原有核心系统组件保护规则保持不变，受保护项目仍只展示、不可修改。
- 批量操作锁改为手势操作：应用或子项向右滑加锁、向左滑解锁；未锁定时不显示锁图标，锁定后才显示状态图标。整体锁保护整个应用，部分锁只保护对应子项；锁只跳过全选/反选等批量操作，仍可手动修改，不参与排序，也不改变已选/半选/未选或 Root 状态。“全部 → 已锁定”会汇总各分类中的锁定项目。
- 组件发现改为通用 Android 标准层优先：Widget 优先读取 AppWidgetManager Provider 注册表并以 Manifest 扫描补充；快捷方式创建入口合并 LauncherApps 配置 Activity 与旧 ACTION_CREATE_SHORTCUT；同一组件多来源自动去重。
- 新增组件发现来源诊断，可记录 PACKAGE_MANAGER、APP_WIDGET_MANAGER、LAUNCHER_APPS 等来源；安装、更新或卸载应用后会自动使候选缓存失效。
- 新增组件发现运行时能力握手与自动降级：若 APK 已更新但 system_server 仍运行旧 Hook，不强行调用新 Widget 注册表；未确认新协议时自动使用旧安全扫描。LauncherApps、AppWidgetManager 与 discovery Hook 遇到异常时均 fail-open，保留原始/旧扫描结果而不是让应用崩溃。
- 优化扫描与批量操作性能：锁状态改为内存缓存；应用名、应用状态和已配置候选元数据减少重复 PackageManager 查询；批量组件操作完成后按分类一次重新发现并批量回读状态。
- 保留 Root 真禁用、LSPosed 发现过滤以及开机/解锁/应用更新后的自动校正机制。

本版本包含 v1.6.10 之后的全部改动；正式 Release 继续使用固定签名证书、R8 优化、zipalign、SHA-256 与 APK 身份/版本校验。

---

## English

- Added unified user/system app filtering across Rules, Ordering, and Components with a space-saving “Apps: All / User apps / System apps” dropdown that can be combined with the existing View filter.
- App type is classified once during scanning and carried with candidate data, avoiding repeated PackageManager lookups while filtering. Updated preinstalled system apps remain classified as system apps.
- App-type filtering only controls the current view and the target set for Select All/Invert-style bulk actions. It never participates in ordering and does not change selected/partial/unselected state, bulk-operation locks, or Root component state.
- Components can now be viewed and batch-managed separately for user and system apps. Existing protection for core system components is unchanged; protected entries remain display-only.
- Bulk-operation locks now use gestures: swipe right on an app or child entry to lock and swipe left to unlock. Unlocked rows show no lock icon; a status icon appears only after locking. Full locks protect the whole app and partial locks protect selected children. Locks only skip Select All/Invert-style bulk actions, remain manually editable, never participate in ordering, and do not change selected/partial/unselected or Root state. Locked under All aggregates category locks.
- Component discovery now prefers generalized Android subsystem registries: widgets use AppWidgetManager providers with manifest fallback, while shortcut creation entries merge LauncherApps configuration activities with legacy ACTION_CREATE_SHORTCUT entries. Multiple discovery sources are deduplicated.
- Diagnostics now record component discovery sources such as PACKAGE_MANAGER, APP_WIDGET_MANAGER, and LAUNCHER_APPS. Installing, updating, or removing apps invalidates candidate caches automatically.
- Added a runtime component-discovery capability handshake and safe fallback. If the APK is newer while system_server still runs an older hook, List Cleaner avoids forcing the new widget-registry path and falls back to the older safe scan until the new protocol is confirmed. LauncherApps, AppWidgetManager, and discovery-hook failures are fail-open so the original/legacy result is preserved instead of crashing the app.
- Reduced repeated work during scanning and bulk actions: lock state is cached in memory, app labels/state/configured-candidate metadata reuse package lookups, and post-batch component refresh performs one discovery pass per affected category.
- Real Root disable, LSPosed discovery filtering, and automatic reconciliation after boot/unlock/app updates remain in place.

This release includes all changes after v1.6.10. Production releases continue to use the pinned signing certificate, R8 optimization, zipalign, SHA-256, and APK identity/version verification.
