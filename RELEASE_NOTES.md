# 列表清理 / List Cleaner 1.6.13

版本码 / Version code: 38

## 中文

- 浏览器分类新增域名专用规则，可在“浏览器”下面直接添加 github.com 等域名。
- 域名规则继承“浏览器 · 全部”，并可额外单独隐藏候选应用；不会禁用应用，也不会影响该应用处理其他网址。
- 域名规则复用现有列表逻辑：搜索、用户/系统应用筛选、已选/未选/已锁定、批量操作锁、应用聚合和组件展开保持一致。
- 排序页同步支持域名专用优先顺序、长按拖动、上移/下移、恢复继承以及批量操作锁。
- 候选扫描会针对已配置域名实际查询 http/https 处理器，以便发现 GitHub、YouTube 等只声明特定 App Link 域名的应用。
- LSPosed 运行时会按实际 URL host 应用域名规则，并扩展 system_server 内部候选查询 Hook，以覆盖验证 App Link 直接解析路径。
- 规则备份升级为 v10，新增浏览器域名、域名专用规则和域名专用排序；仍兼容导入 v1-v9。
- Hook 兼容版本提升到 38，因此本次更新需要让 system_server / Intent Resolver 加载新版 Hook。

---

## English

- Added per-domain rules directly under Browser, so hosts such as github.com can be configured without creating a separate top-level page.
- Domain rules inherit Browser · All and can add extra hidden handlers without disabling the app or affecting unrelated web domains.
- Domain views reuse the existing list behavior: search, user/system filtering, selected/unselected/locked views, bulk-operation locks, app grouping, and component expansion.
- Ordering now supports domain-specific priority lists with drag reorder, move up/down, inheritance restore, and the same bulk locks.
- Candidate discovery probes configured http/https hosts so apps that only declare specific App Link domains can be discovered.
- The LSPosed runtime applies rules using the real URL host and also hooks internal system_server candidate queries to cover verified App Link direct-resolution paths.
- Backup format is now v10 and includes configured browser domains, per-domain rules, and per-domain ordering while remaining compatible with v1-v9 imports.
- Hook compatibility generation is now 38, so system_server / Intent Resolver must load the updated hook for this feature.

