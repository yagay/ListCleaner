package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isPackageScopedEntry

/** No implicit union: a scoped draft must be able to remove an already-saved rule. */
internal object EffectivePreviewPolicy {
    fun selection(
        saved: Set<ComponentRule>,
        scoped: Set<ComponentRule>?,
        kind: IntentKind?,
    ): Set<ComponentRule> = if (kind == null || scoped == null) saved else
        saved.filterTo(linkedSetOf()) { it.kind != kind } +
            scoped.filterTo(linkedSetOf()) { it.kind == kind }

    fun visible(
        kind: IntentKind,
        candidates: List<SystemProbeItem>,
        selected: Set<ComponentRule>,
        globalMode: DisplayMode,
        overrides: Map<IntentKind, DisplayMode>,
    ): List<SystemProbeItem> {
        val relevant = selected.asSequence().filter { it.kind == kind }.toList()
        val packages = relevant.mapTo(hashSetOf()) { it.packageName }
        val ids = relevant.mapTo(hashSetOf()) { it.id }
        val mode = overrides[kind] ?: globalMode
        return candidates.filter { target ->
            val matched = if (kind.isPackageScopedEntry()) {
                target.packageName in packages
            } else {
                ComponentRule(kind, target.packageName, target.className).id in ids
            }
            mode.includes(matched, relevant.isNotEmpty())
        }
    }
}

/** Root status originates in the matching user-space component scan, not a second PM query. */
internal data class RootPreviewTarget(
    val kind: CleanupKind,
    val userId: Int,
    val item: SystemProbeItem,
    /** Whether the component is effectively enabled; unknown states are not asserted visible. */
    val enabled: Boolean?,
)

internal data class RootPreviewSnapshot(
    val userId: Int,
    val items: List<RootPreviewTarget>,
    val pendingDraft: Boolean,
) {
    fun visible(kind: CleanupKind): List<SystemProbeItem> =
        items.asSequence().filter {
            it.kind == kind && it.userId == userId && it.enabled == true
        }.map { it.item }.distinctBy(SystemProbeItem::component).toList()
}
