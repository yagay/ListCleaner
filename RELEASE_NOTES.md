# 列表清理 / List Cleaner 1.6.10

版本码 / Version code: 35

## 中文

- 新增批量操作锁：规则、排序和组件页统一支持整体/部分锁定，“已锁定”可单独筛选；锁定项跳过全选/反选等批量操作，但仍可手动修改，且锁定不参与排序、不改变已选/半选/未选状态。
- 强化 Root 组件持久保护：保留真实 `pm disable`，持久保存期望禁用状态，并加入 LSPosed/system_server 发现过滤，防止磁贴、快捷方式和小部件在系统临时恢复组件时重新出现在发现列表。
- 新增开机/解锁/应用更新自动校正：启动后进行延迟检查和稳定后复查，只对实际已恢复的受保护组件重新执行 Root 禁用，同时记录校正结果到诊断包。
- 修复多入口 LSPosed/libxposed 模块不支持热重载时仍尝试热重载的问题，改为使用兼容的更新路径，降低模块运行时异常风险。
- 完善 Release 发布链路：自动补全完整 changelog，并确保 LSPosed 同步在完整发布说明生成后执行。
- 改进 Telegram 发布流程，包括单条消息发布、可展开 changelog、链接处理、旧发布消息替换以及中英文检查一致性。
- 本版本包含 v1.6.9 之后的全部改动；正式 Release 继续使用固定签名证书、R8 优化、zipalign、SHA-256 与 APK 身份/版本校验。

建议从旧 Debug 版本升级前先导出规则备份；正式 Release 应继续使用固定 Release 签名安装升级。

---

## English

- Added bulk-operation locks across Rules, Ordering, and Components. Full/partial locks can be filtered with Locked; protected entries skip Select All/Invert-style operations but remain manually editable, and locks never participate in ordering or alter selected/partial/unselected state.
- Strengthened persistent Root component protection: real `pm disable` remains in place, desired disabled state is persisted, and an LSPosed/system_server discovery filter keeps protected tiles, shortcut entries, and widgets out of discovery results even if Android temporarily restores their components.
- Added automatic reconciliation after boot, unlock, and app updates. A delayed pass plus a settled-startup recheck only re-disables protected components whose real state was restored, and reconciliation results are included in diagnostics.
- Fixed unsupported hot reload attempts for multi-entry LSPosed/libxposed modules by using a compatible update path, reducing runtime failure risk.
- Improved the Release pipeline with automatic complete changelog generation and ensured LSPosed synchronization happens after the finalized release notes are available.
- Improved Telegram publishing with single-message releases, expandable changelogs, corrected links, stale-post replacement, and aligned bilingual validation.
- This release includes all changes after v1.6.9. Production releases continue to use the pinned signing certificate, R8 optimization, zipalign, SHA-256, and APK identity/version verification.

Export a rule backup before replacing older Debug builds. Production Release upgrades should continue using the fixed Release signing certificate.
