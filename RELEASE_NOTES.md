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
