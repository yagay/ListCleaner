package com.yagay.ListCleaner.runtime

import android.content.Context
import com.yagay.ListCleaner.domain.CapturedSettingsPage
import com.yagay.ListCleaner.domain.SettingsPageCaptureProtocol
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Manager-owned, opt-in page discovery. Captures expire automatically. */
object SettingsPageCaptureStore {
    private const val START = "started"
    private const val ITEMS = "pages"
    private val serializer = ListSerializer(CapturedSettingsPage.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) = context.getSharedPreferences(
        SettingsPageCaptureProtocol.PREFS, Context.MODE_PRIVATE
    )

    @Synchronized
    fun begin(context: Context): Boolean = prefs(context).edit()
        .putLong(START, System.currentTimeMillis())
        .putString(ITEMS, "[]")
        .commit()

    @Synchronized
    fun stop(context: Context) {
        prefs(context).edit().remove(START).apply()
    }

    fun active(context: Context): Boolean {
        val started = prefs(context).getLong(START, 0)
        val age = System.currentTimeMillis() - started
        return started > 0 && age in 0L..SettingsPageCaptureProtocol.MAX_AGE_MS
    }

    fun pages(context: Context): List<CapturedSettingsPage> =
        runCatching {
            json.decodeFromString(serializer, prefs(context).getString(ITEMS, "[]") ?: "[]")
                .filter(SettingsPageCaptureProtocol::valid)
                .take(SettingsPageCaptureProtocol.MAX_ITEMS)
        }.getOrDefault(emptyList())

    /** Called only by the UID-verified RuntimeCaptureProvider, not from the Hook directly. */
    @Synchronized
    fun record(context: Context, page: CapturedSettingsPage): Boolean {
        if (!active(context) || !SettingsPageCaptureProtocol.valid(page)) return false
        val age = System.currentTimeMillis() - page.capturedAtMillis
        if (age !in 0L..30_000L) return false
        val previous = pages(context)
        val updated = (listOf(page) + previous.filterNot {
            it.packageName == page.packageName && it.activityClass == page.activityClass &&
                it.fragmentClass == page.fragmentClass && it.action == page.action &&
                it.roleName == page.roleName
        }).take(SettingsPageCaptureProtocol.MAX_ITEMS)
        prefs(context).edit().putString(ITEMS, json.encodeToString(serializer, updated)).apply()
        return true
    }
}
