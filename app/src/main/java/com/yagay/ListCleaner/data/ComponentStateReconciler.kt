package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CancellationException
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.ComponentStatePolicy

data class ComponentReconcileResult(
    val reason: String,
    val persisted: Int,
    val eligible: Int,
    val mismatched: Int,
    val repaired: Int,
    val failed: Int,
    val missing: Int,
    val rootExitCode: Int?,
    val rootTimedOut: Boolean,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val error: String? = null
)

object ComponentReconcileState {
    private const val PREFS = "component_reconcile_state"

    fun save(context: Context, result: ComponentReconcileResult) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("reason", result.reason)
            .putInt("persisted", result.persisted)
            .putInt("eligible", result.eligible)
            .putInt("mismatched", result.mismatched)
            .putInt("repaired", result.repaired)
            .putInt("failed", result.failed)
            .putInt("missing", result.missing)
            .putInt("rootExitCode", result.rootExitCode ?: Int.MIN_VALUE)
            .putBoolean("rootTimedOut", result.rootTimedOut)
            .putLong("startedAtMillis", result.startedAtMillis)
            .putLong("finishedAtMillis", result.finishedAtMillis)
            .putString("error", result.error)
            .apply()
    }

    fun snapshot(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rootExit = prefs.getInt("rootExitCode", Int.MIN_VALUE)
        return buildString {
            appendLine("reason=${prefs.getString("reason", "never")}")
            appendLine("persisted=${prefs.getInt("persisted", 0)}")
            appendLine("eligible=${prefs.getInt("eligible", 0)}")
            appendLine("mismatched=${prefs.getInt("mismatched", 0)}")
            appendLine("repaired=${prefs.getInt("repaired", 0)}")
            appendLine("failed=${prefs.getInt("failed", 0)}")
            appendLine("missing=${prefs.getInt("missing", 0)}")
            appendLine("rootExitCode=${if (rootExit == Int.MIN_VALUE) "none" else rootExit}")
            appendLine("rootTimedOut=${prefs.getBoolean("rootTimedOut", false)}")
            appendLine("startedAtMillis=${prefs.getLong("startedAtMillis", 0)}")
            appendLine("finishedAtMillis=${prefs.getLong("finishedAtMillis", 0)}")
            appendLine("error=${prefs.getString("error", null) ?: "none"}")
        }
    }
}

object ComponentStateReconciler {
    private const val TAG = "ListCleaner.BootReconcile"
    private const val MAX_BATCH = 100

