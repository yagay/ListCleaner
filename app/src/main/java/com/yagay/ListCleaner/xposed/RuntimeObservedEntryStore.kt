package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.pm.ShortcutInfo
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import java.util.concurrent.ConcurrentHashMap

internal data class ObservedShortcutEntry(
    val kind: IntentKind,
    val packageName: String,
    val syntheticClass: String,
    val label: String,
    val activityClass: String?,
    val observedAt: Long,
)

/** Process-local observations from ShortcutService, bounded and privacy-minimized. */
internal object RuntimeObservedEntryStore {
    private const val MAX_ENTRIES = 1024
    private const val MAX_LABEL_CHARS = 160
    private const val MAX_ID_CHARS = 300
    private val entries = ConcurrentHashMap<String, ObservedShortcutEntry>()

    fun observeShortcut(shortcut: ShortcutInfo) {
        observe(IntentKind.SHORTCUT_ITEM, shortcut, shortcut.activity)
    }

    fun observeDirectShare(shortcut: ShortcutInfo, target: ComponentName?) {
        observe(IntentKind.DIRECT_SHARE, shortcut, target ?: shortcut.activity)
    }

    private fun observe(kind: IntentKind, shortcut: ShortcutInfo, component: ComponentName?) {
        val packageName = shortcut.`package`?.takeIf { it.isNotBlank() } ?: return
        val shortcutId = shortcut.id?.takeIf { it.isNotBlank() && it.length <= MAX_ID_CHARS } ?: return
        val targetClass = component?.className
        val synthetic = when (kind) {
            IntentKind.DIRECT_SHARE -> SyntheticEntryKeys.directShareClass(targetClass, shortcutId)
            else -> SyntheticEntryKeys.shortcutItemClass(targetClass, shortcutId)
        }
        val label = shortcut.shortLabel?.toString()?.take(MAX_LABEL_CHARS).orEmpty()
        val entry = ObservedShortcutEntry(
            kind = kind,
            packageName = packageName,
            syntheticClass = synthetic,
            label = label,
            activityClass = targetClass,
            observedAt = System.currentTimeMillis(),
        )
        val key = "${kind.name}|$packageName|$synthetic"
        entries[key] = entry
        if (entries.size > MAX_ENTRIES) {
            entries.values.sortedBy { it.observedAt }
                .take(entries.size - MAX_ENTRIES)
                .forEach { old ->
                    entries.remove("${old.kind.name}|${old.packageName}|${old.syntheticClass}", old)
                }
        }
    }

    fun snapshot(): List<ObservedShortcutEntry> =
        entries.values.sortedWith(
            compareByDescending<ObservedShortcutEntry> { it.observedAt }
                .thenBy { it.packageName }
                .thenBy { it.label }
        )
}
