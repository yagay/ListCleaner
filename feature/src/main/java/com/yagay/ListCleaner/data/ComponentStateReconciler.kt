package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp
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
    private const val PER_USER_RANGE = 100_000
    private const val MAX_BATCH = 100

    fun reconcile(context: Context, reason: String): ComponentReconcileResult {
        val started = System.currentTimeMillis()
        val app = context.applicationContext as ListCleanerApp
        val store = PersistentComponentStore(app)
        val keys = store.disabledKeys()
        val currentUser = Process.myUid() / PER_USER_RANGE
        val refs = keys.asSequence()
            .mapNotNull(PersistentComponentState::parse)
            .filter { it.user == currentUser }
            .distinctBy { PersistentComponentState.key(it.user, it.component) }
            .toList()

        val pm = context.packageManager
        var missing = 0
        val mismatched = refs.filter { ref ->
            if (!componentExists(pm, ref.component)) {
                missing++
                false
            } else {
                runCatching {
                    pm.getComponentEnabledSetting(ref.component) !=
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }.getOrDefault(false)
            }
        }

        if (mismatched.isEmpty()) {
            val result = ComponentReconcileResult(
                reason, keys.size, refs.size, 0, 0, 0, missing,
                null, false, started, System.currentTimeMillis()
            )
            ComponentReconcileState.save(context, result)
            store.syncRemote()
            Log.i(TAG, "RECONCILE_OK reason=$reason persisted=${keys.size} eligible=${refs.size} repaired=0 missing=$missing")
            return result
        }

        var lastExit: Int? = null
        var timedOut = false
        var launchError: String? = null

        try {
            mismatched.chunked(MAX_BATCH).forEach { batch ->
                val script = buildString {
                    appendLine("test \"\$(id -u)\" = 0 || exit 77")
                    batch.forEach { ref ->
                        append(ComponentStatePolicy.commandLine(
                            ref.component.packageName,
                            ref.component.className,
                            ref.user,
                            false
                        ))
                        appendLine(" >/dev/null 2>&1 || true")
                    }
                }
                val result = ComponentRootCommand.run(script)
                lastExit = result.exitCode
                timedOut = timedOut || result.timedOut
                if (result.timedOut || result.exitCode != 0) {
                    Log.w(TAG, "ROOT_BATCH_FAILED reason=$reason exit=${result.exitCode} timeout=${result.timedOut}")
                }
            }
        } catch (failure: Exception) {
            launchError = failure.javaClass.name
            Log.e(TAG, "Root reconcile invocation failed", failure)
        }

        val repaired = mismatched.count { ref ->
            runCatching {
                pm.getComponentEnabledSetting(ref.component) ==
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }.getOrDefault(false)
        }
        val failed = mismatched.size - repaired

        val result = ComponentReconcileResult(
            reason = reason,
            persisted = keys.size,
            eligible = refs.size,
            mismatched = mismatched.size,
            repaired = repaired,
            failed = failed,
            missing = missing,
            rootExitCode = lastExit,
            rootTimedOut = timedOut,
            startedAtMillis = started,
            finishedAtMillis = System.currentTimeMillis(),
            error = launchError
        )
        ComponentReconcileState.save(context, result)
        store.syncRemote()
        Log.i(
            TAG,
            "RECONCILE_DONE reason=$reason persisted=${keys.size} eligible=${refs.size} " +
                "mismatched=${mismatched.size} repaired=$repaired failed=$failed missing=$missing " +
                "rootExit=$lastExit timedOut=$timedOut error=${launchError ?: "none"}"
        )
        return result
    }

    @Suppress("DEPRECATION")
    private fun componentExists(pm: PackageManager, component: ComponentName): Boolean {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        return runCatching { pm.getServiceInfo(component, flags) }.isSuccess ||
            runCatching { pm.getActivityInfo(component, flags) }.isSuccess ||
            runCatching { pm.getReceiverInfo(component, flags) }.isSuccess
    }
}
