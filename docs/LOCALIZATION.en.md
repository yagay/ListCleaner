# List Cleaner bilingual parity policy

[简体中文](LOCALIZATION.md) | **English**

List Cleaner maintains corresponding Chinese and English **user-facing content**. The priority is text visible in the app plus installation, usage, and release documentation. Developer-only logs, CI output, source comments, and machine-oriented diagnostic data do not require Chinese translations.

## Core rules

- `values/` contains the default English user-facing resources.
- `values-zh/` contains the corresponding Chinese resources.
- `res/xml/locales_config.xml` declares only languages that are actively maintained.
- Compose UI uses `stringResource(...)`; non-Compose user-facing messages use `Context.getString(...)`.
- Adding, removing, or changing a user-visible resource requires updating both languages while preserving equivalent format placeholders.
- Chinese is the default project-documentation language; English user documentation must cover the same features, warnings, limitations, and operating steps.

## Content that must correspond

- App UI, Toasts, errors, status text, warnings, dialogs, buttons, and accessibility descriptions.
- Scan, Root, backup/restore, and diagnostic-export results shown directly in the app.
- GitHub README and user-facing usage documentation.
- LSPosed README, SUMMARY, and module documentation.
- Release Notes, GitHub Releases, and Telegram release messages.
- Installation, release, and usage documentation.
- Diagnostic ZIP overview documentation intended for normal users, such as `README.zh-CN.txt` and `README.en.txt`.

Parity means equivalent information and structure, not word-for-word translation. A complete Chinese document with only an English summary, or an English warning missing from Chinese, violates this policy.

## Content that does not require Chinese

The following may remain English-only or use stable machine identifiers:

- Developer source comments, tests, test output, and CI/Actions logs.
- Internal exception text, debug logs, Xposed/Root event names, diagnostic event names, and developer analysis reports.
- Raw logcat, LSPosed, shell, dumpsys, and other system-command output.
- Package, class, and component names; MIME values; URI schemes; Intent actions; protocol tokens.
- Configuration keys, serialized enums, rule IDs, diagnostic machine-field names, and fixed ZIP paths.
- User-defined names and original labels supplied by third-party applications.

These are primarily for development and troubleshooting; translating them would reduce searchability, comparability, or parser stability.

## Diagnostic policy

Diagnostic ZIPs have two layers:

1. Overview documentation intended for normal users has corresponding Chinese and English versions.
2. Developer analysis files, machine fields, and raw logs remain in one stable format, normally English.

Developer diagnostics should not be duplicated solely for bilingual parity. Only diagnostic text shown directly in the app UI must use Android string resources.

## Automated validation

`tools/check-source-localization.py` targets genuinely user-visible hardcoded text; developer-internal English is allowed.

`tools/check-translation-resources.py` validates Android user-facing resource XML, duplicate/unknown keys, format placeholders, `translatable=false` overrides, and locale declarations.

`tools/check-bilingual-parity.py` validates user documentation, LSPosed documentation, Release Notes, the Telegram publishing path, and Chinese/English app resource parity. It does not require developer diagnostic reports to be bilingual.

## Maintenance rule

Use one question to decide whether text needs both languages: “Will a normal user see it directly?” If yes, maintain Chinese and English equivalents. If it is only for developers, CI, log analysis, or machine parsing, a single stable English/machine representation is sufficient.
