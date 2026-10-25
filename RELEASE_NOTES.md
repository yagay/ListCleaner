# 列表清理 / List Cleaner 1.6.46

版本码 / Version code: 71
Xposed Hook compatibility: 70
上一正式版 / Previous stable release: v1.6.18

## 中文

- **系统入口扩展**：新增并完善长按快捷方式、Storage Access Framework 存储位置、Direct Share、发送到、打开方式、浏览器/深度链接及多种系统管理入口的候选发现、规则过滤和排序；无法验证的厂商入口优先安全放行。
- **更准确的系统真实列表**：系统预览使用实际 API、RoleController、PackageManager、Settings 与相关服务的最终权威结果；区分真实已生效候选、诊断候选和历史记录，修复虚假条目与过期快照。
- **组件清理和保护**：完善桌面入口、磁贴、快捷方式创建入口和小组件的 Root 组件管理、状态回读、重协调保护及扫描缓存刷新，尽量避免显示已经失效的组件。
- **Android 多空间**：规则、优先排序、组件状态及批量锁按用户空间隔离；空间切换入口更清晰，支持多空间备份与导入时逐项映射目标空间。
- **分享与快捷方式**：改进 Direct Share 和 Launcher Shortcut 的发现、回传及过滤；提供主动刷新和候选探测能力，同时避免记录联系人、会话内容。
- **运行时与兼容性**：强化 libxposed API 102 的授权、作用域、Hook 生命周期与分用户路由；统一 Runtime Probe 配置、系统真实结果验证和失败时安全降级，改善稳定性与性能。
- **全面扁平化 Material 3**：统一规则、组件、排序、设置与弹出菜单的列表结构、图标、触摸区域、分隔线和交互；统一应用与组件长按的三点菜单。
- **高亮和导航**：应用展开、组件选中和分类选择统一使用主题高亮；精简底部导航栏，去掉选中横线；支持浅色/深色沉浸式系统栏。
- **外观及设置**：新增主题模式、颜色和尺寸等集中配置，统一设置控件；新增“关于”页、GitHub/Telegram 项目入口及多空间展开箭头。
- **备份、诊断与体验**：完善多空间备份、系统预览、扫描记录、自动刷新和健康提示；修复异步候选发布、切换、菜单和页面状态的多个问题。
- **构建与测试**：整合 Debug arm64 单包构建与 Material 3、翻译、双语一致性及回归测试；Release 继续使用固定证书签名和人工触发发布。

本版本汇总自 v1.6.18 以来的提交。部分系统入口仍受 Android/OEM 权限及具体版本限制，实际预览和过滤结果以设备运行时验证为准。

---

## English

- **More Android entry points:** Expanded discovery, filtering and ordering for long-press shortcuts, Storage Access Framework locations, Direct Share, Send To, Open With, browser/deep links and system-managed entry surfaces; unknown OEM results remain fail-open where necessary.
- **Authoritative system previews:** Prefer verified post-filter results from actual Android APIs, RoleController, PackageManager, Settings and system services; separate active candidates from diagnostics and history, and reconcile stale snapshots.
- **Root component controls:** Improved launcher entries, Quick Settings tiles, shortcut-creation activities and widget discovery; added stronger read-back, reconciliation and cache refresh behavior.
- **Android multi-user spaces:** Isolate rules, priority ordering, component state and bulk-operation locks per user; support multi-space export and explicit import destination mapping.
- **Share and shortcut reliability:** Better Direct Share and Launcher Shortcut observation, proactive discovery, refresh and verification without collecting contact or conversation contents.
- **Runtime compatibility:** Strengthened libxposed API 102 scopes, authorization, lifecycle, per-user routing, atomic Runtime Probe publication, validation and safe fallback.
- **Unified flat Material 3 UI:** Consistent Cleanup, Components, Ordering, Settings and popups, with shared rows, spacing, icons, touch targets, menus and app/component long-press actions.
- **Selection and navigation:** Theme-aware highlighting for expanded apps, selected components and category choices; compact bottom navigation without underline and immersive light/dark system bars.
- **Appearance and About:** Consolidated theme, color and sizing controls, an About screen with GitHub/Telegram links, and a clearer multi-space selector.
- **Backup, diagnostics and stability:** Multi-space backup, more reliable real-system previews, scan records, refresh policy, improved concurrency and state handling.
- **Release quality:** Preserve arm64-only Debug CI and localization, Material 3 and regression checks; stable Releases remain manually published with the pinned signing certificate.

This release consolidates changes since v1.6.18. Availability and behavior of system-managed entries can vary by Android/OEM version and permission; verify the effective results on-device.
