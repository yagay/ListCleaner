# List Cleaner 翻译与本地化

**简体中文** | [English](LOCALIZATION.en.md)

List Cleaner 使用标准 Android 资源系统。所有运行时用户可见文字都应放在 Android 字符串资源中，这样既能使用 Android 原生本地化，也能直接接入 Weblate 等社区翻译平台，而不需要改动 App 的运行时语言架构。

## 当前结构

- `values/`：默认英文源语言，同时作为缺失翻译时的回退语言。
- `values-zh/`：中文翻译。
- `res/xml/locales_config.xml`：声明 Android“应用语言”设置里可选择的语言。
- Compose 界面使用 `stringResource(...)`。
- 非 Compose 的用户提示使用 `Context.getString(...)`。
- 包名、组件名、MIME、URI scheme、协议值和诊断机器字段等技术内容不翻译。

## 添加新语言

1. 创建 `app/src/main/res/values-<语言>/`，例如日语使用 `values-ja/`。
2. 从 `app/src/main/res/values/` 复制需要翻译的 XML 资源文件。
3. 只修改字符串内容，不修改资源名，也不要改变 `%1$s`、`%2$d` 等格式占位符。
4. 在 `app/src/main/res/xml/locales_config.xml` 加入对应 locale。
5. 运行 `python3 tools/check-translation-resources.py`，并通过 Debug 工作流。

允许提交“未完成”的新语言。缺失的资源会自动回退到默认英文，所以社区贡献者不需要一次翻译完整个 App。

## 自动检查

`tools/check-source-localization.py` 用于防止生产 Kotlin/Java 源码重新出现无法翻译的用户可见硬编码文本。

`tools/check-translation-resources.py` 专门检查社区翻译资源，包括：XML 解析、重复资源、未知 key、格式占位符一致性、`translatable="false"` 误覆盖、语言目录是否已声明，以及每种语言的翻译覆盖率。

缺少部分 key 只会显示覆盖率，不会导致检查失败，方便多人逐步完成一个语言。

## 翻译规则

- 必须完整保留 Android 格式占位符。
- 不翻译包名、类名/组件名、MIME、URI scheme、协议 token 和用户自己输入的名称。
- 不要把翻译后的文字保存进序列化 enum、配置数据或规则文件。
- Compose 之外的用户提示应通过 Android 资源获取。
- 诊断 ZIP 中供程序解析的机器字段应保持稳定，不随语言变化。

## 社区翻译平台

当前资源结构已经按标准 Android `values/values-xx` 组织，可以直接接入 Weblate 等平台。翻译平台只负责编辑资源文件并提交回 GitHub；App 本身不需要联网翻译，也不会增加运行时翻译 API、网络权限或第三方 SDK。

正式接入翻译平台后，建议让平台以 `app/src/main/res/values/*.xml` 作为英文源文本，并把各语言写回对应 `values-xx/` 目录。GitHub PR 或同步提交仍必须通过上述两个本地化检查和 Debug 构建。
