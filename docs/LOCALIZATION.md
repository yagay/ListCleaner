# List Cleaner localization

List Cleaner uses the standard Android resource system only. Production Kotlin/Java code must not contain Chinese UI text or Chinese strings used as translation keys.

## Current behavior

- `values/` is the default English resource set and the fallback for missing translations.
- `values-zh/` contains the Chinese resource set.
- `res/xml/locales_config.xml` declares the languages exposed by Android's per-app language settings.
- Compose UI uses `stringResource(...)`.
- Application, ViewModel, repository, Root and scanner code use `Context.getString(...)` for user-facing dynamic messages.
- Domain objects expose stable state/enums instead of localized labels whenever the value is part of application logic.
- Internal diagnostics use stable language-neutral technical tokens when they are not user-facing prose.

The former `AppLanguage`, `LocaleText`, `UiText`, `locale_overrides` and localization startup provider compatibility layer has been removed.

## Add a new language

1. Create `app/src/main/res/values-<language>/` (for example `values-ja/`).
2. Copy the XML resource files from `app/src/main/res/values/` that contain translatable strings into the new directory.
3. Translate the string values without changing resource names or format placeholders such as `%1$s` and `%2$d`.
4. Add the locale tag to `app/src/main/res/xml/locales_config.xml`.
5. Run the Debug workflow. It builds the APK, runs unit tests and rejects CJK text accidentally added to production Kotlin/Java source.

Missing keys automatically fall back to the English resources in `values/`.

## Rules

- Do not translate package names, class/component names, MIME values, URI schemes, protocol tokens or user-defined names.
- Preserve numbered Android format placeholders exactly.
- Do not store localized labels inside serialized domain enums or configuration objects.
- Prefer structured state plus a resource lookup over constructing complete user-facing sentences in domain code.
- User-facing errors generated outside Compose must use `Context.getString(...)`.
- Diagnostic-only strings should be stable technical English tokens rather than localized text when localization adds no user value.
- New production Kotlin/Java code must pass `tools/check-source-localization.py`.

## Resource layout

```text
app/src/main/res/
├── values/
│   ├── strings.xml
│   ├── strings_core.xml
│   ├── strings_ui.xml
│   ├── strings_root.xml
│   ├── strings_scope.xml
│   └── ...
├── values-zh/
│   ├── strings.xml
│   ├── strings_core.xml
│   ├── strings_ui.xml
│   ├── strings_root.xml
│   ├── strings_scope.xml
│   └── ...
└── xml/locales_config.xml
```

`tools/localization/values-xx-template.xml` is only a small starter example. The source of truth for the complete key set is the default `values/` directory.
