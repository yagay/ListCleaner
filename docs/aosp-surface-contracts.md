# AOSP surface-cleanup contracts (bata)

This document separates **real implemented operations** from AOSP reference paths
that cannot yet be validated on the user's OxygenOS launcher/SystemUI.
A class name from AOSP is **not** evidence that an OEM app implements it.

## Implemented and independently verifiable

| Surface | Android owner and source | Actual effect | Fail-safe |
|---|---|---|---|
| Active notifications | `NotificationListenerService` (`android.service.notification`) | User-approved `cancelNotifications(keys)`: removes notifications, **not** display-only filtering | Requires OS-granted notification access and a connected listener. Filters out ongoing, foreground-service, alarm, call and no-clear notifications; no message bodies are stored |
| Channel settings | `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` with `EXTRA_APP_PACKAGE` and `EXTRA_CHANNEL_ID` | Opens OS-owned per-channel settings for an active notification | Uses the public app-notification settings fallback. Does not attempt privileged cross-app channel mutation. Only opens current-profile settings |
| Device Controls provider | `ControlsProviderService`, `BIND_CONTROLS` | Root disables/restores a provider component after confirmation, not just a row in SystemUI | Exported service + required permission; profile-specific component rule. Warns controls can stop working |
| Invalid rules | Existing `IntentCatalog`, `MainState`, `MainViewModel.deselectRules` | Full-scan, manually selected unavailable rules are unselected | No automatic deletion; scan required, locks stay honored; a restricted scan may show a false unavailable item |

## AOSP integration targets not yet safe to assert operational

| Requested filter | AOSP owner and relevant source | Requirement before activation |
|---|---|---|
| Notifications UI-only hide | `packages/SystemUI/.../NotifPipeline.kt`: `addPreGroupFilter`, `addFinalizeFilter` | A real `NotifFilter` registration in the running OEM pipeline, no clearing from NotificationManager, and full shade/lockscreen regression tests |
| Launcher drawer and suggestions | `packages/apps/Launcher3/.../AlphabeticalAppsList.java`: `onAppsUpdated`, `getFiltersAppInfos` | Confirm actual OEM launcher class signatures, app model and user/profile behavior; only filter presentation rows, never disabled launch components |
| Recents task cards | Launcher3 Quickstep `RecentsView`/task view | Identify final task-card data owner, preserve running tasks and task IDs; **do not** call removeTask/finishAndRemoveTask |
| QS editor recommendations | SystemUI `TileQueryHelper`/QS tile query listener | Distinguish currently installed tiles from editor-only suggestions. Preserve active tiles and platform stock tiles |
| DocumentsUI Recent files | DocumentsUI `RecentsLoader` and cursor-backed result | Distinguish recent-row display from underlying provider document; preserve URI access, document contents and profile boundaries |
| Settings app-management rows | Settings `ApplicationsState` | Only filter one target UI's adapter. Never hide packages from permissions, installer, device policy or system managers |
| Media-output picker routes | SystemUI media-output controller / `MediaRouter2` | Separate selected/connected routes, safe return value shape, audio handover and device disconnect regression checks |

Do **not** add dummy toggles for missing OEM hooks; leave these surfaces unmodified.
In a test build, log the loaded class name, supported method signature and process before implementing an active UI filter. AOSP code may have been independently forked or refactored by OxygenOS.

## References (AOSP/public SDK)

- [NotifPipeline](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/collection/NotifPipeline.kt)
- [Launcher3 AlphabeticalAppsList](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/allapps/AlphabeticalAppsList.java)
- [SystemUI TileQueryHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/customize/TileQueryHelper.java)
- [Android Settings actions](https://developer.android.com/reference/android/provider/Settings)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Android Recents](https://developer.android.com/guide/components/activities/recents)
