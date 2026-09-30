package com.yagay.ListCleaner.xposed

import android.content.pm.ResolveInfo
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.RuntimeProtocol
import com.yagay.ListCleaner.domain.prioritizeApps
import com.yagay.ListCleaner.domain.webTargetKind

/** Pure resolver ordering/index helpers shared by query and final-adapter stages. */
internal class ResolverOrderingEngine(
    private val diagnostic: (String) -> Unit,
) {
    fun effectivePriorities(
        kind: IntentKind,
        preset: OpenPreset?,
        browserHost: String?,
        current: RuntimeRuleSnapshot,
    ): List<String> {
        val typed = if (kind == IntentKind.OPEN && preset != null) {
            current.openTypes.priorities[preset].orEmpty()
        } else emptyList()
        if (typed.isNotEmpty()) return typed
        val hostPriority = if (kind == IntentKind.DEEP_LINK && browserHost != null) {
            current.browserLinks.priority(browserHost)
        } else emptyList()
        return if (hostPriority.isNotEmpty()) hostPriority else current.priorities.apps[kind].orEmpty()
    }

    fun candidateKind(baseKind: IntentKind, info: ResolveInfo): IntentKind =
        if (baseKind == IntentKind.BROWSER) info.webTargetKind() else baseKind

    fun hasEffectivePriorities(
        kind: IntentKind,
        preset: OpenPreset?,
        browserHost: String?,
        current: RuntimeRuleSnapshot,
    ): Boolean {
        if (kind != IntentKind.BROWSER) {
            return effectivePriorities(kind, preset, browserHost, current).isNotEmpty()
        }
        return current.priorities.apps[IntentKind.BROWSER].orEmpty().isNotEmpty() ||
            effectivePriorities(IntentKind.DEEP_LINK, null, browserHost, current).isNotEmpty()
    }

    fun hasResolverPolicyMetadata(items: List<*>, stage: String): Boolean =
        items.asSequence().mapNotNull { item ->
            runCatching { resolveInfoForOrder(item, stage) }.getOrNull()
        }.any { resolverMetadataDigest(it) != null }

    fun orderItemsFromMetadata(
        items: List<*>,
        kind: IntentKind,
        stage: String,
        fixedPackages: Set<String> = emptySet(),
    ): List<*> {
        if (items.size < 2) return items
        val infos = items.map { resolveInfoForOrder(it, stage) }
        val positions = items.indices.toMutableList()
        fun reorderSubset(targetKind: IntentKind) {
            val movable = items.indices.filter { index ->
                candidateKind(kind, infos[index]) == targetKind &&
                    infos[index].activityInfo.packageName !in fixedPackages
            }
            if (movable.size < 2) return
            movable.groupBy { index ->
                requireNotNull(infos[index].activityInfo.applicationInfo).uid / PER_USER_RANGE
            }.values.forEach { profilePositions ->
                val sorted = profilePositions.sortedBy { index ->
                    val meta = infos[index].activityInfo.metaData
                    if (meta?.containsKey(RuntimeProtocol.META_PRIORITY_RANK) == true) {
                        meta.getInt(RuntimeProtocol.META_PRIORITY_RANK, Int.MAX_VALUE)
                    } else Int.MAX_VALUE
                }
                profilePositions.forEachIndexed { orderIndex, position ->
                    positions[position] = sorted[orderIndex]
                }
            }
        }
        if (kind == IntentKind.BROWSER) {
            reorderSubset(IntentKind.BROWSER)
            reorderSubset(IntentKind.DEEP_LINK)
        } else reorderSubset(kind)
        val changed = positions != items.indices.toList()
        val digest = infos.asSequence().mapNotNull(::resolverMetadataDigest).firstOrNull()
        diagnostic(
            "ORDER_RESULT stage=$stage kind=$kind count=${items.size} changed=$changed " +
                "source=system_metadata digest=${digest ?: "none"}"
        )
        return if (changed) positions.map { items[it] } else items
    }

    fun orderItems(
        items: List<*>,
        kind: IntentKind,
        current: RuntimeRuleSnapshot,
        stage: String,
        fixedPackages: Set<String> = emptySet(),
        preset: OpenPreset? = null,
        browserHost: String? = null,
    ): List<*> {
        if (items.size < 2) return items
        val infos = items.map { resolveInfoForOrder(it, stage) }
        val positions = items.indices.toMutableList()
        fun reorderSubset(targetKind: IntentKind, priorities: List<String>) {
            if (priorities.isEmpty()) return
            val movable = items.indices.filter { index ->
                candidateKind(kind, infos[index]) == targetKind &&
                    infos[index].activityInfo.packageName !in fixedPackages
            }
            if (movable.size < 2) return
            val sorted = prioritizeApps(
                movable,
                priorities,
                { index -> requireNotNull(infos[index].activityInfo).packageName },
                { index -> requireNotNull(infos[index].activityInfo.applicationInfo).uid / PER_USER_RANGE },
            )
            movable.forEachIndexed { orderIndex, position -> positions[position] = sorted[orderIndex] }
        }
        if (kind == IntentKind.BROWSER) {
            reorderSubset(IntentKind.BROWSER, current.priorities.apps[IntentKind.BROWSER].orEmpty())
            reorderSubset(
                IntentKind.DEEP_LINK,
                effectivePriorities(IntentKind.DEEP_LINK, null, browserHost, current),
            )
        } else {
            reorderSubset(kind, effectivePriorities(kind, preset, browserHost, current))
        }
        val changed = positions != items.indices.toList()
        diagnostic(
            "ORDER_RESULT stage=$stage kind=$kind count=${items.size} changed=$changed digest=${current.digest}"
        )
        return if (changed) positions.map { items[it] } else items
    }

    private fun resolveInfoForOrder(item: Any?, stage: String): ResolveInfo {
        requireNotNull(item)
        return if (item is ResolveInfo) item else if (stage == "alpha") {
            OrderingAccess.call(item, "getResolveInfo") as ResolveInfo
        } else {
            item.javaClass.getMethod("getResolveInfoAt", Int::class.javaPrimitiveType)
                .invoke(item, 0) as ResolveInfo
        }
    }

    private fun resolverMetadataDigest(info: ResolveInfo): String? =
        info.activityInfo?.metaData
            ?.getString(RuntimeProtocol.META_POLICY_DIGEST)
            ?.takeIf(RuntimeProtocol::validDigest)

    private companion object {
        const val PER_USER_RANGE = 100_000
    }
}
