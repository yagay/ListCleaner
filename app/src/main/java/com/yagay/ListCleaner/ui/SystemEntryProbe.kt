package com.yagay.ListCleaner.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.appwidget.AppWidgetManager
import android.os.Process
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isPackageScopedEntry

/** One explicit request, never an implicit query of whatever workspace was previously selected. */
internal data class SystemProbeRequest(
    val entryKind: IntentKind? = null,
    val componentKind: CleanupKind? = null,
    val userId: Int,
    val focusPackage: String? = null,
    val focusClass: String? = null,
) {
    init { require((entryKind == null) != (componentKind == null)) }

    fun matches(item: SystemProbeItem): Boolean {
        if (focusPackage == null || item.packageName != focusPackage) return false
        if (entryKind?.isPackageScopedEntry() == true) return true
        return focusClass == null || item.className == focusClass
    }
}

internal data class SystemProbeItem(
    val label: String,
    val packageName: String,
    val className: String,
) {
    val component: String get() = "$packageName/$className"
}

internal data class SystemProbeResult(
    val sourceRes: Int,
    val explanationRes: Int,
    val items: List<SystemProbeItem> = emptyList(),
    val supported: Boolean = true,
    val failure: Boolean = false,
)

/**
 * Reads live public Android service/PackageManager results for this app's own user.
 * No stored ListCleaner candidate catalog, Xposed log observations, or locally
 * predicted filtering is substituted for an actual API response.
 *
 * The manager app may be exempt from its own Xposed rule hooks. As such even real
 * PackageManager query results are NOT proof that the picker shown to another app
 * received the same (filtered) list. This distinction is always visible in the UI.
 */
internal object SystemEntryProbe {
    fun query(context: Context, request: SystemProbeRequest): SystemProbeResult {
        if (request.userId != AndroidUid.userId(Process.myUid())) {
            return SystemProbeResult(
                R.string.system_probe_source_none,
                R.string.system_probe_other_user,
                supported = false,
            )
        }
        return try {
            val kind = request.entryKind
            if (kind != null) probeEntry(context, kind) else probeComponent(
                context, requireNotNull(request.componentKind)
            )
        } catch (_: SecurityException) {
            SystemProbeResult(R.string.system_probe_source_none, R.string.system_probe_restricted,
                supported = false, failure = true)
        } catch (_: Exception) {
            SystemProbeResult(R.string.system_probe_source_none, R.string.system_probe_failed,
                supported = false, failure = true)
        }
    }

    private fun result(sourceRes: Int, explanationRes: Int, items: List<SystemProbeItem>) =
        SystemProbeResult(sourceRes, explanationRes, items.distinctBy(SystemProbeItem::component).take(1500))

    private fun unavailable() = SystemProbeResult(
        R.string.system_probe_source_none, R.string.system_probe_no_public_registry,
        supported = false,
    )

