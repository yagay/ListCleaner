package com.yagay.ListCleaner.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.EmbeddedDirectShareProfiles
import com.yagay.ListCleaner.domain.IntentKind

data class ResolverHost(
    val packageName: String,
    val className: String,
    val processName: String,
    val scenarios: Set<String>
) {
    val requiresManualScope: Boolean get() = when {
        packageName == "system" -> false
        packageName == "com.android.intentresolver" -> false
        packageName == "com.android.systemui" -> false
        packageName in ResolverScopeDetector.AUTHORITY_PACKAGES -> false
        className == ResolverScopeDetector.ROLE_CONTROLLER_SERVICE_INTERFACE -> false
        EmbeddedDirectShareProfiles.isKnownHost(packageName) -> false
        packageName == "android" -> processName !in ResolverScopeDetector.FRAMEWORK_UI_PROCESSES
        else -> true
    }
}

data class ScopeDetection(
    val hosts: List<ResolverHost> = emptyList(),
    val installedCandidates: Set<String> = emptySet(),
    val warnings: List<String> = emptyList()
) {
    val recommended: Set<String> get() = hosts.filterNot { it.requiresManualScope }.map { it.packageName }.toSet()
}

/** Resolve probes without launching activities. Ordinary default handlers are not Resolver hosts. */
class ResolverScopeDetector(private val context: Context) {
    @Suppress("DEPRECATION")
    fun detect(): ScopeDetection {
        val pm = context.packageManager
        val warnings = mutableListOf<String>()
        val installedInfo = KNOWN_PACKAGES.mapNotNull { packageName ->
            try {
                packageName to pm.getApplicationInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (failure: Exception) {
                Log.e(TAG, "Installed-host detection failed for $packageName", failure)
                warnings += context.getString(R.string.scope_detection_failed, packageName)
                null
            }
        }.toMap()
        val installed = installedInfo.keys

        val probes = listOf(
            context.getString(R.string.scope_scenario_system_share_sheet) to
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain"), null),
            context.getString(R.string.scope_scenario_share) to Intent(Intent.ACTION_SEND).setType("image/*"),
            context.getString(R.string.scope_scenario_share_multiple) to Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*"),
            context.getString(R.string.scope_scenario_open_file) to Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://com.yagay.ListCleaner.placeholder/item"),
                "application/pdf"
            ),
            context.getString(R.string.scope_scenario_web_link) to Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://example.com")
            ).addCategory(Intent.CATEGORY_BROWSABLE),
            context.getString(R.string.scope_scenario_process_text) to Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        )
        val resolverHosts = probes.mapNotNull { (scenario, intent) ->
            try {
                val flags = if (intent.action == Intent.ACTION_PROCESS_TEXT) {
                    IntentCatalog.queryFlags(IntentKind.PROCESS_TEXT, discovery = false)
                } else PackageManager.MATCH_DEFAULT_ONLY
                val info = pm.resolveActivity(intent, flags)?.activityInfo ?: return@mapNotNull null
                val system = info.applicationInfo.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val activityName = info.targetActivity ?: info.name
                val resolver = info.packageName == "com.android.intentresolver" ||
                    activityName.endsWith("ResolverActivity") || activityName.endsWith("ChooserActivity")
                if (!system || !resolver) return@mapNotNull null
                ResolverHost(
                    info.packageName,
                    info.name,
                    info.processName ?: info.applicationInfo.processName ?: info.packageName,
                    setOf(scenario)
                )
            } catch (failure: Exception) {
                Log.e(TAG, "Resolver probe failed for $scenario", failure)
                warnings += context.getString(R.string.scope_detection_failed, scenario)
                null
            }
        }.groupBy { it.packageName to it.className }.values.map { entries ->
            entries.first().copy(scenarios = entries.flatMap { it.scenarios }.toSet())
        }

        val embeddedShareHosts = EmbeddedDirectShareProfiles.all.flatMap { profile ->
            profile.packages.filter { it in installed }.map { packageName ->
                ResolverHost(
                    packageName = packageName,
                    className = profile.shareActivityClasses.firstOrNull()
                        ?: profile.adapterClasses.firstOrNull()
                        ?: profile.id,
                    processName = installedInfo[packageName]?.processName ?: packageName,
                    scenarios = setOf(context.getString(R.string.scope_scenario_share)),
                )
            }
        }

        val roleControllerHosts = runCatching {
            pm.queryIntentServices(
                Intent(ROLE_CONTROLLER_SERVICE_INTERFACE),
                PackageManager.MATCH_SYSTEM_ONLY,
            ).mapNotNull { resolveInfo ->
                val info = resolveInfo.serviceInfo ?: return@mapNotNull null
                ResolverHost(
                    packageName = info.packageName,
                    className = ROLE_CONTROLLER_SERVICE_INTERFACE,
                    processName = info.processName ?: info.applicationInfo?.processName ?: info.packageName,
                    scenarios = emptySet(),
                )
            }
        }.onFailure { failure ->
            Log.e(TAG, "Role-controller detection failed", failure)
            warnings += context.getString(R.string.scope_detection_failed, ROLE_CONTROLLER_SERVICE_INTERFACE)
        }.getOrDefault(emptyList())

        val knownRoleControllerHosts = ROLE_CONTROLLER_PACKAGES
            .filter { it in installed }
            .map { packageName ->
                ResolverHost(
                    packageName = packageName,
                    className = ROLE_CONTROLLER_SERVICE_INTERFACE,
                    processName = installedInfo[packageName]?.processName ?: packageName,
                    scenarios = emptySet(),
                )
            }

        val roleHosts = (roleControllerHosts + knownRoleControllerHosts).distinctBy { it.packageName }
        val authorityHosts = AUTHORITY_PACKAGES.filter { it in installed }.map { packageName ->
            ResolverHost(
                packageName = packageName,
                className = when (packageName) {
                    "com.android.nfc" -> "CardEmulationManager"
                    else -> "CombinedProviderInfo"
                },
                processName = installedInfo[packageName]?.processName ?: packageName,
                scenarios = emptySet(),
            )
        }

        val systemHost = ResolverHost(
            "system",
            "PackageManagerService",
            "system",
            setOf(context.getString(R.string.scope_scenario_global_intent))
        )
        return ScopeDetection(
            hosts = listOf(systemHost) + resolverHosts + embeddedShareHosts + roleHosts + authorityHosts,
            installedCandidates = installed + roleHosts.map { it.packageName } + authorityHosts.map { it.packageName } + "system",
            warnings = warnings,
        )
    }

    companion object {
        const val ROLE_CONTROLLER_SERVICE_INTERFACE = "android.app.role.RoleControllerService"
        private val ROLE_CONTROLLER_PACKAGES = setOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
        val AUTHORITY_PACKAGES = setOf("com.android.settings", "com.android.nfc")
        private val STANDARD_PACKAGES = setOf("android", "com.android.intentresolver", "com.android.systemui")
        val KNOWN_PACKAGES: Set<String> =
            STANDARD_PACKAGES + EmbeddedDirectShareProfiles.knownPackages + ROLE_CONTROLLER_PACKAGES + AUTHORITY_PACKAGES
        val FRAMEWORK_UI_PROCESSES = setOf("android:ui", "system:ui")
        private const val TAG = "ListCleaner.Scope"
    }
}
