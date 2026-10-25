package com.yagay.ListCleaner.domain

/**
 * Reconciles active discovery with manager/role authority observations.
 *
 * For AUTHORITY_UPGRADE surfaces, an actively discovered candidate remains visible while awaiting
 * final authority confirmation. Only genuinely historical candidates keep unavailable=true.
 */
object AuthorityCandidatePolicy {
    fun normalize(
        items: List<ComponentCandidate>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ComponentCandidate> = items.map { item ->
        val definition = item.rule.kind.surfaceDefinition() ?: return@map item
        if (definition.availabilityMode != EntryAvailabilityMode.AUTHORITY_UPGRADE) return@map item

        val confirmed = authorityConfirmed(item, nowMillis)
        when {
            confirmed && item.unavailable -> item.copy(
                unavailable = false,
                evidence = (item.evidence + "AUTHORITY_POLICY confirmed=true").distinct(),
            )
            !confirmed && !item.unavailable -> item.copy(
                evidence = (item.evidence + "AUTHORITY_POLICY confirmed=false provisional=true").distinct(),
            )
            else -> item
        }
    }

    fun authorityConfirmed(item: ComponentCandidate, @Suppress("UNUSED_PARAMETER") nowMillis: Long = System.currentTimeMillis()): Boolean {
        val definition = item.rule.kind.surfaceDefinition() ?: return true
        if (definition.availabilityMode != EntryAvailabilityMode.AUTHORITY_UPGRADE) return true

        val observed = item.evidence.firstOrNull { it.startsWith("AUTHORITY_OBSERVED ") } ?: return false
        // Cache recency is never proof that the owning manager currently exposes an
        // entry. Only live runtime evidence can promote it to authoritative.
        return "source=live" in observed
    }
}
