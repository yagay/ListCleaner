package com.yagay.ListCleaner.runtime

import android.content.Context
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeCaptureContract
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshotCodec
import java.util.UUID

/**
 * Manager-owned, short-lived correlation between one native system test and Hook returns.
 * The framework Hook never needs to read manager private preferences: the trusted capture
 * provider attaches a matching return to the active session when it receives the result.
 * No candidate is inferred from a session alone.
 */
internal object PreviewCaptureSessionStore {
    private const val PREFIX = "preview_capture_session_"
    private const val MAX_AGE_MS = 120_000L

    fun begin(
        context: Context, kind: IntentKind, userId: Int,
        action: String?, mime: String?, host: String?,
        startedAt: Long = System.currentTimeMillis(),
    ): String {
        val id = UUID.randomUUID().toString()
        val saved = prefs(context).edit()
            .putString(PREFIX + "id", id)
            .putString(PREFIX + "kind", kind.name)
            .putInt(PREFIX + "user", userId)
            .putString(PREFIX + "action", action.orEmpty())
            .putString(PREFIX + "mime", mime.orEmpty())
            .putString(PREFIX + "host", host.orEmpty())
            .putLong(PREFIX + "started", startedAt)
            .commit()
        return if (saved) id else ""
    }

    /** Called by the UID-verified receiver, not directly by hooked processes. */
    fun recordIfMatching(context: Context, snapshot: RuntimeInterfaceSnapshot, encoded: String) {
        val prefs = prefs(context)
        val id = prefs.getString(PREFIX + "id", null) ?: return
        val started = prefs.getLong(PREFIX + "started", 0L)
        if (started <= 0 || snapshot.capturedAtMillis - started !in 0L..MAX_AGE_MS) return
        if (snapshot.kind != prefs.getString(PREFIX + "kind", null) ||
            snapshot.callerUserId != prefs.getInt(PREFIX + "user", -1) ||
            snapshot.requestAction != prefs.getString(PREFIX + "action", "") ||
            snapshot.requestMimeType != prefs.getString(PREFIX + "mime", "") ||
            snapshot.requestHost != prefs.getString(PREFIX + "host", "")) return
        prefs.edit().putString(PREFIX + "result_" + id, encoded).apply()
    }

    fun read(context: Context, id: String?, kind: IntentKind): RuntimeInterfaceSnapshot? {
        if (id.isNullOrBlank() || id.length > 64) return null
        return RuntimeInterfaceSnapshotCodec.decode(
            prefs(context).getString(PREFIX + "result_" + id, null), kind
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(
        RuntimeCaptureContract.PREFS, Context.MODE_PRIVATE,
    )
}
