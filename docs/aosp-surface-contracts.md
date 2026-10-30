# AOSP system-surface filtering — bata

The additional cleanups are implemented **against AOSP class and method contracts**;
no OxygenOS-specific class paths, naming heuristics or compatibility branches are
used by `AospSurfaceFilterModule`.

The controls are opt-in. Without a saved rule for a surface the original AOSP
method runs unchanged. Filtering a displayed list is **not** disabling an app,
terminating a task, cancelling a notification or deleting a document.

## UI / persistence

- Settings → More cleanup → AOSP presentation filters
- Choose one of 8 independent surfaces, select matching items, then confirm Apply
- Each Android user has its own `SurfaceFilterConfig` inside `UserSpaceRules`
- The complete `ModuleConfig` is sent through the existing RemotePreferences
  configuration bridge to LSPosed; legacy configs default to empty surface rules
- Single-profile JSON backups and multi-profile v13 backups preserve these selections

## AOSP hook paths

| Surface | Concrete Android source boundary | Selection identity |
| --- | --- | --- |
| Notification shade | `RenderStageManager.onRenderList(List<ListEntry>)` | Notification package; protected foreground service/call/alarm rows preserved; mixed groups retained |
| Launcher all apps | `AllAppsStore.getApps()` | `AppInfo` component package and profile |
| Launcher predictions | `AlphabeticalAppsList.getPredictedApps()` | `AppInfo` component package |
| Recent task cards | Quickstep `RecentsView.applyLoadPlan(List<GroupTask>)` | Task's base-intent package; split task group retained when one task isn't selected |
| Add Quick Settings tiles | `TileAdapter.onTilesChanged(List<TileInfo>)` | Custom tile component package; active specs retained |
| DocumentsUI recent files | `RecentsLoader.loadInBackground()` (`DirectoryResult.cursor`) | Recent document authority; cloned bounded Cursor, never mutate document provider |
| Settings app lists | `ApplicationsState.Session.rebuild(...)` | `AppEntry.info.packageName` |
| Media output | `MediaOutputController.getMediaDevices()` | `MediaDevice.getId()`; current connected route preserved |

All hooks check the expected class and method shape, return the original result on
unsupported signatures, and do not manipulate underlying Android registries.

The notification **dismiss** screen is separate: a user-granted
`NotificationListenerService.cancelNotifications(keys)` really removes currently
posted notifications. The notification-shade rule above affects only presentation.

Device Controls is also separate: its `ControlsProviderService` entries are
Root component states rather than a display-only filter.

## Scope / validation

- AOSP process targets: `com.android.systemui`, `com.android.launcher3`,
  `com.android.documentsui`, `com.android.settings`
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
- **Registered notification channels**: optionally query
  `NotificationListenerService.getNotificationChannels(pkg, user)` where the
  platform grants associated-device / notification-assistant privilege. Without
  it, the manager clearly labels an **observed-only** list from current posts.
  Per-channel selections are stored for the current Android profile and applied
  only to the notification-shade presentation hook.
- **Surface lock and ordering**: a locked selected item stays hidden and a
  locked unselected item stays visible during bulk operations. Selected orders
  are separate per profile and surface, with stable order hooks for Launcher
  drawer/predictions, Settings managed-app rows and the QS edit list. The
  existing active QS tiles and task/notification safety protections remain.

No user-facing option silently deletes accounts, rewrites other packages'
registered notification-channel importance, or disables an application's
non-authenticator components.

## AOSP source references

- [NotifPipeline / notification render sequence](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/collection/NotifPipeline.kt)
- [ShadeListBuilder](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/collection/ShadeListBuilder.java)
- [Launcher3 AlphabeticalAppsList](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/allapps/AlphabeticalAppsList.java)
- [Quickstep RecentsView](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/quickstep/src/com/android/quickstep/views/RecentsView.java)
- [SystemUI TileQueryHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/customize/TileQueryHelper.java)
- [DocumentsUI RecentsLoader](https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/refs/heads/main/src/com/android/documentsui/RecentsLoader.java)
- [Android Settings](https://android.googlesource.com/platform/packages/apps/Settings/)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
