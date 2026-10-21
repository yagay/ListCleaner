package com.yagay.ListCleaner.ui

import android.content.Context
import com.yagay.ListCleaner.domain.IntentKind

/** Small manager-local request marker. Never reads or writes user file contents. */
internal class PickerProbeSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "system_picker_probe_session", Context.MODE_PRIVATE
    )

    fun read(): PickerProbeSession? {
        val kind = prefs.getString(KEY_KIND, null)
            ?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() } ?: return null
        val scenario = prefs.getString(KEY_SCENARIO, null)
            ?.let { runCatching { SystemProbeScenario.valueOf(it) }.getOrNull() } ?: return null
        val started = prefs.getLong(KEY_STARTED, 0)
        val user = prefs.getInt(KEY_USER, -1)
        if (started <= 0 || user < 0) return null
        return PickerProbeSession(kind, scenario, user, started)
    }

    fun begin(session: PickerProbeSession) {
        prefs.edit().putString(KEY_KIND, session.kind.name)
            .putString(KEY_SCENARIO, session.scenario.name)
            .putInt(KEY_USER, session.userId)
            .putLong(KEY_STARTED, session.startedAtMillis)
            .commit()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_KIND = "kind"
        const val KEY_SCENARIO = "scenario"
        const val KEY_USER = "user"
        const val KEY_STARTED = "started"
    }
}
