# List Cleaner 中英文对应规则

**简体中文** | [English](LOCALIZATION.en.md)

List Cleaner 固定维护中文和英文两套**用户相关内容**。重点是 App 用户能看到的文字，以及安装、使用、发布相关说明；开发者内部日志、CI 输出、源码注释和纯诊断机器数据不要求提供中文。

## 基本原则

- `values/`：默认英文用户资源。
- `values-zh/`：对应中文用户资源。
- `res/xml/locales_config.xml`：只声明当前实际维护的语言。
- Compose 用户界面使用 `stringResource(...)`；非 Compose 用户提示使用 `Context.getString(...)`。
- 新增、删除或改变用户可见资源时，中英文必须同时处理，格式占位符必须一致。
- 中文是项目说明的默认展示语言，英文说明应覆盖相同的功能点、警告、限制和操作步骤。

## 必须中英文对应

- App 界面、Toast、错误、状态、警告、对话框、按钮和可访问性说明。
- App 中直接展示给用户的扫描/Root/备份/恢复/诊断导出结果。
- GitHub `README` 和面向用户的使用说明。
- LSPosed `README`、`SUMMARY` 和模块说明。
- Release Notes、GitHub Release 和 Telegram 发布内容。
- 发布/安装/使用相关文档。
- 诊断 ZIP 中面向普通用户阅读的总说明，例如 `README.zh-CN.txt` / `README.en.txt`。

对应表示信息和结构等价，不要求逐字直译。不能出现中文完整而英文只有摘要，或英文新增重要警告而中文遗漏。

## 不要求中文

以下内容可以只使用英文或稳定机器标识：

- 开发者源码注释、测试代码、测试输出和 CI/Actions 日志。
- 内部异常文本、调试日志、Xposed/Root 事件名、诊断事件名和开发者分析报告。
- `logcat`、LSPosed 日志、shell、dumpsys、系统命令原始输出。
- 包名、类名、组件名、MIME、URI scheme、Intent Action、协议 token。
- 配置 key、序列化 enum、规则 ID、诊断机器字段名和固定 ZIP 路径。
- 用户自己输入的名称和第三方应用提供的原始名称。

这些内容主要用于开发和排障，强制翻译反而会降低检索、比较和自动解析的稳定性。

## 诊断原则

诊断 ZIP 保持两层：

1. 给普通用户看的总说明提供中英文对应版本。
2. 开发者使用的分析文件、机器字段和原始日志保持单一稳定格式，默认英文即可。

开发者诊断内容不应为了“中英文对应”复制成两份。只有当某段诊断结果直接显示在 App UI 中给用户看时，才必须走 Android 字符串资源。

## 自动检查

`tools/check-source-localization.py` 只针对真正用户可见的硬编码文字；开发者内部英文允许保留。

`tools/check-translation-resources.py` 检查 Android 用户资源 XML、重复/未知 key、格式占位符、`translatable="false"` 覆盖和 locale 声明。

`tools/check-bilingual-parity.py` 检查用户文档、LSPosed 说明、Release Notes、Telegram 发布链路和 App 中英文资源对应性，不强制开发者诊断报告双语化。

## 维护规则

判断一个文字是否需要中英文时，以“普通用户是否会直接看到”为准。用户能看到：中英文对应；只给开发者、CI、日志分析或机器解析使用：保持单一稳定英文/机器格式即可。
