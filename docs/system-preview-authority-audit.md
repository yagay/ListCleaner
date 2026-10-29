# Android system-preview authority audit

This file records the contract for **every** ListCleaner preview type. A real public
API call is not automatically the final list displayed by Android, SystemUI or a
vendor picker. Avoid inventing final visibility, ranking or "cleaning succeeded"
from PackageManager registrations.

The runtime authority mapping lives in `SystemPreviewAuthority.kt`. Queries and
UI rendering are dispatched by `SystemEntryProbe.kt` and `SystemProbeDialog.kt`.
This audit is deliberately separate from the rules catalog: scanning everything
that *could* be cleaned and verifying what a particular host *actually displays*
are different operations.

## Every IntentKind

| Surface | Actual observable source | Final visible system choices? |
| --- | --- | --- |
| Choose content (`GET_CONTENT`) | Native picker host + optional **hidden diagnostics** of matching handlers | **No public enumeration** of picker-internal sources or documents |
| Open document, create document | Native SAF DocumentsUI and optional diagnostic handlers | **No**; matching document roots and files are computed inside the system |
| Document root | Actual DocumentsUI tree (native launch), runtime Hook may observe roots | **No** direct general-purpose read API for final roots |
| Photo source | Cloud-provider registration **only in diagnostics** and native photo picker | **No**; the active cloud provider and displayed photos are system-controlled |
| Direct Share, dynamic shortcut, retired launcher shortcut | Native Sharesheet where possible; no public all-app ranking API | **No**; ShortcutService, caller and resolver ranking determine content |
| Share, share multiple | `PackageManager.queryIntentActivities` with exact test MIME | Activity **candidates**; not Direct Share or final Sharesheet list |
| Send to, open, deep link, dial | `PackageManager.queryIntentActivities` with exact action/URI/MIME | Activity **candidates**; defaults, exclusions and host presentation may differ |
| Capture image, capture video, record audio, process text | Intent handler registrations | Activity **candidates**; OEM resolver may apply other rules |
| Browser, home, assistant, call screening | Eligible role-related candidate packages / service declarations | **Not** final RoleController eligibility, installed defaults or system list |
| Input method | `InputMethodManager.enabledInputMethodList`, installed list optional diagnostics | **Enabled entries** from public manager; Settings UI can still be customized |
| Accessibility | `AccessibilityManager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)`, installed list optional diagnostics | **Enabled entries** from public manager; custom OEM Settings may differ |
| Document provider | `PackageManager.queryIntentContentProviders(DOCUMENTS_PROVIDER)` | Provider declaration, **not** actual visible document roots |
| Autofill | AutofillService registrations and required binding permission | **Candidates**, not Android 16's combined picker list. Credential cleanup has been retired; serialized old rules are inert. |
| VPN, print, NFC HCE | Binding-permission-eligible services; NFC uses static payment AID groups | **Candidates**, not final manager/settings UI or dynamic AID registrations |
| Notification listener, wallpaper, dream | Matching enabled service declarations with binding contracts | Registered services; final Settings list may differ |

## Four Root component types

| Surface | Actual observable source | Final host behavior? |
| --- | --- | --- |
| Launcher icons | `LauncherApps.getActivityList` | A launcher may hide, rank or change icons privately |
| Shortcut configuration | `LauncherApps.getShortcutConfigActivityList` | Not the full dynamic/pinned/Direct Share shortcut list |
| Widgets | `AppWidgetManager.installedProviders` | Launcher may apply private filtering and preferences |
| Quick Settings tiles | `PackageManager.queryIntentServices(QS_TILE)` + bind permission/exported/enabled checks | SystemUI editor's actual available/active tile list is not public |

## Key invariants

1. System picker interiors are never labeled or counted as visible entries from
   `ACTION_GET_CONTENT` resolver registrations. These can be inspected only
   through an explicit, clearly labeled diagnostic view; normal display points
   to the real system picker.
2. A picker host `ResolveInfo` is a host, not a file provider, media source or
   selected document.
3. Public manager **enabled** lists are separate from their **installed**
   diagnostic lists; the latter must not be shown as enabled.
4. Service and provider candidates must meet the declared Android binding
   permissions and component eligibility. Root component management may also
   include *disabled* items for re-enabling; its inventory cannot be reused as a
   list of currently active tiles.
5. Preview-only queries can traverse ListCleaner's authenticated Xposed filter
   path. Other app callers, OEM process-specific filters, work profiles and the
   final system UI can still return different lists; only in-device comparison
   establishes actual displayed contents.
6. Work-profile previews must never silently substitute personal-profile data.
7. System UI launch buttons are offered only for supported actions. Large result
   sets render lazily; dynamic shortcuts and role controller data are not faked.
8. Every new `IntentKind` must be classified exhaustively in the authority
   contract and covered by a regression test. Do not add a generic
   `queryIntentActivities()` fallback claiming to be the final system list.

## On-device acceptance checks

On an OxygenOS 16 device with the current LSPosed module loaded:

- Choose content (all/text/images/PDF): compare the native picker with
  "Diagnostics: possible handlers" — diagnostic entries must never be called
  "actually enabled apps" or appear as the primary picker list.
- Open/create document, photo source and document root: native picker is the
  source of visible truth; no invented internal provider/root list.
- Input methods and accessibility: enabled counts must come from manager
  APIs, while installed candidates remain separate.
- Share/Direct Share: check MIME-sensitive results and recognize dynamic
  targets are shown only by system/host, not generic `queryIntentActivities`.
- Tiles, widgets, shortcut and launcher: compare host registry versus actual
  SystemUI/launcher UI, keeping unknown OEM filtering explicitly unverified.
- Verify main rules still let the user re-enable disabled components and that
  authenticated preview Hook queries do not affect normal catalog scanning.

References: [Android common intents](https://developer.android.com/guide/components/intents-common),
[Direct Share targets](https://developer.android.com/develop/ui/compose/sharing/direct-share-targets),
[AccessibilityManager API](https://developer.android.com/reference/android/view/accessibility/AccessibilityManager).
