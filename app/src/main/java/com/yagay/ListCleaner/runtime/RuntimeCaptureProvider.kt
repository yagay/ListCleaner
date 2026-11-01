package com.yagay.ListCleaner.runtime

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeCaptureContract
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshotCodec

/**
 * One-way, UID-verified bridge from system_server / the real Resolver process into the
 * manager's private storage. Hook RemotePreferences is intentionally read-only.
 *
 * No provider query, file access or public read route is exposed. App rules are never mutated.
 */
class RuntimeCaptureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != RuntimeCaptureContract.METHOD_PUBLISH) return null
        val ctx = context ?: return null
        val callingUid = Binder.getCallingUid()
        if (!trustedFrameworkUid(callingUid)) return null
        val kind = extras?.getString("kind")?.let {
            IntentKind.entries.firstOrNull { candidate -> candidate.name == it }
        } ?: return null
        val encoded = extras.getString(RuntimeCaptureContract.KEY_SNAPSHOT)
            ?.takeIf { it.isNotBlank() && it.length <= RuntimeCaptureContract.MAX_IPC_CHARS }
            ?: return null
        val snapshot = RuntimeInterfaceSnapshotCodec.decode(encoded, kind) ?: return null
        if (!validCapture(snapshot, callingUid)) return null
        val ordered = snapshot.source in ORDERED_SOURCES
        if (extras.getBoolean(RuntimeCaptureContract.KEY_ORDERED, false) != ordered) return null
        // All default-app roles use the same RoleController eligibility endpoint.
        // Never share their slot with PackageManager or Resolver observations.
        val isRoleEligibility = snapshot.source == "ROLE_CONTROLLER_RETURN" &&
            kind in setOf(IntentKind.ASSISTANT, IntentKind.HOME,
                IntentKind.BROWSER, IntentKind.CALL_SCREENING)
        val key = when {
            ordered -> RuntimeInterfaceSnapshotCodec.keyForResolverOrder(kind)
            isRoleEligibility -> RuntimeInterfaceSnapshotCodec.keyForRoleEligibility(kind)
            else -> RuntimeInterfaceSnapshotCodec.key(kind)
        }
        ctx.getSharedPreferences(RuntimeCaptureContract.PREFS, android.content.Context.MODE_PRIVATE)
            .edit().apply {
                putString(key, encoded)
                if (!ordered && !isRoleEligibility && snapshot.callerUid >= 0) {
                    putString(RuntimeInterfaceSnapshotCodec.keyForCaller(kind, snapshot.callerUid), encoded)
                }
            }.apply()
        return Bundle().apply { putBoolean("accepted", true) }
    }

    private fun trustedFrameworkUid(uid: Int): Boolean {
        if (uid == Process.SYSTEM_UID) return true
        val pm = context?.packageManager ?: return false
        @Suppress("DEPRECATION")
        return pm.getPackagesForUid(uid).orEmpty().any { pkg ->
            if (pkg !in TRUSTED_PROCESS_PACKAGES) return@any false
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                info.uid == uid && (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }.getOrDefault(false)
        }
    }

    private fun validCapture(value: RuntimeInterfaceSnapshot, callingUid: Int): Boolean {
        val now = System.currentTimeMillis()
        if (now - value.capturedAtMillis !in 0L..120_000L) return false
        val ordered = value.source in ORDERED_SOURCES
        if (value.source !in ORDERED_SOURCES && value.source !in RETURN_SOURCES) return false
        if (ordered && value.callerPackage.substringBefore(':') !in
            setOf("android", "system", "com.android.intentresolver")) return false
        if (value.source == "ROLE_CONTROLLER_RETURN") {
            if (value.requestAction != "android.app.role." + value.kind ||
                value.callerPackage.substringBefore(':') !in setOf(
                    "com.android.permissioncontroller", "com.google.android.permissioncontroller"
                )) return false
        }
        // system_server may report calls originating in a different profile.
        if (callingUid != Process.SYSTEM_UID &&
            value.callerUserId != AndroidUid.userId(callingUid)) return false
        return !value.truncated || !ordered // partial observations must not claim final order
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0

    companion object {
        private val ORDERED_SOURCES = setOf("CHOOSER_ADAPTER_ORDER", "RESOLVER_RANKED_ORDER")
        private val RETURN_SOURCES = setOf(
            "PACKAGE_MANAGER_FILTERED_RETURN", "SYSTEM_MANAGER_RETURN",
            "INPUT_METHOD_MENU", "ROLE_CONTROLLER_RETURN",
            "DIRECT_SHARE_SERVICE_RETURN", "NFC_PAYMENT_FILTERED_RETURN",
        )
        private val TRUSTED_PROCESS_PACKAGES = setOf(
            "com.android.intentresolver", "com.android.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.google.android.documentsui", "com.android.systemui", "com.android.nfc",
        )
    }
}
