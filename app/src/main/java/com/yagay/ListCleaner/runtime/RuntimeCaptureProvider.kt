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
import com.yagay.ListCleaner.domain.ObservedEntrySnapshot
import com.yagay.ListCleaner.domain.ObservedEntryCacheCodec
import com.yagay.ListCleaner.data.ObservedEntryCache
import com.yagay.ListCleaner.ListCleanerApp
import kotlinx.serialization.json.Json

/**
 * One-way, UID-verified bridge from system_server / the real Resolver process into the
 * manager's private storage. Hook RemotePreferences is intentionally read-only.
 *
 * No provider query, file access or public read route is exposed. App rules are never mutated.
 */
class RuntimeCaptureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == RuntimeCaptureContract.METHOD_PUBLISH_SHORTCUTS) return acceptShortcutSnapshot(extras)
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
        val isRoleEligibility = snapshot.source in setOf(
            "ROLE_CONTROLLER_ORIGINAL", "ROLE_CONTROLLER_RETURN"
        ) && kind in setOf(IntentKind.ASSISTANT, IntentKind.HOME,
            IntentKind.BROWSER, IntentKind.CALL_SCREENING)
        val isNfcPayment = snapshot.source == "NFC_PAYMENT_FILTERED_RETURN" &&
            kind == IntentKind.NFC_HCE
        val key = when {
            ordered -> RuntimeInterfaceSnapshotCodec.keyForResolverOrder(kind)
            snapshot.source == "ROLE_CONTROLLER_ORIGINAL" && isRoleEligibility ->
                RuntimeInterfaceSnapshotCodec.keyForRoleOriginal(kind, snapshot.callerUserId)
            isRoleEligibility -> RuntimeInterfaceSnapshotCodec.keyForRoleFiltered(kind, snapshot.callerUserId)
            isNfcPayment -> RuntimeInterfaceSnapshotCodec.keyForNfcPayment(snapshot.callerUserId)
            else -> RuntimeInterfaceSnapshotCodec.key(kind)
        }
        ctx.getSharedPreferences(RuntimeCaptureContract.PREFS, android.content.Context.MODE_PRIVATE)
            .edit().apply {
                putString(key, encoded)
                // Preserve the older key for readers predating per-user snapshots.
                if (isRoleEligibility && snapshot.source == "ROLE_CONTROLLER_RETURN" &&
                    snapshot.callerUserId == AndroidUid.userId(callingUid)) {
                    putString(RuntimeInterfaceSnapshotCodec.keyForRoleEligibility(kind), encoded)
                }
                if (!ordered && !isRoleEligibility && !isNfcPayment &&
                    snapshot.callerUid >= 0) {
                    putString(RuntimeInterfaceSnapshotCodec.keyForCaller(kind, snapshot.callerUid), encoded)
                }
            }.apply()
        PreviewCaptureSessionStore.recordIfMatching(ctx, snapshot, encoded)
        return Bundle().apply { putBoolean("accepted", true) }
    }

    /**
     * Only a preinstalled, recognized HOME package may publish a complete shortcut snapshot.
     * Validate the Binder UID, not a caller-supplied package name. Cross-profile rows are
     * permitted only from this trusted launcher and never grant rule mutation.
     */
    private fun acceptShortcutSnapshot(extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val uid = Binder.getCallingUid()
        val launcher = extras?.getString(RuntimeCaptureContract.KEY_LAUNCHER_PACKAGE)
            ?: return null
        if (launcher !in TRUSTED_LAUNCHERS || !trustedSystemPackage(uid, launcher)) return null
        val user = extras.getInt(RuntimeCaptureContract.KEY_SHORTCUT_USER, -1)
        if (user !in 0..21474) return null
        val encoded = extras.getString(RuntimeCaptureContract.KEY_SHORTCUTS)
            ?.takeIf { it.isNotBlank() && it.length <= RuntimeCaptureContract.MAX_SHORTCUT_IPC_CHARS }
            ?: return null
        val payload = runCatching {
            Json.decodeFromString(ObservedEntrySnapshot.serializer(), encoded)
        }.getOrNull() ?: return null
        if (payload.version != 1 || payload.entries.size > ObservedEntryCacheCodec.MAX_ENTRIES ||
            payload.entries.any { it.kind != IntentKind.SHORTCUT_ITEM.name ||
                it.userId != user || it.validatedOrNull() == null }) return null
        val saved = ObservedEntryCache(ctx).replaceLauncherSnapshot(user, payload.entries)
        // Invalidate only after the manager has accepted the complete snapshot, so
        // the currently displayed space refreshes without a manual rescan.
        if (saved) (ctx.applicationContext as? ListCleanerApp)?.catalog?.invalidate()
        return Bundle().apply { putBoolean("accepted", saved) }
    }

    private fun trustedSystemPackage(uid: Int, packageName: String): Boolean {
        val pm = context?.packageManager ?: return false
        if (packageName !in pm.getPackagesForUid(uid).orEmpty()) return false
        return runCatching {
            @Suppress("DEPRECATION")
            val info = pm.getApplicationInfo(packageName, 0)
            info.uid == uid && info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(false)
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
        if (value.source in setOf("ROLE_CONTROLLER_ORIGINAL", "ROLE_CONTROLLER_RETURN")) {
            if (value.requestAction != "android.app.role." + value.kind ||
                value.callerPackage.substringBefore(':') !in setOf(
                    "com.android.permissioncontroller", "com.google.android.permissioncontroller"
                )) return false
        }
        val isNfcPayment = value.source == "NFC_PAYMENT_FILTERED_RETURN"
        if (isNfcPayment && (value.kind != IntentKind.NFC_HCE.name ||
                value.callerPackage.substringBefore(':') != "com.android.nfc" ||
                value.requestAction != "android.nfc.cardemulation.payment" ||
                value.callerUserId !in 0..21474)) return false
        // system_server may report calls originating in a different profile.
        // NFC runs as one trusted service process but queries distinct personal and
        // managed-profile userIds. Never force the queried profile to equal its process UID.
        if (!isNfcPayment && callingUid != Process.SYSTEM_UID &&
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
        private val TRUSTED_LAUNCHERS = setOf(
            "com.android.launcher", "com.android.launcher3",
            "com.google.android.apps.nexuslauncher", "com.miui.home",
            "com.sec.android.app.launcher",
        )
        private val ORDERED_SOURCES = setOf("CHOOSER_ADAPTER_ORDER", "RESOLVER_RANKED_ORDER")
        private val RETURN_SOURCES = setOf(
            "PACKAGE_MANAGER_FILTERED_RETURN", "SYSTEM_MANAGER_RETURN",
            "INPUT_METHOD_MENU", "ROLE_CONTROLLER_ORIGINAL", "ROLE_CONTROLLER_RETURN",
            "DIRECT_SHARE_SERVICE_RETURN", "NFC_PAYMENT_FILTERED_RETURN",
        )
        private val TRUSTED_PROCESS_PACKAGES = setOf(
            "com.android.intentresolver", "com.android.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.google.android.documentsui", "com.android.systemui", "com.android.nfc",
        )
    }
}
