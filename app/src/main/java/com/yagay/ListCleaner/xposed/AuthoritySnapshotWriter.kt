package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.SharedPreferences
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.SyntheticEntryKeys

/** Builds and persists authoritative snapshots with one canonical record format. */
internal class AuthoritySnapshotWriter(
    preferences: SharedPreferences,
    private val record: (String) -> Unit,
) {
    private val persistence = RemoteObservedEntryPersistence(preferences, record)

    fun replacePackages(
        kind: IntentKind,
        packages: Collection<String>,
        labels: Map<String, String> = emptyMap(),
        publishLive: Boolean = false,
        userId: Int = android.os.Process.myUid() / 100000,
    ): Boolean {
        val now = System.currentTimeMillis()
        val records = packages.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .mapNotNull { packageName ->
                val rule = runCatching { SyntheticEntryKeys.packageScopedRule(kind, packageName) }.getOrNull()
                    ?: return@mapNotNull null
                if (!rule.isValid()) return@mapNotNull null
                if (publishLive) RuntimeObservedEntryStore.observePackage(kind, packageName, userId = userId)
                ObservedEntryRecord(
                    kind = kind.name,
                    packageName = packageName,
                    syntheticClass = rule.className,
                    label = labels[packageName].orEmpty(),
                    observedAt = now,
                    userId = userId,
                ).validatedOrNull()
            }
            .toList()
        val saved = persistence.replaceKind(kind, records, userId)
        if (saved) record("AUTHORITY_SNAPSHOT kind=$kind count=${records.size}")
        return saved
    }

    fun replaceComponents(
        kind: IntentKind,
        components: Collection<ComponentName>,
        labels: Map<ComponentName, String> = emptyMap(),
        publishLive: Boolean = false,
        userId: Int = android.os.Process.myUid() / 100000,
    ): Boolean {
        val now = System.currentTimeMillis()
        val records = components.asSequence()
            .distinct()
            .mapNotNull { component ->
                val rule = ComponentRule(kind, component.packageName, component.className)
                if (!rule.isValid()) return@mapNotNull null
                if (publishLive) RuntimeObservedEntryStore.observeComponent(kind, component, userId = userId)
                ObservedEntryRecord(
                    kind = kind.name,
                    packageName = component.packageName,
                    syntheticClass = component.className,
                    label = labels[component].orEmpty(),
                    activityClass = component.className,
                    observedAt = now,
                    userId = userId,
                ).validatedOrNull()
            }
            .toList()
        val saved = persistence.replaceKind(kind, records, userId)
        if (saved) record("AUTHORITY_SNAPSHOT kind=$kind count=${records.size}")
        return saved
    }
}

