# List Cleaner 1.6.7

**English** | [简体中文](RELEASE_NOTES.zh-CN.md)

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