    @Suppress("DEPRECATION")
    private fun probeEntry(context: Context, kind: IntentKind): SystemProbeResult {
        val pm = context.packageManager
        return when (kind) {
            IntentKind.DIRECT_SHARE, IntentKind.SHORTCUT_ITEM, IntentKind.LAUNCHER_SHORTCUT,
            IntentKind.DOCUMENT_ROOT -> unavailable()

            IntentKind.INPUT_METHOD -> {
                val list = context.getSystemService(InputMethodManager::class.java)
                    ?: return unavailable()
                result(R.string.system_probe_source_input_method, R.string.system_probe_manager_notice,
                    list.inputMethodList.mapNotNull { info ->
                        val si = info.serviceInfo ?: return@mapNotNull null
                        SystemProbeItem(
                            info.loadLabel(pm)?.toString().orEmpty().ifBlank { si.name },
                            si.packageName, si.name
                        )
                    })
            }
            IntentKind.ACCESSIBILITY -> {
                val list = context.getSystemService(AccessibilityManager::class.java)
                    ?: return unavailable()
                result(R.string.system_probe_source_accessibility, R.string.system_probe_manager_notice,
                    list.installedAccessibilityServiceList.mapNotNull { info ->
                        val resolved = info.resolveInfo ?: return@mapNotNull null
                        val service = resolved.serviceInfo ?: return@mapNotNull null
                        SystemProbeItem(resolved.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                            service.packageName, service.name)
                    })
            }
            IntentKind.DOCUMENT_PROVIDER, IntentKind.PHOTO_SOURCE -> {
                val action = if (kind == IntentKind.DOCUMENT_PROVIDER) DocumentsContract.PROVIDER_INTERFACE
                             else "android.content.action.CLOUD_MEDIA_PROVIDER"
                result(R.string.system_probe_source_providers, R.string.system_probe_provider_notice,
                    pm.queryIntentContentProviders(Intent(action), PackageManager.GET_META_DATA)
                        .mapNotNull { ri ->
                            val provider = ri.providerInfo ?: return@mapNotNull null
                            SystemProbeItem(
                                ri.loadLabel(pm)?.toString().orEmpty().ifBlank { provider.name },
                                provider.packageName, provider.name
                            )
                        })
            }
            IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER, IntentKind.NOTIFICATION_LISTENER,
            IntentKind.VPN, IntentKind.PRINT, IntentKind.WALLPAPER, IntentKind.DREAM,
            IntentKind.NFC_HCE, IntentKind.CALL_SCREENING -> {
                result(R.string.system_probe_source_services, R.string.system_probe_service_notice,
                    pm.queryIntentServices(Intent(kind.action), PackageManager.GET_META_DATA)
                        .mapNotNull { ri ->
                            val service = ri.serviceInfo ?: return@mapNotNull null
                            SystemProbeItem(
                                ri.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                                service.packageName, service.name
                            )
                        })
            }
            else -> {
                // Same concrete test intent used by the original Android chooser button.
                val intent = SystemEffectPreview.queryIntent(context, kind)
                val flags = if (kind == IntentKind.HOME || kind == IntentKind.ASSISTANT)
                    0 else PackageManager.MATCH_DEFAULT_ONLY
                result(R.string.system_probe_source_activities, R.string.system_probe_pm_notice,
                    pm.queryIntentActivities(intent, flags).mapNotNull { ri ->
                        val activity = ri.activityInfo ?: return@mapNotNull null
                        SystemProbeItem(
                            ri.loadLabel(pm)?.toString().orEmpty().ifBlank { activity.name },
                            activity.packageName, activity.name
                        )
                    })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun probeComponent(context: Context, kind: CleanupKind): SystemProbeResult {
        val pm = context.packageManager
        return when (kind) {
            CleanupKind.LAUNCHER -> {
                val launcher = context.getSystemService(LauncherApps::class.java)
                    ?: return unavailable()
                result(R.string.system_probe_source_launcher, R.string.system_probe_manager_notice,
                    launcher.getActivityList(null, Process.myUserHandle()).map { info ->
                        val component = info.componentName
                        SystemProbeItem(
                            info.label?.toString().orEmpty().ifBlank { component.className },
                            component.packageName, component.className
                        )
                    })
            }
            CleanupKind.SHORTCUT -> {
                val launcher = context.getSystemService(LauncherApps::class.java)
                    ?: return unavailable()
                result(R.string.system_probe_source_launcher, R.string.system_probe_shortcut_notice,
                    launcher.getShortcutConfigActivityList(null, Process.myUserHandle()).map { info ->
                        val component = info.componentName
                        SystemProbeItem(
                            info.label?.toString().orEmpty().ifBlank { component.className },
                            component.packageName, component.className
                        )
                    })
            }
            CleanupKind.WIDGET -> {
                val widget = context.getSystemService(AppWidgetManager::class.java)
                    ?: return unavailable()
                result(R.string.system_probe_source_widgets, R.string.system_probe_widget_notice,
                    widget.installedProviders.map { provider ->
                        val component = provider.provider
                        SystemProbeItem(
                            provider.label.orEmpty().ifBlank { component.className },
                            component.packageName, component.className
                        )
                    })
            }
            CleanupKind.TILE -> result(
                R.string.system_probe_source_services, R.string.system_probe_tile_notice,
                pm.queryIntentServices(
                    Intent("android.service.quicksettings.action.QS_TILE"),
                    PackageManager.GET_META_DATA
                ).mapNotNull { ri ->
                    val service = ri.serviceInfo ?: return@mapNotNull null
                    SystemProbeItem(ri.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                        service.packageName, service.name)
                }
            )
        }
    }
}
