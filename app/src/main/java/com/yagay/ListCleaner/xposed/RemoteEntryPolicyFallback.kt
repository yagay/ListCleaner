package com.yagay.ListCleaner.xposed

import android.content.SharedPreferences
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ModuleConfig
import kotlinx.serialization.json.Json

/** Shared RemotePreferences fallback used until Runtime Probe v2 publishes an authoritative snapshot. */
internal class RemoteEntryPolicyFallback(
    private val preferences: SharedPreferences,
    private val selectRules: (ModuleConfig) -> Set<String>,
    private val selectPriorities: (ModuleConfig) -> Map<IntentKind, List<String>>,
    private val record: (String) -> Unit,
) {
    data class Snapshot(
        val managerAppId: Int = -1,
        val displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
        val rules: Set<String> = emptySet(),
        val priorities: Map<IntentKind, List<String>> = emptyMap(),
    )

    @Volatile private var value = Snapshot()
    private val json = Json { ignoreUnknownKeys = true }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == RuleRepository.KEY_CONFIG) refresh("preference changed")
    }

    fun start() {
        runCatching { preferences.registerOnSharedPreferenceChangeListener(listener) }
            .onFailure { record("POLICY_LISTENER_FAILED error=${it.javaClass.name}") }
        refresh("init")
    }

    fun snapshot(): Snapshot = value

    @Synchronized
    private fun refresh(reason: String) {
        runCatching {
            val encoded = preferences.getString(RuleRepository.KEY_CONFIG, null) ?: return@runCatching
            if (encoded.length > RuleRepository.MAX_BACKUP_CHARS) return@runCatching
            val config = json.decodeFromString(ModuleConfig.serializer(), encoded).validated()
            value = Snapshot(
                managerAppId = config.managerAppId,
                displayMode = config.mode,
                rules = selectRules(config).toSet(),
                priorities = selectPriorities(config).mapValues { (_, packages) -> packages.toList() },
            )
            record("POLICY_READ reason=$reason rules=${value.rules.size}")
        }.onFailure {
            record("POLICY_READ_FAILED reason=$reason error=${it.javaClass.name}")
        }
    }
}
