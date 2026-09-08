package com.yagay.ListCleaner.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.IntentKind

data class ResolverHost(
    val packageName: String,
    val className: String,
    val processName: String,
    val scenarios: Set<String>
) {
    val requiresManualScope: Boolean get() = when (packageName) {
        "system" -> false
        "com.android.intentresolver" -> false
        "com.android.systemui" -> false
        "android" -> processName !in ResolverScopeDetector.FRAMEWORK_UI_PROCESSES
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
        val installed = KNOWN_PACKAGES.filter { packageName ->
            try {
                // This set means installed, not enabled or confirmed as a Resolver host.
                pm.getApplicationInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (failure: Exception) {
                warnings += context.getString(
                    R.string.scope_detection_failed,
                    packageName,
                    failure.message ?: failure.javaClass.simpleName
                )
                false
            }
        }.toSet()
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
                // A framework package is not itself evidence of a Resolver. Check the
                // component (including aliases) before recommending the android scope.
                val activityName = info.targetActivity ?: info.name
                val resolver = info.packageName == "com.android.intentresolver" ||
                    activityName.endsWith("ResolverActivity") || activityName.endsWith("ChooserActivity")
                // resolveActivity already applied dynamic component/app state for this user.
                // A false manifest default does not mean the resolved component is disabled.
                if (!system || !resolver) return@mapNotNull null
                ResolverHost(
                    info.packageName,
                    info.name,
                    info.processName ?: info.applicationInfo.processName ?: info.packageName,
                    setOf(scenario)
                )
            } catch (failure: Exception) {
                warnings += context.getString(
                    R.string.scope_detection_failed,
                    scenario,
                    failure.message ?: failure.javaClass.simpleName
                )
                null
            }
        }.groupBy { it.packageName to it.className }.values.map { entries ->
            entries.first().copy(scenarios = entries.flatMap { it.scenarios }.toSet())
        }
        // Modern LSPosed exposes system_server as the virtual `system` scope. It is not an
        // installed APK and therefore cannot be discovered through PackageManager.
        val systemHost = ResolverHost(
            "system",
            "PackageManagerService",
            "system",
            setOf(context.getString(R.string.scope_scenario_global_intent))
        )
        return ScopeDetection(listOf(systemHost) + resolverHosts, installed + "system", warnings)
    }

    companion object {
        val KNOWN_PACKAGES = setOf("android", "com.android.intentresolver", "com.android.systemui")
        val FRAMEWORK_UI_PROCESSES = setOf("android:ui", "system:ui")
    }
}
