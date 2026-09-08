# List Cleaner localization

List Cleaner uses Android locale resources plus `AppLanguage` as the compatibility bridge for legacy and dynamic text.

## Current behavior

- `zh` uses the Chinese UI.
- `en` is the default/fallback UI.
- Any language without its own resource override falls back to English.
- Compose UI, Root component messages, runtime/module status, dialogs, descriptions and accessibility text all pass through the same localization pipeline.

## Add a new language

1. Copy `tools/localization/values-xx-template.xml`.
2. Create `app/src/main/res/values-<language>/strings.xml` (for example `values-ja/strings.xml`).
3. Set `app_name` for the language.
4. Fill `locale_overrides` entries using the format:

   `Chinese source text|||Translated text`

5. Add the locale tag to `app/src/main/res/xml/locales_config.xml`.
6. Build Debug and run unit tests.

A missing translation is safe: the app automatically falls back to English.

## Rules

- Do not translate package names, component names, MIME values, URI schemes or user-defined names.
- Keep placeholders, numbers and technical tokens intact.
- Dynamic Root/runtime messages are translated through the same source-fragment mechanism, so translated fragments must preserve punctuation where it is part of the source key.
- New UI should prefer Android string resources when practical; legacy text is supported by the bridge so business logic does not need locale-specific branches.

## Resource layout

```text
app/src/main/res/
├── values/strings.xml       # English/default + bridge declaration
├── values-zh/strings.xml    # Chinese app name
├── values-ja/strings.xml    # example future language
└── xml/locales_config.xml   # locales exposed to Android
```
