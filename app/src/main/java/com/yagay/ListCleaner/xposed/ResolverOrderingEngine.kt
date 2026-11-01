package com.yagay.ListCleaner.xposed

import android.content.pm.ResolveInfo
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.RuntimeProtocol
import com.yagay.ListCleaner.domain.effectiveResolverPriorities
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
        shareTypeKey: String? = null,
    ): List<String> {
        if (shareTypeKey != null && kind in setOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE)) {
            current.openTypes.sharePriority(kind, shareTypeKey)
                .takeIf { it.isNotEmpty() }?.let { return it }
        }
        return effectiveResolverPriorities(
            kind, preset, browserHost, current.priorities, current.openTypes, current.browserLinks,
        )
    }

    fun candidateKind(baseKind: IntentKind, info: ResolveInfo): IntentKind =
        if (baseKind == IntentKind.BROWSER) info.webTargetKind() else baseKind

    fun hasEffectivePriorities(
        kind: IntentKind,
        preset: OpenPreset?,
        browserHost: String?,
        current: RuntimeRuleSnapshot,
        shareTypeKey: String? = null,
    ): Boolean {
        if (kind != IntentKind.BROWSER) {
            return effectivePriorities(kind, preset, browserHost, current, shareTypeKey).isNotEmpty()
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
                AndroidUid.userId(requireNotNull(infos[index].activityInfo.applicationInfo).uid)
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
        shareTypeKey: String? = null,
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
                { index -> AndroidUid.userId(requireNotNull(infos[index].activityInfo.applicationInfo).uid) },
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
            reorderSubset(kind, effectivePriorities(kind, preset, browserHost, current, shareTypeKey))
        }
        val changed = positions != items.indices.toList()
        diagnostic(
            "ORDER_RESULT stage=$stage kind=$kind count=${items.size} changed=$changed digest=${current.digest}"
        )
        return if (changed) positions.map { items[it] } else items
    }

    /**
     * Read component identities in the exact adapter order. No contact shortcuts, labels or
     * private target payloads are captured. Reject incomplete reflection instead of returning
     * a misleading partial order.
     */
    fun orderedComponentIds(items: List<*>, stage: String): List<String>? {
        if (items.isEmpty()) return emptyList()
        return runCatching {
            items.map { item ->
                val activity = requireNotNull(resolveInfoForOrder(item, stage).activityInfo)
                require(activity.packageName.isNotBlank() && activity.name.isNotBlank())
                "${activity.packageName}/${activity.name}"
            }
        }.getOrNull()
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
}
