package com.yagay.ListCleaner.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import com.yagay.ListCleaner.domain.webTargetKind
import org.xmlpull.v1.XmlPullParser
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
    val scenario: SystemProbeScenario? = null,
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
            if (kind != null) probeEntry(context, kind, request.scenario ?: defaultProbeScenario(kind)) else probeComponent(
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
    private fun probeEntry(
        context: Context, kind: IntentKind, scenario: SystemProbeScenario
    ): SystemProbeResult {
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
                val action = if (kind == IntentKind.DOCUMENT_PROVIDER)
                    DocumentsContract.PROVIDER_INTERFACE else "android.content.action.CLOUD_MEDIA_PROVIDER"
                result(R.string.system_probe_source_providers, R.string.system_probe_provider_notice,
                    pm.queryIntentContentProviders(
                        SystemEffectPreview.markedQueryIntent(Intent(action)),
                        PackageManager.GET_META_DATA or PackageManager.MATCH_ALL,
                    ).mapNotNull { ri ->
                        val provider = ri.providerInfo ?: return@mapNotNull null
                        if (!provider.enabled || provider.applicationInfo?.enabled != true)
                            return@mapNotNull null
                        if (kind == IntentKind.PHOTO_SOURCE &&
                            provider.readPermission !=
                            "com.android.providers.media.permission.MANAGE_CLOUD_MEDIA_PROVIDERS"
                        ) return@mapNotNull null
                        SystemProbeItem(
                            ri.loadLabel(pm)?.toString().orEmpty().ifBlank { provider.name },
                            provider.packageName, provider.name
                        )
                    })
            }
            IntentKind.NFC_HCE -> {
                val candidates = SYSTEM_SERVICE_ENTRY_DEFINITIONS
                    .filter { it.kind == IntentKind.NFC_HCE }
                    .flatMap { definition ->
                        pm.queryIntentServices(SystemEffectPreview.markedQueryIntent(Intent(definition.action)),
                            PackageManager.GET_META_DATA or PackageManager.MATCH_ALL)
                            .mapNotNull { ri ->
                                val service = ri.serviceInfo ?: return@mapNotNull null
                                if (!validService(service, definition)) return@mapNotNull null
                                val onHost = definition.action ==
                                    "android.nfc.cardemulation.action.HOST_APDU_SERVICE"
                                if (!hasStaticPaymentAidGroup(pm, service, onHost)) return@mapNotNull null
                                SystemProbeItem(
                                    ri.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                                    service.packageName, service.name
                                )
                            }
                    }
                result(R.string.system_probe_source_services, R.string.system_probe_nfc_notice, candidates)
            }
            IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER, IntentKind.NOTIFICATION_LISTENER,
            IntentKind.VPN, IntentKind.PRINT, IntentKind.WALLPAPER, IntentKind.DREAM,
            IntentKind.CALL_SCREENING -> {
                val defs = SYSTEM_SERVICE_ENTRY_DEFINITIONS.filter { it.kind == kind }
                val candidates = defs.flatMap { definition ->
                    pm.queryIntentServices(SystemEffectPreview.markedQueryIntent(Intent(definition.action)),
                        PackageManager.GET_META_DATA or PackageManager.MATCH_ALL)
                        .mapNotNull { ri ->
                            val service = ri.serviceInfo ?: return@mapNotNull null
                            if (!validService(service, definition)) return@mapNotNull null
                            SystemProbeItem(
                                ri.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                                service.packageName, service.name
                            )
                        }
                }
                result(R.string.system_probe_source_services, R.string.system_probe_service_notice,
                    if (kind.isPackageScopedEntry()) candidates.distinctBy { it.packageName }
                    else candidates)
            }
            else -> {
                // Same concrete test intent used by the original Android chooser button.
                val intent = SystemEffectPreview.queryIntent(context, kind, scenario)
                val flags = if (kind == IntentKind.HOME || kind == IntentKind.ASSISTANT)
                    0 else PackageManager.MATCH_DEFAULT_ONLY
                val candidates = pm.queryIntentActivities(intent, flags).mapNotNull { ri ->
                    val activity = ri.activityInfo ?: return@mapNotNull null
                    if (!activity.enabled || activity.applicationInfo?.enabled != true) return@mapNotNull null
                    if (kind == IntentKind.BROWSER && ri.webTargetKind() != IntentKind.BROWSER)
                        return@mapNotNull null
                    SystemProbeItem(
                        ri.loadLabel(pm)?.toString().orEmpty().ifBlank { activity.name },
                        activity.packageName, activity.name
                    )
                }
                result(
                    R.string.system_probe_source_activities,
                    when (kind) {
                        IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT ->
                            R.string.system_probe_role_notice
                        IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT ->
                            R.string.system_probe_limited_notice
                        else -> R.string.system_probe_pm_notice
                    },
                    if (kind.isPackageScopedEntry()) candidates.distinctBy { it.packageName }
                    else candidates
                )
            }
        }
    }

    private fun validService(
        service: ServiceInfo,
        definition: com.yagay.ListCleaner.domain.SystemServiceEntryDefinition,
    ): Boolean = service.enabled && service.applicationInfo?.enabled == true &&
        definition.acceptsPermission(service.permission)

    /** Same manifest payment filter used by ListCleaner's NFC discovery fallback. */
    private fun hasStaticPaymentAidGroup(pm: PackageManager, service: ServiceInfo, onHost: Boolean): Boolean =
        runCatching {
            val meta = if (onHost) "android.nfc.cardemulation.host_apdu_service"
                else "android.nfc.cardemulation.off_host_apdu_service"
            service.loadXmlMetaData(pm, meta)?.use { parser ->
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "aid-group") {
                        val group = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "category"
                        ) ?: parser.getAttributeValue(null, "category")
                        if (group == "payment") return@use true
                    }
                    parser.next()
                }
                false
            } ?: false
        }.getOrDefault(false)

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
                    SystemEffectPreview.markedQueryIntent(
                        Intent("android.service.quicksettings.action.QS_TILE")
                    ),
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
