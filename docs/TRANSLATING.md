# 帮助翻译 List Cleaner

List Cleaner 使用标准 Android 字符串资源。社区翻译不需要修改 Kotlin/Java 源码，也不需要了解 LSPosed 或 Root 实现。

## 当前方式

在 Weblate 项目正式建立前，可以直接通过 GitHub 提交翻译；Weblate 建立后，这份文档会继续作为翻译规则说明。

英文是默认回退语言，位于：

`app/src/main/res/values/`

简体中文位于：

`app/src/main/res/values-zh/`

新的语言使用标准 Android 资源目录，例如：

- 日语：`values-ja/`
- 德语：`values-de/`
- 法语：`values-fr/`

完成后还需要把语言标签加入：

`app/src/main/res/xml/locales_config.xml`

## 翻译时请保持不变

不要修改资源名，例如 `common_save`、`root_not_scanned`。

不要修改格式占位符，例如：

- `%1$s`
- `%2$d`
- `%1$d / %2$d`

不要翻译技术值，例如：

- Android 包名与组件类名
- MIME，例如 `application/pdf`、`image/*`
- URI scheme，例如 `mailto:`、`magnet:`
- Intent Action
- 用户自己输入的名称

如果某条翻译暂时没有完成，可以缺省该条资源；Android 会自动回退到英文，不需要复制英文占位。

## 自动检查

所有翻译提交都会经过 Debug CI。当前检查包括：

- XML 是否能正常解析
- 是否出现重复或不存在的资源 key
- Android 格式占位符是否与英文源文本一致
- `translatable="false"` 资源是否被错误覆盖
- 新语言目录是否在 `locales_config.xml` 声明
- APK 是否能正常编译
- 单元测试是否通过

本地可以运行：

```bash
python3 tools/check-translation-resources.py
python3 tools/check-source-localization.py
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:testDebugUnitTest
```

## Weblate

仓库已经按 Weblate 的 Android String Resource 格式准备。维护者配置说明见 [WEBLATE_SETUP.md](WEBLATE_SETUP.md)。

Weblate 项目地址建立后，会把正式的“开始翻译”入口补到这里和主 README；在此之前不会使用虚构或临时的 Weblate 地址。

---

## English

List Cleaner uses standard Android string resources. Translators do not need to edit Kotlin/Java code or understand the LSPosed/Root implementation.

English source strings live in `app/src/main/res/values/`. Add translations under the corresponding Android `values-<locale>/` directory and register the locale in `app/src/main/res/xml/locales_config.xml`.

Keep resource names and Android format placeholders such as `%1$s` and `%2$d` unchanged. Do not translate package names, component class names, MIME values, URI schemes, Intent actions, or user-defined names.

Partial translations are allowed; missing resources fall back to English. Translation pull requests are checked automatically for XML validity, unknown or duplicate keys, placeholder mismatches, locale registration, APK compilation, and unit tests.

Maintainer-side Weblate configuration is documented in [WEBLATE_SETUP.md](WEBLATE_SETUP.md).
