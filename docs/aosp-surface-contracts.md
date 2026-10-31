# AOSP system-surface filtering — bata

The additional cleanups are implemented **against AOSP class and method contracts**;
no OxygenOS-specific class paths, naming heuristics or compatibility branches are
used by `AospSurfaceFilterModule`.

The controls are opt-in. Without a saved rule for a surface the original AOSP
method runs unchanged. Filtering a displayed list is **not** disabling an app,
terminating a task, cancelling a notification or deleting a document.

## UI / persistence

- Bottom navigation: **Entry cleanup | Components | Lists | Settings**
- **Lists** → Notification Cleanup (single page) / System lists / Rule maintenance
- Choose an individual surface or operation, edit its candidates, locks or order,
  then confirm Apply. Navigation and Android user switching are protected by
  the existing staged-change guard; Settings now contains configuration only.
- **Entry cleanup** continues to own ACTION_EDIT and ACTION_PICK.
  **Components** continues to own Device Controls and AccountAuthenticator Root actions.
- Each Android user has its own `SurfaceFilterConfig` inside `UserSpaceRules`
- The complete `ModuleConfig` is sent through the existing RemotePreferences
  configuration bridge to LSPosed; legacy configs default to empty surface rules
- Single-profile JSON backups and multi-profile v13 backups preserve these selections

## AOSP hook paths

| Surface | Concrete Android source boundary | Selection identity |
| --- | --- | --- |
| Notification shade | `RenderStageManager.onRenderList(List<ListEntry>)` | Notification package; protected foreground service/call/alarm rows preserved; mixed groups retained |
| Add Quick Settings tiles | `TileAdapter.onTilesChanged(List<TileInfo>)` | Custom tile component package; active specs retained |
| DocumentsUI recent files | `RecentsLoader.loadInBackground()` (`DirectoryResult.cursor`) | Recent document authority; cloned bounded Cursor, never mutate document provider |
| Settings app lists | `ApplicationsState.Session.rebuild(...)` | `AppEntry.info.packageName` |
| Media output | `MediaOutputController.getMediaDevices()` | `MediaDevice.getId()`; current connected route preserved |

All hooks check the expected class and method shape, return the original result on
unsupported signatures, and do not manipulate underlying Android registries.

**Home and Recents retired:** the three previous Lists navigation entries
(Launcher app drawer, predicted apps, recent task cards) have been removed.
`AospSurfaceFilterModule` no longer installs
`AllAppsStore.getApps`, `AlphabeticalAppsList.getPredictedApps` or
`RecentsView.applyLoadPlan` hooks, and candidate discovery no longer queries
`LauncherApps` for these surfaces. Old `SurfaceKind` names and stored rules
remain deserializable in existing user profiles and JSON backups. They are
**inert**: no Launcher3 / Quickstep UI filtering or sorting will run, even when
the legacy backup contains selected or ordered apps. This does not delete
other modules' entry/shortcut functionality. LSPosed processes already running
an older module may require an app/process restart to lose the old hooks.

**Notification Cleanup** now combines application-level notification-shade rules
and registered channel rules on one page. Selecting the app hides all of its
unprotected shade notifications; selecting an expanded channel hides only
notifications from that channel. The two rule sets have separate locks and are
saved independently per Android user. Bulk actions target only the visible,
unlocked **channels**, not the whole-app rule. Scanning registered channels uses
one batched, read-only root `app_process` probe (with listener/observed fallback);
apps with no discovered channels are omitted unless a saved app-level rule
exists. Scan failures must not be described as an empty channel registry.

The previous separate Current Notifications, Types, and Notification Shade
navigation entries are not exposed. Existing global category rules remain
active for backward compatibility and are visible/removable in a legacy-rule
section of Notification Cleanup. Backup schemas and SystemUI presentation-hook
identity are unchanged. The notification listener still observes current
notifications for inventory fallback, but the cleanup screen never invokes
`cancelNotifications`; hiding a notification is not cancelling its delivery.

Device Controls is also separate: its `ControlsProviderService` entries are
Root component states rather than a display-only filter.

## Scope / validation

- AOSP surface process targets: `com.android.systemui`,
  `com.android.documentsui`, `com.android.settings`.
  `com.android.launcher3` is no longer a target for this surface module
  (but may remain in the broader LSPosed scope for independent features).
- The package must exist in LSPosed scope, and the process must be restarted to
  install a newly enabled Hook. No new package is hidden by default.
- CI can validate Android APK compilation and JVM unit tests, **not** live
  rendering behavior. AOSP framework versions can also change method shapes.
- A failed lookup leaves the original system list untouched. Device-level UI
  confirmation and runtime hook-installation diagnostics are needed to declare
  each surface effective.


## Completed new cleanup types

- **Edit with**: separate `Intent.ACTION_EDIT` rule identity, component discovery
  using MIME-scoped content URIs, independent resolver filtering and direct preview
  with temporary read/write access to ListCleaner-owned sample documents.
- **Pick item**: `Intent.ACTION_PICK` probes for contacts and media, separate
  from GET_CONTENT and DocumentsUI. These are normal resolver activities.
- **AccountAuthenticator services**: an additional **Root component** category,
  using `android.accounts.AccountAuthenticator` service registration and
  metadata. A disabled authenticator may break adding/recovering accounts and
  synchronization. This is a reversible, user-confirmed *component state change*,
  **not** AccountManager token interception or Credential Provider cleanup.
- **Registered notification channels**: use a batched read-only Root
  `RootNotificationChannelProbe` to inspect installed apps' registered channels
  (32 packages per batch), falling back to
  `NotificationListenerService.getNotificationChannels(pkg, user)` where permitted,
  then historical observations where privileged APIs are unavailable.
  Per-channel and per-app hide rules remain separate and apply only to
  notification-shade presentation hooks; neither disables a notification channel.
- **Surface lock and ordering**: a locked selected item stays hidden and a
  locked unselected item stays visible during bulk operations. Selected orders
  are separate per profile and surface, with active order hooks for Settings
  managed-app rows and the QS edit list. Legacy Launcher drawer / predictions
  order values are retained for backup compatibility but are no longer applied.
  The existing active QS tiles and notification safety protections remain.

No user-facing option silently deletes accounts, rewrites other packages'
registered notification-channel importance, or disables an application's
non-authenticator components.

## AOSP source references

- [NotifPipeline / notification render sequence](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/collection/NotifPipeline.kt)
- [ShadeListBuilder](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/collection/ShadeListBuilder.java)
- [SystemUI TileQueryHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/customize/TileQueryHelper.java)
- [DocumentsUI RecentsLoader](https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/refs/heads/main/src/com/android/documentsui/RecentsLoader.java)
- [Android Settings](https://android.googlesource.com/platform/packages/apps/Settings/)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
