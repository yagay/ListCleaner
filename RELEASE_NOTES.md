# 列表清理 / List Cleaner 1.6.9

版本码 / Version code: 34

## 中文

- 修复组件禁用状态不能稳定保持的问题，持久化期望的组件状态，并增加状态校验与保护，避免组件被系统或后续刷新意外恢复。
- 修复组件状态校验器的注册与导入问题，进一步提高磁贴、快捷方式、小部件等 Root 组件管理的可靠性。
- 修复多入口 LSPosed/libxposed 模块不支持热重载时仍尝试热重载的问题，改为使用兼容的更新路径，降低模块运行时异常风险。
- 完善 Release 发布链路：自动补全完整 changelog，并确保 LSPosed 同步在完整发布说明生成后执行。
- 改进 Telegram 发布流程，包括单条消息发布、可展开 changelog、链接处理、旧发布消息替换以及中英文检查一致性。
- 本版本包含 v1.6.8 之后的全部改动；正式 Release 继续使用固定签名证书、R8 优化、zipalign、SHA-256 与 APK 身份/版本校验。

建议从旧 Debug 版本升级前先导出规则备份；正式 Release 应继续使用固定 Release 签名安装升级。

---

## English

- Fixed disabled component states not remaining persistent. Desired component states are now stored and guarded with validation to reduce unintended restoration after refreshes or system-side changes.
- Fixed component-state validator registration/import issues to improve the reliability of Root component management for tiles, shortcuts, widgets, and related entries.
- Fixed unsupported hot reload attempts for multi-entry LSPosed/libxposed modules by using a compatible update path, reducing runtime failure risk.
- Improved the Release pipeline with automatic complete changelog generation and ensured LSPosed synchronization happens after the finalized release notes are available.
- Improved Telegram publishing with single-message releases, expandable changelogs, corrected links, stale-post replacement, and aligned bilingual validation.
- This release includes all changes after v1.6.8. Production releases continue to use the pinned signing certificate, R8 optimization, zipalign, SHA-256, and APK identity/version verification.

Export a rule backup before replacing older Debug builds. Production Release upgrades should continue using the fixed Release signing certificate.
