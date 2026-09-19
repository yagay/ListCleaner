package com.yagay.ListCleaner.runtime

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.util.Log
import com.yagay.ListCleaner.data.ComponentStateReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ComponentReconcileJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val reason = params.extras.getString(EXTRA_REASON, "scheduled")
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                ComponentStateReconciler.reconcile(applicationContext, reason)
            } catch (failure: Throwable) {
                Log.e(TAG, "RECONCILE_JOB_FAILED reason=$reason", failure)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeJob?.cancel()
        activeJob = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ListCleaner.BootReconcile"
        private const val JOB_ID = 0x4C4301
        private const val EXTRA_REASON = "reason"

        fun schedule(context: Context, reason: String) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val delay = when (reason) {
                "boot_completed" -> 5_000L
                "user_unlocked" -> 8_000L
                "manager_replaced" -> 2_000L
                "package_added", "package_replaced" -> 3_000L
                else -> 3_000L
            }
            val extras = PersistableBundle().apply {
                putString(EXTRA_REASON, reason)
            }
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, ComponentReconcileJobService::class.java)
            )
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay + 15_000L)
                .setExtras(extras)
                .build()
            val result = scheduler.schedule(info)
            Log.i(TAG, "JOB_SCHEDULED reason=$reason delayMs=$delay result=$result")
        }
    }
}
