# 列表清理 / List Cleaner 1.6.15

版本码 / Version code: 40

## 中文

- 新增“深度链接”独立分类，与“浏览器”真正分开：Chrome、Firefox、YBrowser 等通用网页处理器继续归“浏览器”，GitHub、YouTube 等特定域名 App Link 归“深度链接”。
- 深度链接完整复用现有规则体系：应用筛选、查看筛选、搜索、锁定、多选、排序、全选、全不选、反选等均与其他分类统一。
- 域名规则只属于“深度链接”，不再和普通浏览器规则混用；旧版按域名保存的 Browser 规则、自定义标题和子项锁会自动兼容迁移。
- 新增 Android App Link 域名自动发现，不需要先手动添加域名；自动发现的域名只有在实际扫描到至少一个可用 Deep Link App 时才显示，避免空域名。
- 保留手动添加/管理域名；已手动配置的域名即使当前没有候选 App 也继续显示，方便后续管理或删除。
- 深度链接域名下拉新增实时搜索；弹出菜单默认以“域名”筛选按钮为中心展开，屏幕空间不足时再自动调整位置。
- 优化域名弹出菜单密度：全部、管理域名、搜索框外部留白、空结果提示和域名列表统一紧凑；域名字体、搜索文字和搜索图标保持正常大小。
- “选择”菜单新增“全不选”，与“全选 / 反选”一起遵守当前过滤范围和锁定规则；被锁定项目不会被批量取消。
- 修复 APK 已更新但旧 Hook 仍在运行时，管理 App 启动阶段可能直接退出的问题：现在会正常进入应用并提示“需要重启手机”，不再在启动阶段自动热重载旧 Hook。
- 优化 Hook 兼容代数：本版本 APK versionCode 为 40，但 Hook compatibility 继续保持 39；本次 UI/管理侧修复本身不会额外制造新的 Hook 重启要求。
- Release 工作流调整为：版本号升级即视为正式发布，执行优化 Release 编译、固定签名校验、zipalign、SHA-256、GitHub Release 发布并触发 LSPosed 同步。

---

## English

- Added a first-class Deep Links category that is genuinely separate from Browser. Generic web handlers such as Chrome, Firefox, and YBrowser remain Browser targets, while host-specific App Links such as GitHub and YouTube are classified as Deep Links.
- Deep Links now reuse the same rules UI and behavior as other categories: app filters, view filters, search, locks, multi-select, ordering, Select all, Select none, and Invert.
- Per-domain rules now belong only to Deep Links instead of being mixed with generic browser rules. Legacy per-domain Browser rules, custom titles, and child locks are migrated compatibly.
- Added automatic Android App Link domain discovery, so a host no longer has to be entered manually first. Auto-discovered hosts are shown only when at least one eligible Deep Link app is actually found, avoiding empty domains.
- Manual domain management remains available. Previously configured hosts stay visible even if no current candidate app is found, so they can still be managed or removed.
- Added live search to the Deep Link domain dropdown. The popup now prefers to open centered on the Domain filter button and only shifts when screen space requires it.
- Compacted the entire domain popup: All, Manage domains, search-field outer spacing, empty-state spacing, and domain rows now use tighter vertical spacing while keeping normal domain text, search text, and icon sizes.
- Added Select none next to Select all and Invert. Bulk deselection respects the current filter scope and lock protection, so locked entries are skipped.
- Fixed a startup failure when the APK is newer but an older hook generation is still running. The manager now opens normally and shows a restart-required prompt instead of attempting an automatic hot reload before the UI appears.
- The APK versionCode is now 40 while hook compatibility remains 39. These UI/manager-side changes do not create an additional hook restart requirement by themselves.
- Release automation now treats a version bump as a formal release: optimized Release compilation, pinned-signature verification, zipalign, SHA-256 generation, GitHub Release publication, and LSPosed sync dispatch.
