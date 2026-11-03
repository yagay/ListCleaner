# AOSP system-surface filtering — current development contract

The additional cleanups are implemented **against AOSP class and method contracts**;
no OxygenOS-specific class paths, naming heuristics or compatibility branches are
used by `AospSurfaceFilterModule`.

The controls are opt-in. Without a saved rule for a surface the original AOSP
method runs unchanged. Filtering a displayed list is **not** disabling an app,
terminating a task, cancelling a notification or deleting a document.

## UI / persistence

- Bottom navigation: **Controls | Cleanup | Settings**. Cleanup switches between
  **Entry** (rules/candidate filtering) and **Component** (real Root component
  states). Supported Entry categories open Priority Order via their sort control.
- **Controls → Cleanup management** has **Entry cleanup** and **Component
  cleanup**. Entry cleanup shows the default cleanup method first, followed by
  category-specific inheritance/overrides and independent category switches.
  Both pages support search, enabled/disabled filters, Enable all / Disable all,
  and confirmation. A switch is **not** an immediate Android Root component edit.
- **Cleanup → Entry** contains LSPosed-managed **Settings Apps** and
  **Notification Channels** alongside other Entry kinds. These use AOSP
  presentation filtering / channel rules, not Root component disabling.
- **Cleanup → Component** manages six Root component kinds (launcher, shortcut
  configuration, widget, tile, device controls, account authenticator). Root
  state edits require permissions, staged confirmation and system read-back.
- **Controls → Favorite categories** shares existing pinned Entry and Component
  kinds. The + chooser reuses Cleanup category icons/flat rows with multi-select;
  long press reorders or removes; four items are shown initially and can
  expand. Cross-type order (`homePinnedCategoryOrder`) is a local preference,
  separate from per-space rules and omitted from rule backups.
- User-space rules still live in each `UserSpaceRules` / `SurfaceFilterConfig`.
  The `ModuleConfig` is synchronized through the existing RemotePreferences
  bridge; older configurations default to empty surface rules. Multi-profile
  v13 rule backups preserve the synchronized selections. Navigation and
  profile changes retain the staged-change guard.
- Settings holds compatibility/scope and diagnostics configuration. The
  Module status summary is on the Controls home and opens Compatibility.

## AOSP hook paths

| Surface | Concrete Android source boundary | Selection identity |
| --- | --- | --- |
| Notification shade | `RenderStageManager.onRenderList(List<ListEntry>)` | Notification package; protected foreground service/call/alarm rows preserved; mixed groups retained |
| Root > Tiles, add-tile candidate order | `TileAdapter.onTilesChanged(List<TileInfo>)` | Custom TileService `ComponentName`; active specs and built-in suggestions stay in their original slots |
| Root > Tiles, visible label | `CustomTile.handleUpdateState` / `getTileLabel` | Per-user TileService `ComponentName` |
| Settings app lists | `ManageApplications.ApplicationsAdapter.onRebuildComplete(...)` | `AppEntry.info.packageName` |

All hooks check the expected class and method shape, return the original result on
unsupported signatures, and do not manipulate underlying Android registries.

**Launcher Shortcut Items discovery (read-only):** `LauncherShortcutObserverModule`
starts in an LSPosed-scoped default HOME process (including
`com.android.launcher`, `com.android.launcher3`, Pixel Launcher, MIUI
Home and Samsung One UI Home). It verifies `LauncherApps.hasShortcutHostPermission()`
before querying all published dynamic, manifest, pinned and cached shortcut
IDs for each accessible Android profile. Results contain only user ID,
package, shortcut ID-derived synthetic rule key, activity and short label;
never shortcut intents/extras or contact payloads. A registered
`LauncherApps.Callback` handles package/shortcut changes; a periodic
read-only refresh handles missed callbacks. RemotePreferences publishes
per-user `SHORTCUT_ITEM` authority snapshots; a valid empty snapshot
removes obsolete observations for that user, while errors preserve the
previous snapshot. Manager sync uses existing observed-entry cache and
unified Shortcut Items interface; Root/shell scanning remains a fallback.
Fresh launcher snapshots are regarded as current for a bounded interval.
This observer never installs filtering hooks, changes HOME role, or alters
the existing shortcut cleanup, locks, or per-user rules. Default scope
is not a promise of OEM runtime compatibility: a nonstandard launcher
package must be explicitly scoped and adapted; install/update requires
restarting the HOME process. Query results are published shortcuts, not
necessarily all launcher-generated long-press actions.

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

**System Lists integrated:** **Settings Apps** is now a managed **Entry /
LSPosed** category backed by the existing Settings ApplicationsAdapter
presentation hook, not a Root component or an independent Lists editor.
Former standalone Lists **Quick Settings Add-Tile**, **Documents Recents**
and **Media Output** editors and discovery are retired. Their legacy
package-based rules are inert, while `SurfaceKind` identities remain
deserializable in per-user JSON backups; no legacy rule is silently deleted.

**Root > Tiles stays active:** its `tileOrder` and `tileLabels` fields
continue to control component-level SystemUI add-tile suggestion ordering
and CustomTile label presentation. The shared `TileAdapter` hook no longer
uses legacy `QS_EDITOR` package rules to hide or sort whole apps; only
`tileOrder` affects the candidate list. This does not remove any existing
Quick Settings tiles, and it does not change enabled/disabled TileServices.
`com.android.documentsui` may still be required by independent Entry /
DocumentsUI modules; retiring *recent-file filtering* does not disable those.

**Notification Channels** is a managed **Entry** category combining
application-level notification-shade rules and registered channel rules
under grouped app/detail rows. Selecting an app hides its unprotected
notifications in the shade; selecting a channel hides only that channel.
These have independent locks/rules per Android user. Bulk actions target
visible unlocked channels, not an entire app. Channel scanning uses a
batched read-only Root `app_process` probe, with listener/observation
fallback; missing privileges cannot be called an empty channel registry.

The former standalone Current Notifications, Types, Notification Shade
and Notification Channels navigation pages are not exposed. Legacy
global notification category rules remain compatible and reviewable.
Notification listeners may observe active notifications but the managed
cleanup screen does not call `cancelNotifications`: hiding a shade row
is not cancelling notification delivery.

Device Controls is also separate: its `ControlsProviderService` entries are
Root component states rather than a display-only filter.

## Scope / validation

- Active AOSP surface process targets: `com.android.systemui`
  (notification presentation and Root Tiles) and `com.android.settings`
  (Settings Apps). Managing the first two as Entry surfaces does not
  route display-only edits through Root PackageManager component
  enable/disable commands. `com.android.documentsui` and
  `com.android.launcher3` may be in broader LSPosed scope for other
  independent Entry functionality.
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
  locked unselected item stays visible during bulk operations. the managed Entry Settings Apps category follows its
  supported presentation/order contract, while Root Tiles retains independent
  per-component ordering. Legacy Lists QS package ordering, Launcher drawer / predictions,
  and other retired surface rules are retained for backup compatibility but
  no longer applied. Existing active QS tiles and notification protections remain.

No user-facing option silently deletes accounts, rewrites other packages'
registered notification-channel importance, or disables an application's
non-authenticator components.

## AOSP source references

- [NotifPipeline / notification render sequence](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/collection/NotifPipeline.kt)
- [ShadeListBuilder](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/collection/ShadeListBuilder.java)
- [SystemUI TileQueryHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/customize/TileQueryHelper.java)
- [Android Settings](https://android.googlesource.com/platform/packages/apps/Settings/)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
