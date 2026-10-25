package com.yagay.ListCleaner.data

import android.content.SharedPreferences
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PackageIdentity
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.VisibilityCompatConfig
import com.yagay.ListCleaner.domain.VisibilityScope
import kotlinx.serialization.json.Json

/** Decode the pre-atomic RemotePreferences mirror used by older List Cleaner builds. */
internal fun readLegacyRemoteConfig(
    prefs: SharedPreferences,
    json: Json,
): ModuleConfig? {
    val hasLegacy = RuleRepository.SYNCED_KEYS.any(prefs::contains)
    if (!hasLegacy) return null

    val rules = decodeLegacyRuleIds(
        prefs.getStringSet(RuleRepository.KEY_RULES, emptySet()).orEmpty()
    )
    val mode = DisplayMode.fromStored(
        prefs.getString(RuleRepository.KEY_DISPLAY_MODE, null),
        prefs.getBoolean(RuleRepository.KEY_BLACKLIST, true),
    )
    val priorities = json.decodeFromString(
        PriorityConfig.serializer(),
        prefs.getString(RuleRepository.KEY_PRIORITIES, null) ?: "{}",
    ).validated()
    val openTypes = json.decodeFromString(
        OpenTypeConfig.serializer(),
        prefs.getString(RuleRepository.KEY_OPEN_TYPES, null) ?: "{}",
    ).validated()
    val browserLinks = json.decodeFromString(
        BrowserLinkConfig.serializer(),
        prefs.getString(RuleRepository.KEY_BROWSER_LINKS, null) ?: "{}",
    ).validated()
    val hiddenFromApps = prefs.getStringSet(RuleRepository.KEY_HIDDEN_FROM_APPS, emptySet())
        .orEmpty()
        .asSequence()
        .map(String::trim)
        .filter { it != "android" && it != "com.yagay.ListCleaner" && PackageIdentity.valid(it) }
        .take(2_000)
        .toSet()
    val visibilityScopes = prefs.getStringSet(RuleRepository.KEY_VISIBILITY_SCOPES, emptySet())
        .orEmpty()
        .mapNotNull { name -> runCatching { VisibilityScope.valueOf(name) }.getOrNull() }
        .toSet()

    return ModuleConfig(
        rules = rules,
        mode = mode,
        priorities = priorities,
        diagnostic = prefs.getBoolean(RuleRepository.KEY_DIAGNOSTIC, false),
        hiddenFromApps = hiddenFromApps,
        openTypes = openTypes,
        browserLinks = browserLinks,
        visibilityCompat = VisibilityCompatConfig(scopes = visibilityScopes),
    ).validated()
}

/** Retired LAUNCHER_SHORTCUT rows are intentionally discarded; malformed active rows stay fatal. */
internal fun decodeLegacyRuleIds(values: Collection<String>): Set<ComponentRule> =
    values.mapNotNullTo(linkedSetOf()) { id ->
        if (id.substringBefore('|') == IntentKind.LAUNCHER_SHORTCUT.name) {
            null
        } else {
            requireNotNull(ComponentRule.fromId(id)) { "invalid_legacy_component_rule" }
        }
    }
