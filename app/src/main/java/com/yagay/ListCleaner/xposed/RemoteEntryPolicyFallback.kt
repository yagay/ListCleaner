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

    // One immutable generation prevents adapters from reading one user's rules from a
    // different configuration generation.
    @Volatile private var values: Map<Int, Snapshot> = mapOf(0 to Snapshot())
    private val json = Json { ignoreUnknownKeys = true }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == RuleRepository.KEY_CONFIG) refresh("preference changed")
    }

    fun start() {
        runCatching { preferences.registerOnSharedPreferenceChangeListener(listener) }
            .onFailure { record("POLICY_LISTENER_FAILED error=${it.javaClass.name}") }
        refresh("init")
    }

    fun snapshot(userId: Int = 0): Snapshot {
        val current = values
        return current[userId] ?: Snapshot(
            managerAppId = current[0]?.managerAppId ?: -1,
            displayMode = DisplayMode.SHOW_ALL,
        )
    }

    @Synchronized
    private fun refresh(reason: String) {
        runCatching {
            val encoded = preferences.getString(RuleRepository.KEY_CONFIG, null)
            if (encoded == null) {
                values = mapOf(0 to Snapshot())
                record("POLICY_CLEARED reason=$reason source=config_missing")
                return@runCatching
            }
            require(encoded.length <= RuleRepository.MAX_BACKUP_CHARS) { "Config too large" }
            val config = json.decodeFromString(ModuleConfig.serializer(), encoded).validated()
            fun compile(single: ModuleConfig): Snapshot = Snapshot(
                managerAppId = config.managerAppId,
                displayMode = single.mode,
                rules = selectRules(single).toSet(),
                priorities = selectPriorities(single).mapValues { (_, packages) -> packages.toList() },
            )
            val next = linkedMapOf(0 to compile(config))
            config.userSpaces.forEach { (userId, settings) ->
                next[userId] = compile(settings.toModuleConfig())
            }
            values = next
            record("POLICY_READ reason=$reason users=${next.keys} primaryRules=${next[0]?.rules?.size}")
        }.onFailure {
            // Fallback filtering is destructive. Invalid/missing policy must fail open instead of
            // retaining a stale snapshot after the manager has reset or replaced its config.
            values = mapOf(0 to Snapshot())
            record("POLICY_READ_FAILED reason=$reason error=${it.javaClass.name} cleared=true")
        }
    }
}