    /**
     * Restore only persisted, user-requested disabled component overrides.
     * Cross-user restore must never resolve another user's components through
     * this application's own PackageManager context.
     *
     * A missing/locked user or partial catalog is not permission to guess:
     * preserve the desired state and retry on the next scheduled reconcile.
     */
    suspend fun reconcile(context: Context, reason: String): ComponentReconcileResult {
        val started = System.currentTimeMillis()
        val app = context.applicationContext as ListCleanerApp
        val store = PersistentComponentStore(app)
        val keys = store.disabledKeys()
        val currentUser = AndroidUid.userId(Process.myUid())
        val refs = keys.asSequence()
            .mapNotNull(PersistentComponentState::parse)
            .distinctBy { PersistentComponentState.key(it.user, it.component) }
            .toList()
        val localRefs = refs.filter { it.user == currentUser }
        val remoteRefs = refs.filter { it.user != currentUser }
        val pm = context.packageManager
        var missing = 0
        var mismatched = 0
        var repaired = 0
        var failed = 0
        var eligible = localRefs.size
        var lastExit: Int? = null
        var timedOut = false
        val errors = mutableListOf<String>()

        // For the local profile, use authoritative PackageManager component
        // overrides. Never infer state from cached UI or a manifest default.
        val localMismatch = mutableListOf<PersistentComponentRef>()
        localRefs.forEach { ref ->
            if (ref.component.packageName in PROTECTED_PACKAGES + context.packageName) {
                failed++
                return@forEach
            }
            if (!componentExists(pm, ref.component)) {
                missing++
                return@forEach
            }
            val observed = runCatching { pm.getComponentEnabledSetting(ref.component) }.getOrNull()
            when (observed) {
                null -> failed++
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> Unit
                else -> localMismatch += ref
            }
        }
        mismatched += localMismatch.size
        if (localMismatch.isNotEmpty()) {
            try {
                localMismatch.chunked(MAX_BATCH).forEach { batch ->
                    val script = buildString {
                        appendLine("test \"\$(id -u)\" = 0 || exit 77")
                        appendLine("rc=0")
                        batch.forEach { ref ->
                            append(ComponentStatePolicy.commandLine(
                                ref.component.packageName, ref.component.className, ref.user, false
                            ))
                            appendLine(" >/dev/null 2>&1 || rc=1")
                        }
                        appendLine("exit \"\$rc\"")
                    }
                    val result = ComponentRootCommand.run(script)
                    lastExit = result.exitCode
                    timedOut = timedOut || result.timedOut
                    if (result.timedOut || result.exitCode != 0) {
                        Log.w(TAG, "LOCAL_ROOT_BATCH_FAILED reason=$reason exit=${result.exitCode}")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errors += "local:${failure.javaClass.simpleName}"
                Log.e(TAG, "Local component reconcile command failed", failure)
            }
            val localRepaired = localMismatch.count { ref ->
                runCatching {
                    pm.getComponentEnabledSetting(ref.component) ==
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }.getOrDefault(false)
            }
            repaired += localRepaired
            failed += localMismatch.size - localRepaired
        }

        if (remoteRefs.isNotEmpty()) {
            // Re-enumerate real Android users. Never use an arbitrary saved ID
            // if the user was deleted or cannot currently be observed.
            try {
                app.spaces.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errors += "users:${failure.javaClass.simpleName}"
                Log.w(TAG, "Android profile enumeration failed", failure)
            }
            val knownUsers = app.spaces.spaces.value.map { it.userId }.toSet()
            val allowed = remoteRefs.filter { it.user in knownUsers }
            val unavailable = remoteRefs.size - allowed.size
            if (unavailable > 0) {
                failed += unavailable
                errors += "unavailable_users=$unavailable"
            }
            val catalog = ProfileComponentCatalog(context)
            allowed.groupBy { it.user }.forEach { (userId, userRefs) ->
                eligible += userRefs.size
                // An incomplete profile scan must not authorize edits.
                val scan = try {
                    catalog.scan(userId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    errors += "user$userId:${failure.javaClass.simpleName}"
                    Log.w(TAG, "Cross-user component scan failed for user $userId", failure)
                    null
                }
                if (scan == null || scan.warning.isNotBlank()) {
                    failed += userRefs.size
                    errors += "user$userId:incomplete_scan"
                    return@forEach
                }
                val byKey = scan.items.associateBy {
                    PersistentComponentState.key(it.user, it.component)
                }
                userRefs.forEach component@{ ref ->
                    if (ref.component.packageName in PROTECTED_PACKAGES + context.packageName) {
                        failed++
                        return@component
                    }
                    val candidate = byKey[PersistentComponentState.key(ref.user, ref.component)]
                    if (candidate == null) {
                        missing++
                        return@component
                    }
                    if (candidate.blocked != null || candidate.overrideState == null ||
                        candidate.enabled == null) {
                        failed++
                        return@component
                    }
                    if (candidate.overrideState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        return@component
                    }
                    mismatched++
                    try {
                        // The catalog rechecks the target user, package and
                        // component immediately before Root mutation and verifies it.
                        catalog.change(candidate, enable = false)
                        repaired++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        failed++
                        errors += "user$userId:restore_failed"
                        Log.w(TAG, "Cross-user restore failed for ${ref.component}", failure)
                    }
                }
            }
        }

        val result = ComponentReconcileResult(
            reason = reason, persisted = keys.size, eligible = eligible,
            mismatched = mismatched, repaired = repaired, failed = failed,
            missing = missing, rootExitCode = lastExit, rootTimedOut = timedOut,
            startedAtMillis = started, finishedAtMillis = System.currentTimeMillis(),
            error = errors.distinct().take(10).joinToString(";").ifEmpty { null }
        )
        ComponentReconcileState.save(context, result)
        store.syncRemote()
        Log.i(TAG, "RECONCILE_DONE reason=$reason persisted=${keys.size} eligible=$eligible " +
            "mismatched=$mismatched repaired=$repaired failed=$failed missing=$missing " +
            "lastExit=$lastExit timedOut=$timedOut error=${result.error ?: "none"}")
        return result
    }

    private val PROTECTED_PACKAGES = setOf("android", "com.android.systemui")

    @Suppress("DEPRECATION")
    private fun componentExists(pm: PackageManager, component: ComponentName): Boolean {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        return runCatching { pm.getServiceInfo(component, flags) }.isSuccess ||
            runCatching { pm.getActivityInfo(component, flags) }.isSuccess ||
            runCatching { pm.getReceiverInfo(component, flags) }.isSuccess
    }
}
