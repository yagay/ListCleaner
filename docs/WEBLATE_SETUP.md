# Weblate 接入配置

这份文档用于维护者创建 List Cleaner 的 Weblate 项目。社区翻译说明见 [TRANSLATING.md](TRANSLATING.md)。

## 推荐接入方式

Hosted Weblate 使用 GitHub App 连接 `yagay/ListCleaner`，让 Weblate 负责读取仓库、接收更新，并通过翻译分支/PR 回写。不要在仓库里保存 Weblate Token、GitHub Token 或其他凭据。

建议设置：

- Project：`List Cleaner`
- Source language：English
- Repository branch：`main`
- File format：`Android String Resource`
- Translation workflow：优先通过 GitHub Pull Request 回写，避免翻译机器人直接改 `main`

## 组件发现

List Cleaner 把 Android 文案拆成多个 `strings*.xml`。不需要手工建立每个组件，可以在 Weblate 的 Component discovery 中从默认英文资源自动发现。

建议配置：

```text
Regular expression:
app/src/main/res/values/(?P<component>strings(?:_[^/]+)?)\.xml

Component name:
Android · {{ component }}

Monolingual base language file:
app/src/main/res/values/{{ component }}.xml

File mask:
app/src/main/res/values-*/{{ component }}.xml

File format:
Android String Resource
```

这会覆盖 `strings.xml`、`strings_core.xml`、`strings_ui.xml`、`strings_root.xml`、`strings_scope.xml` 等现有资源文件，也能在以后新增同样命名规则的文件时继续发现组件。

## 新语言

Weblate 创建的新翻译应落在标准 Android 目录，例如：

```text
app/src/main/res/values-ja/strings_core.xml
app/src/main/res/values-de/strings_core.xml
```

同时必须把语言加入：

```text
app/src/main/res/xml/locales_config.xml
```

仓库 CI 会检查语言目录与 `locales_config.xml` 是否一致。区域语言在启用前应确认 Android qualifier 与 `locales_config.xml` 的 BCP-47 标签正确对应。

## 合并策略

建议 Weblate 把翻译提交到独立分支并创建 PR。PR 合并前保留 Debug CI 为必需检查。当前 CI 会验证：

- 用户可见硬编码字符串
- 翻译 XML
- 未知与重复 key
- 格式占位符
- locale 声明
- Debug APK 编译
- 单元测试

不要让 Weblate 修改版本号、Release Notes、签名配置或 Release 工作流。正式发布仍由现有手动 Release 流程控制。

## 建立正式入口后

Weblate 项目实际创建并得到固定 URL 后，再做以下修改：

1. 在 `docs/TRANSLATING.md` 加入正式“开始翻译”链接。
2. 在 `README.md` 和 `README.en.md` 加入 Weblate 翻译入口/徽章。
3. 如需要，在 App 内增加“帮助翻译 / Help translate”入口，直接打开正式 Weblate 项目。

在 URL 尚未实际存在前，不写占位 Weblate 地址。
