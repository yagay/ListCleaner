# System preview authority audit (Android 12–16)

## Why the old "actual API list" was misleading

`queryIntentActivities(ACTION_GET_CONTENT)` retrieves possible activity handlers for the
caller's Intent. Android can first route that Intent to a system photo or documents
picker, which then decides **its own** visible tabs, roots, apps, documents and cloud
sources. Those are different APIs and often different processes/UIDs. Even when the
ListCleaner Hook filters the PackageManager response, the public response cannot tell
what DocumentsUI/PhotoPicker eventually renders.

The same distinction applies to all other preview surfaces. An Android API call can
be genuine without measuring the visible contents of an OEM-controlled UI.

## One evidence contract, applied to every selectable surface

| Category | What is directly observable in ListCleaner | Can this prove final system visibility? |
| --- | --- | --- |
| Choose content (`GET_CONTENT`) | Native `ACTION_GET_CONTENT` dialog for chosen MIME | **No** public API for its internal sources; do not print PM handlers as visible apps |
| Open document / Create document | Native SAF document UI with the matching MIME | **No**; PackageManager returns picker activities, not matching documents or roots |
| Document root | DocumentsUI tree native dialog | **No**; internal root list, runtime hook observation is distinct |
| Photo source | Native system Photo Picker | **No**; cloud-provider declarations are not picker source/UI list |
| Direct Share / Shortcut item / legacy launcher shortcut | System sharesheet where available; no generic registry query | **No**; dynamic shortcuts, caller and UI ranking matter |
| Share / Share multiple / Send to | `PackageManager.queryIntentActivities` for exact test Intent | **Candidate handlers**, not final Sharesheet ranking/direct-share rows |
| Open / Deep link / Dial / Capture image / Capture video / Record audio / Process text | `PackageManager.queryIntentActivities` for exact MIME/URI/action | **Candidate handlers**, not resolver presentation, defaults or OEM selection |
| Browser / Home / Assistant / Call screening | Eligible package-level query candidates | **Not exact** RoleController settings choices or role holders |
| Input method / Accessibility | Public manager's installed-service registry | **Registry**, not final Settings list; additional conditions and OEM UI possible |
| Document provider | `queryIntentContentProviders(DOCUMENTS_PROVIDER)` | **Provider registration**, not visible navigation roots |
| Autofill / Credential provider / VPN / Print / NFC HCE | Binding-permission-checked service declarations (NFC also checks static payment group) | **Registry**, not privileged settings/manager final list |
| Notification listener / Wallpaper / Dream | Declared active services with required binding permission | **Registry**, not eventual settings surface |
| Launcher entries / Shortcut configuration / Widgets | `LauncherApps` / `AppWidgetManager` | **Registry**, launcher layout/suggestions may differ |
| Quick Settings tiles | Eligible `TileService` declarations with bind permission/exported/enabled | **Registry**, SystemUI's available/active tile editor is private |

This table is enforced by `SystemProbeEvidence.kt` and exhaustive unit tests.
The standard preview window always names the query's evidence type. For native-only
surfaces it hides the meaningless resolver-handler list and offers the real system
dialog. For other surfaces, numbers are described as API candidates or registered
entries; no count is ever called the **number actually shown by the system**.

## Filtering, privileges, and work profiles

- The system_server preview-only Hook marker is UID authenticated. ListCleaner's
  general app catalog scans remain exempt so editing a rule cannot hide its own
  management rows. This does not grant ListCleaner the authority of a system picker.
- Normal Android PackageManager queries can still differ by originating package,
  UID, MIME, URL, default handler, disabled/excluded targets and selected Android user.
- SystemUI, DocumentsUI, RoleController, ShortcutService and OEM code can apply
  additional lists or ranking. Their final data is not available to arbitrary apps
  through a single public API. Never guess at final visibility from scan inventory.
- Work-profile data is never silently replaced by the personal user result.
- Diagnostic scan and actual preview should be separate features; a fresh but incomplete
  query must not be presented as evidence that every clean-up rule worked.

## Regression expectations

1. Opening the GET_CONTENT category with `*/*`, text, image, PDF, video or audio
   never prints a large fake "visible apps" list. The native dialog uses the chosen MIME.
2. `OPEN_DOCUMENT` and `CREATE_DOCUMENT` also show the system picker rather than
   treating its `ResolveInfo` as documents or providers.
3. Provider, service, role, launcher, share and ordinary resolver queries all show
   a provenance label and explicitly candidate/registered counts only.
4. Native-only entries without a general direct-open action do not invent one.
5. Thousands of registered entries are rendered lazily, with the actual raw count
   distinguished from the capped set displayed.
6. Existing Root rules and Hook filtering/transport are unchanged by the preview UI
   classification refactor.
