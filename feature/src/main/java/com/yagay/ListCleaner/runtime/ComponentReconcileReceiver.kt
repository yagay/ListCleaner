package com.yagay.ListCleaner.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp

class ComponentReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED -> {
                (context.applicationContext as? ListCleanerApp)?.catalog?.invalidate(packageName)
                Log.i(TAG, "CATALOG_INVALIDATED action=${intent.action} package=${packageName ?: "none"}")
            }
        }

        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "boot_completed"
            Intent.ACTION_USER_UNLOCKED -> "user_unlocked"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "manager_replaced"
            Intent.ACTION_PACKAGE_ADDED -> "package_added"
            Intent.ACTION_PACKAGE_REPLACED -> "package_replaced"
            // Removal only invalidates discovery caches. There is nothing to re-disable while the
            // package is absent; a reinstall/update will schedule reconciliation on add/replace.
            Intent.ACTION_PACKAGE_REMOVED -> return
            else -> return
        }
        Log.i(TAG, "SCHEDULE reason=$reason package=${packageName ?: "none"}")
        ComponentReconcileJobService.schedule(context, reason)
    }

    private companion object {
        const val TAG = "ListCleaner.BootReconcile"
    }
}
