# 列表清理 / List Cleaner 1.6.47

版本码 / Version code: 72
Xposed Hook compatibility: 72
上一正式版 / Previous stable release: v1.6.46 (2026-10-25)
版本范围 / Changes since: [v1.6.46...main](https://github.com/yagay/ListCleaner/compare/v1.6.46...main)

## 中文

- **新的控制与清理导航**：底部统一为 **控制 / 清理 / 设置**；清理页内部切换 **Entry / Component**。移除同一功能的重复独立页面，统一分类选择、应用列表、图标、过滤、长按菜单、确认修改与返回逻辑。Quick access 中的 Entry 与 Component 现在与真实目标一致。
- **控制主页与分类开关**：将模块状态入口放到控制主页，点击进入兼容性详情；清理管理集中展示入口和组件两组清理分类。支持搜索、启用/禁用筛选、全部启用/全部禁用与确认保存。“禁用清理”不会自动删除规则，也**不等于**真正禁用或恢复 Android 组件。
- **默认与独立清理方式**：默认清理方式移到“控制 → 清理管理 → 入口清理”顶部；各 Entry 分类可继承默认方式或单独设置“隐藏已选 / 只显示已选 / 全部显示”。清理方式与分类启用/禁用分别管理，多空间配置保持独立。
- **Entry 与 Component 混合常用分类**：控制主页可自由添加、移除和交错排序两类分类；添加弹窗与 Cleanup 共用分类图标及选中样式。默认显示四项，可展开全部；点击直接打开相应分类。置顶顺序属于本地界面设置，不纳入规则备份。
- **系统入口清理结构重整**：统一 Open、Share、Multi Share 等类型敏感入口的管理与 MIME/文件类型入口；整合打开文档、保存文档、内容选择、存储提供器及存储位置等相关列表，并将 Settings Apps 与 Notification Channels 纳入 Entry 一侧的共同分类管理。按分类清理只处理支持的系统候选或展示结果，不卸载应用或删除文件。
- **分享与快捷方式**：改善 Direct Share 的只读探测、MIME 类型处理、已观察与历史候选区分，以及 Launcher 长按快捷方式的发现和刷新；仅在对应系统返回路径中应用支持的过滤，不把推测候选当作实际菜单，也不保存联系人或会话正文。
- **系统预览与真实页面跳转**：改进眼睛预览、原生 Android Intent/Role/Settings 页面跳转与错误页回退；将 API 候选、运行时 Hook 返回、已启用服务和历史观察分开标识。支持基于选择情景验证，并区分已确认与估计结果；不保证厂商界面的最终显示与框架返回完全相同。
- **组件及通知管理**：完善 Launcher、磁贴、小部件、快捷方式创建入口、设备控制和账户认证器等 Root 组件的发现、操作保护、系统状态回读与持久策略校正；通知应用/频道规则统一进入应用列表与明细，保留独立规则、锁定和刷新。隐藏通知显示**不会**改变频道重要性或取消系统已投递通知。
- **稳定性与性能**：为易失败的 AOSP Hook 适配增加进程级熔断及安全回退，改进扫描、缓存、批量读取、页面切换和权限/多空间处理，降低异常 Hook 引发的重复崩溃和无效扫描风险。
- **界面、文档与测试**：统一扁平 Material 3 列表、分类图标和操作状态；更新 App 内中英文说明、README、LSPosed 文档、AOSP 与预览契约，并扩展分类、导航、混合常用项、系统预览和 Hook 回归测试。

### 升级说明

- 本版 Hook 兼容号由 **70 升为 72**。安装升级后如模块提示旧 Hook 仍在运行，请按 LSPosed/Root 管理器要求重启相关进程或设备并检查“控制 → 模块状态”。
- LSPosed 过滤与 Root 组件禁用是两种机制。**卸载 App 或清除数据不会自动重新启用已通过 Root 禁用的系统组件**；操作前建议备份并核对组件状态。
- 系统选择器、厂商定制菜单、服务权限与 Android 多空间可能影响实际结果；CI 编译通过不代表所有设备的系统菜单均已完成实机验证。
- 正式版使用固定发布签名；不同签名的 Debug APK 可能无法覆盖安装。新版本发布前请备份规则。

本次汇总自 **v1.6.46** 以来的 **1230** 个开发提交（其中 1229 个非合并提交）。[完整逐条记录](https://github.com/yagay/ListCleaner/blob/main/docs/releases/v1.6.47-commits.md) · [GitHub 全部提交对比](https://github.com/yagay/ListCleaner/compare/v1.6.46...main)。

---

## English

- **Controls/Cleanup navigation:** Bottom navigation is now **Controls / Cleanup / Settings**, with **Entry / Component** editors inside Cleanup. Consolidated duplicate screens and shared category pickers, app rows, icons, filters, long-press actions, draft confirmation and navigation behavior. Quick access labels now match their actual destinations.
- **Controls and cleanup switches:** Module status moved to the Controls home and opens compatibility details. Cleanup management groups Entry and Component categories with search, enabled/disabled filters, Enable all / Disable all and explicit confirmation. Disabling a cleanup category **does not** delete rules or directly disable/restore an Android component.
- **Default and per-category methods:** The default cleanup method appears at the top of Controls → Cleanup management → Entry cleanup. Each Entry kind may inherit or override Hide selected / Show selected only / Show all, independently from its cleanup enable/disable switch and other user spaces.
- **Mixed Entry/Component favorites:** Add, remove and interleave either category type on Controls. Favorite pickers reuse Cleanup icons and selection rows; long-press reorders or removes; the compact four-item preview can expand. Items open their precise category. UI pin ordering is not included in rule backups.
- **Entry cleanup consolidation:** Reworked Open, Share and Multi Share type-sensitive flows and MIME/file-type entry points. Consolidated document open/save/content selection, storage providers/roots and related lists. Settings Apps and Notification Channels now appear on the Entry side through unified category management. These cleanup rules do not uninstall apps or delete files.
- **Direct Share and launcher shortcuts:** Improved read-only discovery, MIME-aware handling, runtime-versus-history provenance, refresh and shortcut observation. Only supported system return paths are filtered; discovered candidates are not guaranteed to be in the final menu, and contact or conversation text is not stored.
- **Native previews and routing:** Improved eye previews, native Android Intent/Role/Settings destinations and safe fallbacks. Registered API candidates, enabled service lists, Hook returns and historical observations remain distinct; request-correlated evidence and estimates are not presented as guaranteed final OEM UI.
- **Root components and notifications:** Improved discovery and state read-back for launchers, tiles, widgets, shortcut-creation activities, device controls and account authenticators, including persistent-state reconciliation. Notification app/channel rules share grouped app/details, locks and refresh; hiding a shade row does **not** modify channel importance or cancel delivery.
- **Stability/performance:** Added per-process circuit breakers and safe fallback for failing AOSP Hook adapters; improved scanning, caching, batch discovery, navigation performance, permission handling and per-user routing.
- **UI, documentation and testing:** Unified flat Material 3 list controls and icons; refreshed bilingual in-app help, project/LSPosed documentation and system-preview contracts; expanded UI, navigation, mixed-favorite, preview and Hook regression coverage.

### Upgrade notes

- The Hook compatibility code increases from **70 to 72**. If an older Hook remains loaded after upgrading, restart the affected processes or device as instructed and review Controls → Module status.
- LSPosed candidate filtering and actual Root component disabling are separate. **Uninstalling the app or clearing its data does not re-enable components already disabled through Root.** Back up rules and review component states before replacing builds.
- Android/OEM chooser differences, privileges and user spaces affect results. Passing CI does not establish live-device compatibility for every system menu.
- Official releases use the pinned signing certificate; Debug builds signed differently may not update in place.

This summary covers **1230** development commits since **v1.6.46** (1229 non-merge commits). [Complete chronological commit archive](https://github.com/yagay/ListCleaner/blob/main/docs/releases/v1.6.47-commits.md) · [Full GitHub comparison](https://github.com/yagay/ListCleaner/compare/v1.6.46...main).
