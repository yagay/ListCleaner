# 列表清理 / List Cleaner 1.6.7

## 中文

- “打开方式”规则与排序全面支持按类型管理：内置 PDF、Office、APK、图片、视频、音频、文本、压缩包、scheme 等分类，并支持最多 8 个自定义 MIME / 文件后缀类型。
- “全部”与分类型配置加入继承语义：规则页显示“继承自全部 / 当前类型专用”，排序页支持继承通用 OPEN 排序并一键恢复继承。
- 新增实际文件最终效果预览，可查看识别类型、原始候选、预计最终候选、规则来源、排序位置和空列表保护结果。
- 加强 ES 等文件管理器的应用隐藏兼容：来源应用可单独配置，命中分类支持“全部 / 分享 / 多文件分享 / 打开方式 / 浏览器 / 文本处理”多选；仅完整勾选的应用整行参与包级隐藏，半勾选不加入。
- 规则页和组件页默认按“完整勾选 → 半勾选 → 未勾选”排序；应用隐藏列表按“已勾选 → 未勾选”排序，同组继续按应用名称排序。
- 修复 selector Intent 分类路径，并在 wildcard / opaque MIME 场景下增加受控文件后缀识别；具体未知 MIME 不会被后缀错误覆盖。
- 状态页新增 system_server 实际 Hook 命中统计；诊断日志不再记录完整文件名或 URI，仅保留安全扩展名和识别类型。
- Root 组件页新增“只看本应用修改”历史筛选；Root 操作和 OPEN 继承逻辑进一步拆分，降低 MainViewModel 职责。
- 旧 DefaultOpenConfig / TileConfig 不再进入新远程配置和新备份；备份格式升级并保持旧版本导入兼容。
- PR Debug 构建会上传 APK artifact；本版本同步补充自定义类型、继承、应用隐藏兼容和半勾选行为的回归测试。

旧 Debug 版签名可能不同；安装前请导出规则备份。

---

## English

- Open-with rules and ordering now support type-specific management across built-in PDF, Office, APK, image, video, audio, text, archive, scheme, and other categories, plus up to eight custom MIME/file-extension types.
- Generic “All” Open-with configuration and typed configurations now use inheritance semantics. The Rules page distinguishes inherited and type-specific entries, while Ordering can inherit generic OPEN order and restore inheritance in one step.
- Added final-effect preview for real files, including detected type, original candidates, expected final candidates, rule source, ordering position, and empty-list protection results.
- Improved package-visibility compatibility for file managers such as ES. Source apps can be configured independently, match categories support multi-select across All / Share / Multi-share / Open with / Browser / Text processing, and only fully selected app rows contribute to package-level hiding; partially selected rows do not.
- Rules and Components now sort by fully selected → partially selected → unselected by default. App visibility sorts selected → unselected, with app-name ordering inside each group.
- Fixed selector Intent classification and added controlled file-extension recognition for wildcard/opaque MIME cases. A specific unknown MIME is no longer incorrectly overridden by its extension.
- The Status page now reports actual system_server hook hit counts. Diagnostic logs no longer record full file names or URIs and retain only safe extensions and detected types.
- The Root Components page added a “modified by this app only” history filter. Root operations and OPEN inheritance logic were further split to reduce MainViewModel responsibilities.
- Legacy DefaultOpenConfig / TileConfig no longer enter new remote configurations or new backups. The backup format was upgraded while retaining compatibility with older imports.
- PR Debug builds upload an APK artifact. This release also adds regression coverage for custom types, inheritance, app-visibility compatibility, and partial-selection behavior.

Older Debug builds may use a different signing certificate. Export your rule backup before replacing or uninstalling them.
