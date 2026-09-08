# 列表清理 / List Cleaner 1.6.8

版本码 / Version code: 33

## 中文

- 本版本汇总自上一个正式版本 v1.6.7 之后的全部实际改动，而不只包含最后一轮性能优化。
- 持续完善“打开方式”分类、继承和排序逻辑，包括通用 OPEN 与分类型配置、实际文件效果预览、selector / MIME / 文件后缀识别，以及相关回归覆盖。
- 加强应用隐藏与来源应用兼容，继续修复 ES 等文件管理器场景，统一全选 / 半选 / 未选的筛选、排序和包级隐藏行为。
- 改进 Root 组件管理，包括磁贴、快捷方式、小部件的状态读取、历史筛选、批量操作和修改后局部刷新，并保留串行 Root 操作和结果确认。
- 重构并加强 LSPosed / libxposed 102 运行时状态管理，引入 ServiceSession、ModuleRuntimeController、RuntimeCapabilities、PackageIdentity 等结构，改善重连、热更新、目标版本校验、ACK 和能力判断。
- 继续强化 system_server Hook 安全性与兼容性，保留 caller UID、package visibility、Resolver/Chooser、空结果恢复和 fail-open 等保护，同时增加实际 Hook 命中统计。
- 改进远程配置与备份：配置写入和 revision 更一致，旧 DefaultOpenConfig / TileConfig 逐步退出新配置路径，备份继续保持旧版本导入兼容。
- 完成诊断体系整理，减少敏感文件名/URI 暴露，补充运行时、Hook、Root 和能力信息，并缩减重复或低价值日志。
- 完成本地化结构整理，拆分中英文资源，加入 README.en、发布/本地化文档，并在 CI 中加入源码本地化、翻译资源和双语一致性检查。
- 完成一轮完整性能优化：缓存 system_server 反射访问器、减少 RemotePreferences 兜底轮询、加入 session + revision 同步 fast-path、复用候选和图标缓存、Scope 列表懒加载图标、Root 局部刷新，以及列表分组/搜索缓存与 120 ms debounce。
- 改进 Debug / Release / LSPosed / Telegram 发布流程和校验，正式 Release 继续使用固定签名证书、R8 优化、签名校验、zipalign、SHA-256 与发布产物检查。
- 本次 Release 后附“完整变更 / Full changelog”，范围固定为 v1.6.7 → v1.6.8，列出该区间内的全部非 merge commit，避免遗漏中间修改。

建议从旧 Debug 版本升级前先导出规则备份；正式 Release 应继续使用固定签名证书安装升级。

---

## English

- This release now summarizes the full set of real changes since the previous stable release v1.6.7, not only the final performance pass.
- Continued improving Open-with classification, inheritance, ordering, real-file effect preview, selector/MIME/file-extension detection, and regression coverage for generic and type-specific OPEN behavior.
- Strengthened app-hiding and source-app compatibility, including file-manager cases such as ES, with more consistent full/partial/unselected filtering, ordering, and package-level hiding behavior.
- Improved Root component management for tiles, shortcuts, and widgets, including state reads, history filtering, batch operations, and local refresh after mutations while preserving sequential Root execution and state verification.
- Refactored and hardened libxposed 102 runtime management with ServiceSession, ModuleRuntimeController, RuntimeCapabilities, PackageIdentity, stronger reconnect handling, hot reload, target-version checks, ACK handling, and capability detection.
- Continued hardening system_server hooks while preserving caller-UID checks, package-visibility protections, Resolver/Chooser handling, empty-result recovery, and fail-open safety; actual hook-hit counters were also added.
- Improved remote configuration and backups with more consistent revisions and writes, removal of legacy DefaultOpenConfig/TileConfig from new configuration paths, and continued backward-compatible backup imports.
- Reworked diagnostics to reduce exposure of file names and URIs, add runtime/hook/root/capability information, and remove redundant or low-value logging.
- Reorganized localization into split English/Chinese resources, added English README and release/localization documentation, and added CI checks for source localization, translation resources, and bilingual parity.
- Completed a full performance pass: cached system_server reflection accessors, reduced RemotePreferences fallback polling, added a session + revision sync fast path, reused candidate/icon caches, lazy-loaded Scope icons, locally refreshed Root mutations, and cached list grouping/search work with a 120 ms debounce.
- Improved Debug, Release, LSPosed, and Telegram publication workflows and validation. Production releases continue to use the pinned signing certificate, R8 optimization, signature verification, zipalign, SHA-256, and artifact checks.
- The Release now appends a complete changelog for v1.6.7 → v1.6.8, listing every non-merge commit in that range so intermediate changes are not omitted.

Export a rule backup before replacing older Debug builds. Production Release upgrades should continue using the fixed release signing certificate.
