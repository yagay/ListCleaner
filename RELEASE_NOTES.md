# 列表清理 / List Cleaner 1.6.8

## 中文

- 完成一轮完整性能优化，重点降低 system_server Hook 热路径中的反射和重复计算开销。
- 为 package visibility 相关 Hook 缓存 getPackagesForUid、caller package 字段、packageName getter/field，以及 ParceledListSlice 访问器，减少高频反射扫描。
- RemotePreferences 在监听器正常工作时降低兜底轮询频率，减少 system_server 中无意义的配置读取。
- 运行时同步加入 session + revision fast-path；配置未变化时跳过重复 JSON 序列化、SHA-256、RemotePreferences 写入和 ACK probe，同时仍保留 target 版本校验。
- IntentCatalog 支持候选缓存；普通刷新复用扫描结果，手动刷新仍会强制完整扫描，自定义打开类型变化时自动失效。
- IntentCatalog 与 Root 组件图标缓存跨扫描复用，减少重复 Drawable/Bitmap 解码和 GC 压力。
- 应用可见性来源选择器改为按可见行懒加载图标，不再打开页面时一次性加载全部已安装应用图标。
- Root 组件批量修改后只刷新实际改变的组件，不再完整重扫磁贴、快捷方式和小部件。
- 规则列表预缓存应用分组和搜索规范化文本，搜索加入 120 ms debounce，减少连续输入时的全列表 groupBy/filter 开销。
- 修复候选缓存命中诊断信息可能持续增长的问题，并保留手动完整刷新和诊断能力。

建议从旧 Debug 版本升级前先导出规则备份；正式 Release 应继续使用固定签名证书安装升级。

---

## English

- Completed a full performance pass focused on reducing reflection and repeated work in system_server hook hot paths.
- Cached getPackagesForUid, caller-package fields, package-name getter/field accessors, and ParceledListSlice accessors used by package-visibility hooks.
- Reduced fallback RemotePreferences polling when the preference listener is healthy.
- Added a session + revision synchronization fast path. Unchanged configurations now skip repeated JSON serialization, SHA-256 hashing, RemotePreferences writes, and ACK probes while still validating loaded target versions.
- IntentCatalog now reuses candidate scan results for ordinary refreshes. Manual refresh still forces a full scan, and custom open-type changes invalidate the cache automatically.
- IntentCatalog and Root component icon caches now survive scans to reduce repeated Drawable/Bitmap decoding and GC pressure.
- The app-visibility source picker now lazy-loads icons only for visible rows instead of decoding every installed app icon on dialog open.
- Root component batch operations now refresh only components that were actually changed rather than rescanning tiles, shortcuts, and widgets.
- Rules list app grouping and normalized search text are cached, and search filtering uses a 120 ms debounce to reduce repeated full-list grouping/filtering while typing.
- Fixed unbounded growth in catalog cache-hit diagnostics while preserving manual full refresh and diagnostic behavior.

Export a rule backup before replacing older Debug builds. Production Release upgrades should continue using the fixed release signing certificate.
