# List Cleaner localization

[简体中文](LOCALIZATION.md) | **English**

List Cleaner uses the standard Android resource system. Runtime UI strings must live in Android resources so they can be translated by Android tooling or a community translation platform.

## Current layout

- `values/` is the default English source/fallback set.
- `values-zh/` contains Chinese translations.
- `res/xml/locales_config.xml` declares languages exposed by Android per-app language settings.
- Compose UI uses `stringResource(...)`.
- Non-Compose user-facing messages use `Context.getString(...)`.
- Technical tokens such as package names, component names, MIME values, URI schemes and diagnostic machine fields are not translated.

## Add a language

1. Create `app/src/main/res/values-<language>/`, for example `values-ja/`.
2. Copy the translatable XML resources from `app/src/main/res/values/`.
3. Translate values without changing resource names or format placeholders such as `%1$s` and `%2$d`.
4. Add the locale tag to `app/src/main/res/xml/locales_config.xml`.
5. Run `python3 tools/check-translation-resources.py` and the Debug workflow.

Partial translations are allowed. Missing resources fall back to the default English values.

## Validation

`tools/check-source-localization.py` prevents hardcoded user-visible text from returning to production Kotlin/Java code.

`tools/check-translation-resources.py` validates community translation resources. It checks malformed XML, duplicate or unknown resource keys, format placeholder mismatches, `translatable=false` overrides, and locale declarations. It reports translation coverage but does not reject a language only because some keys are missing.

## Translation rules

- Preserve Android format placeholders exactly.
- Do not translate package names, class/component names, MIME values, URI schemes, protocol tokens or user-defined names.
- Do not put localized labels into serialized enums/configuration data.
- Use Android resources for user-facing errors outside Compose.
- Keep diagnostic-only machine fields language-neutral when localization would break tooling.

This structure is intended to remain compatible with community translation services such as Weblate without changing the app runtime localization architecture.
