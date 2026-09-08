# List Cleaner 中英文对应规则

**简体中文** | [English](LOCALIZATION.en.md)

List Cleaner 固定维护中文和英文两套内容。所有供用户、维护者或问题排查人员阅读的项目自有文字，都必须同时存在语义对应的中文和英文版本；不能只更新其中一种语言。

## 基本原则

- `values/`：默认英文资源。
- `values-zh/`：对应中文资源。
- `res/xml/locales_config.xml`：只声明当前实际维护的语言。
- Compose 用户界面使用 `stringResource(...)`；非 Compose 用户提示使用 `Context.getString(...)`。
- 新增、删除或改变用户可见资源时，中英文必须同时处理，格式占位符必须一致。
- 中文是项目说明的默认展示语言，英文内容必须与中文的功能点、警告、限制和操作步骤对应。

## 必须中英文对应的内容

包括但不限于：

- App 界面、Toast、错误、状态、警告、对话框和可访问性说明。
- GitHub `README` 及项目说明。
- LSPosed `README`、`SUMMARY` 和相关模块说明。
- Release Notes、GitHub Release 和 Telegram 发布内容。
- 使用、开发、发布、本地化等维护文档。
- List Cleaner 自己生成的诊断说明、分析摘要、警告和可读状态解释。
- 后续新增的其他项目自有、面向人阅读的文字。

对应表示信息和结构等价，不要求逐字直译。不能出现中文完整而英文只有摘要，或英文新增重要限制而中文遗漏的情况。

## 不翻译的机器数据

以下内容保持稳定和语言无关，不复制成中英文两份：

- 包名、类名、组件名、MIME、URI scheme、Intent Action、协议 token。
- 配置 key、序列化 enum、规则 ID、诊断机器字段名和固定 ZIP 路径。
- Android、LSPosed、Root、shell、logcat、dumpsys 等外部系统产生的原始输出。
- 用户自己输入的名称和第三方应用提供的原始应用名称。

这些数据用于程序解析和排障，翻译会降低可比较性或破坏工具兼容性。项目自有的解释性说明应另行提供中英文对应版本。

## 诊断与日志

诊断 ZIP 的目录和机器字段保持稳定。List Cleaner 自己写入的人类可读说明应提供中英文对应内容，例如中文和英文 README/说明文件；原始 logcat、LSPosed 日志和系统命令输出保持原样。

运行时日志优先使用稳定事件/字段和值表达事实；如果加入供人阅读的解释或建议，必须同时提供中文和英文对应解释，不能让翻译改变用于检索和比较的机器标识。

## 自动检查

`tools/check-source-localization.py` 防止生产 Kotlin/Java 重新出现无法本地化的用户可见硬编码文字。

`tools/check-translation-resources.py` 检查 Android 资源 XML、重复/未知 key、格式占位符、`translatable="false"` 覆盖和 locale 声明。

项目还应通过中英文对应性检查，覆盖成对文档、LSPosed 文档结构和诊断说明。任何一侧缺失或结构明显不对应都应在 CI 中失败，而不是等发布后人工发现。

## 维护规则

每次修改文字时，把中文和英文视为同一个改动：先确定信息内容，再同步更新两边。正式发布前，中英文 README、LSPosed 说明、发布说明、Telegram 文案以及诊断说明必须保持对应。
