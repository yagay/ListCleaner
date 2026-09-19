# 未发布改动 / Unreleased

## 中文

- **批量操作锁改为滑动交互**：应用或子项向右滑加锁、向左滑解锁；未锁定时不显示锁图标，锁定后才显示状态图标。整体锁保护整个应用，部分锁只保护对应子项。锁只跳过全选、反选等批量操作，仍可手动修改，不参与排序，也不改变已选/半选/未选或 Root 真实禁用状态。
- **“全部 → 已锁定”改为聚合视图**：规则页会汇总各主分类中的锁，组件页会汇总磁贴、快捷方式和小部件中的锁；在“全部”里执行全选/反选也会尊重这些分类锁。旧版在“全部”页产生的锁会在滑动操作时兼容迁移/清理。
- **更新 APK 后列表不再依赖 Hook 版本一致才显示**：规则、排序、筛选和本地候选扫描与 LSPosed Runtime readiness 解耦。system_server / Resolver 仍运行旧模块时，本地管理列表仍可正常使用；支持的主模块会自动尝试热更新，不能热更新的系统保护层等待后续重启即可。
- **组件发现扩展为通用 Android 标准来源**：小部件优先读取 AppWidgetManager Provider 注册表并合并 Manifest fallback；快捷方式创建入口合并 LauncherApps 配置 Activity 与旧 ACTION_CREATE_SHORTCUT；标准磁贴继续按 TileService 发现，不加入厂商专用类名。
- **新增运行时组件发现能力握手与安全降级**：APK 已更新但 system_server 仍加载旧 Hook 时，不强行进入新的 AppWidgetManager 路径；旧 Hook 自动使用 Manifest 安全扫描。新版 Hook 的 Widget 过滤、ParceledListSlice 反射和 LauncherApps 发现均改为 fail-open，异常时返回原始/旧扫描结果而不是导致闪退。
- **扫描与缓存优化**：应用安装、更新、卸载后自动使候选缓存失效；应用名、应用类型、锁状态和配置缺失项元数据减少重复 PackageManager/SharedPreferences 读取；批量组件操作后的刷新改为每个分类只重新发现一次。
- **诊断增强**：组件条目记录 PACKAGE_MANAGER / APP_WIDGET_MANAGER / LAUNCHER_APPS 等发现来源，诊断包同时记录发现来源统计、运行时组件发现协议、持久禁用策略和开机校正结果，方便定位漏项、旧 Hook 或兼容降级。

## English

- **Bulk-operation locks now use swipe gestures**: swipe right on an app or child entry to lock and swipe left to unlock. Unlocked rows show no lock icon; a status icon appears only after locking. Full locks protect an entire app and partial locks protect selected child entries. Locks only skip Select All/Invert-style bulk actions; manual edits remain available, ordering is unchanged, and selected/partial/unselected or actual Root-disabled state is not modified.
- **Locked under All is now an aggregate view**: Rules aggregates locks from the main rule categories, while Components aggregates Tile, Shortcut, and Widget locks. Bulk Select All/Invert from All also respects those category locks. Legacy locks created by the old All-page behavior are migrated/cleared during swipe operations.
- **Management lists no longer depend on hook-version readiness after an APK update**: Rules, Ordering, filtering, and local candidate discovery are decoupled from LSPosed runtime readiness. Lists remain usable while system_server/Resolver still runs the older module; supported main hooks attempt hot reload while non-reloadable protection hooks can wait for a later restart.
- **Component discovery now uses generalized Android sources**: widgets prefer the AppWidgetManager provider registry with manifest fallback; shortcut creation entries merge LauncherApps configuration activities with legacy ACTION_CREATE_SHORTCUT; standard tiles continue to use TileService discovery without vendor-specific class names.
- **Added runtime discovery capability negotiation and safe fallback**: if the APK is newer than the system_server hook, List Cleaner avoids forcing the new AppWidgetManager path and keeps the manifest-safe fallback. New widget filtering, ParceledListSlice reflection, and LauncherApps discovery are fail-open so errors preserve original/fallback results instead of crashing.
- **Scanning and cache performance improvements**: package add/update/removal invalidates candidate caches automatically; app labels, app type, lock state, and configured-missing package metadata avoid repeated PackageManager/SharedPreferences reads; post-batch component refresh now re-discovers once per affected category.
- **Diagnostics now expose discovery sources**: component entries report PACKAGE_MANAGER / APP_WIDGET_MANAGER / LAUNCHER_APPS sources plus source totals, runtime component-discovery protocol, persistent disabled policy, and boot reconciliation state.

---

# 列表清理 / List Cleaner 1.6.11

版本码 / Version code: 36

## 中文

- 新增统一的系统应用 / 用户应用筛选：规则、排序和组件页都使用节省空间的“应用：全部 / 用户应用 / 系统应用”紧凑下拉，可与现有“查看”筛选组合使用。
- 应用类型在扫描时一次性识别并随候选数据保存，列表过滤不会反复查询 PackageManager；预装系统应用即使后续更新，仍按系统应用处理。
- 应用类型筛选只控制当前显示范围和全选/反选等批量操作目标，不参与现有排序，不改变已选/半选/未选状态，也不改变批量操作锁或 Root 组件状态。
- 组件页可分别查看和批量管理用户应用、系统应用；原有核心系统组件保护规则保持不变，受保护项目仍只展示、不可修改。
- 保留 v1.6.10 的批量操作锁、Root 真禁用、LSPosed 发现过滤以及开机/解锁/应用更新后的自动校正机制。

本版本包含 v1.6.10 之后的全部改动；正式 Release 继续使用固定签名证书、R8 优化、zipalign、SHA-256 与 APK 身份/版本校验。

---

## English

- Added unified user/system app filtering across Rules, Ordering, and Components with a space-saving “Apps: All / User apps / System apps” dropdown that can be combined with the existing View filter.
- App type is classified once during scanning and carried with candidate data, avoiding repeated PackageManager lookups while filtering. Updated preinstalled system apps remain classified as system apps.
- App-type filtering only controls the current view and the target set for Select All/Invert-style bulk actions. It never participates in ordering and does not change selected/partial/unselected state, bulk-operation locks, or Root component state.
- Components can now be viewed and batch-managed separately for user and system apps. Existing protection for core system components is unchanged; protected entries remain display-only.
- The v1.6.10 bulk-operation locks, real Root disable, LSPosed discovery filtering, and automatic reconciliation after boot/unlock/app updates remain in place.

This release includes all changes after v1.6.10. Production releases continue to use the pinned signing certificate, R8 optimization, zipalign, SHA-256, and APK identity/version verification.
