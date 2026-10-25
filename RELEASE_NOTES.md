# 列表清理 / List Cleaner 1.6.46

版本码 / Version code: 71
Xposed Hook compatibility: 70
上一正式版 / Previous stable release: v1.6.18

## 中文

- **系统入口扩展**：新增并完善长按快捷方式、Storage Access Framework 存储位置、Direct Share、发送到、打开方式、浏览器/深度链接及多种系统管理入口的候选发现、规则过滤和排序；无法验证的厂商入口优先安全放行。
- **系统预览与候选来源**：依据 Android 可访问的实际 API、RoleController、PackageManager、Settings 与相关服务查询，明确区分已启用项目、API 候选、Hook 返回和历史观察；不再将未验证的候选冒充最终系统列表，并修复过期快照。
- **组件清理和保护**：完善桌面入口、磁贴、快捷方式创建入口和小组件的 Root 组件管理、状态回读、重协调保护及扫描缓存刷新，尽量避免显示已经失效的组件。
- **Android 多空间**：规则、优先排序、组件状态及批量锁按用户空间隔离；空间切换入口更清晰，支持多空间备份与导入时逐项映射目标空间。
- **分享与快捷方式**：改进 Direct Share 和 Launcher Shortcut 的发现、回传及过滤；提供主动刷新和候选探测能力，同时避免记录联系人、会话内容。
- **运行时与兼容性**：强化 libxposed API 102 的授权、作用域、Hook 生命周期与分用户路由；统一 Runtime Probe 配置、系统真实结果验证和失败时安全降级，改善稳定性与性能。
- **全面扁平化 Material 3**：统一规则、组件、排序、设置与弹出菜单的列表结构、图标、触摸区域、分隔线和交互；统一应用与组件长按的三点菜单。
- **高亮和导航**：应用展开、组件选中和分类选择统一使用主题高亮；精简底部导航栏，去掉选中横线；支持浅色/深色沉浸式系统栏。
- **外观及设置**：新增主题模式、颜色和尺寸等集中配置，统一设置控件；新增“关于”页、GitHub/Telegram 项目入口及多空间展开箭头。
- **操作确认与安全锁**：规则、排序及 Root 组件修改统一采用待应用草稿、放弃/应用和确认流程；应用与组件行通过三点菜单或长按管理操作锁，已锁定子项需先解锁才可修改。
- **备份、诊断与体验**：完善 v13 多空间备份和显式导入空间映射、系统预览、扫描记录、自动刷新与健康提示；更新应用内中英文功能说明及外部文档。Root 禁用组件记录仅供备份参考，不会随规则导入自动执行 Root 命令。
- **构建与测试**：Debug 保留 arm64 单包与 Material 3、翻译、双语一致性及回归检查；Release 构建包含依赖提供的所有 ABI，以固定证书签名，并且只能手动启动正式发布。

本版本汇总自 v1.6.18 以来的全部开发提交。完整逐条记录见 [提交归档](https://github.com/yagay/ListCleaner/blob/v1.6.46/docs/releases/v1.6.46-commits.md) 与 [GitHub 对比](https://github.com/yagay/ListCleaner/compare/v1.6.18...main)。部分系统入口仍受 Android/OEM 权限及具体版本限制，实际预览和过滤结果以设备运行时验证为准。

---

## English

- **More Android entry points:** Expanded discovery, filtering and ordering for long-press shortcuts, Storage Access Framework locations, Direct Share, Send To, Open With, browser/deep links and system-managed entry surfaces; unknown OEM results remain fail-open where necessary.
- **System previews and candidate evidence:** Use accessible Android APIs, RoleController, PackageManager, Settings and system services; distinguish enabled services, API candidates, Hook returns and historical observations. Avoid presenting unverified candidates as the final rendered system list and reconcile stale snapshots.
- **Root component controls:** Improved launcher entries, Quick Settings tiles, shortcut-creation activities and widget discovery; added stronger read-back, reconciliation and cache refresh behavior.
- **Android multi-user spaces:** Isolate rules, priority ordering, component state and bulk-operation locks per user; support multi-space export and explicit import destination mapping.
- **Share and shortcut reliability:** Better Direct Share and Launcher Shortcut observation, proactive discovery, refresh and verification without collecting contact or conversation contents.
- **Runtime compatibility:** Strengthened libxposed API 102 scopes, authorization, lifecycle, per-user routing, atomic Runtime Probe publication, validation and safe fallback.
- **Unified flat Material 3 UI:** Consistent Cleanup, Components, Ordering, Settings and popups, with shared rows, spacing, icons, touch targets, menus and app/component long-press actions.
- **Selection and navigation:** Theme-aware highlighting for expanded apps, selected components and category choices; compact bottom navigation without underline and immersive light/dark system bars.
- **Appearance and About:** Consolidated theme, color and sizing controls, an About screen with GitHub/Telegram links, and a clearer multi-space selector.
- **Confirmation and safety locks:** Rule, ordering and Root component changes use staged drafts with Discard/Apply confirmation. App and component long-press or three-dot menus manage locks; locked child entries must be unlocked before editing.
- **Backup, diagnostics and documentation:** Improved v13 multi-space backups with explicit space mapping, previews, scan records and refresh. Updated bilingual in-app help and project guides. Root-disabled component records remain informational and are not replayed on import.
- **Release quality:** Preserve arm64-only Debug CI plus localization, Material 3 and regression checks. Release uses all ABIs supplied by dependencies and the pinned signing certificate; stable publication remains manual.

This release consolidates all development commits since v1.6.18. See the [complete commit archive](https://github.com/yagay/ListCleaner/blob/v1.6.46/docs/releases/v1.6.46-commits.md) and [GitHub comparison](https://github.com/yagay/ListCleaner/compare/v1.6.18...main). Availability and behavior of system-managed entries can vary by Android/OEM version and permission; verify the effective results on-device.
