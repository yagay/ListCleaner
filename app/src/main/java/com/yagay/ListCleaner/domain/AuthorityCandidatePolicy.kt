package com.yagay.ListCleaner.domain

/**
 * Reconciles active discovery with manager/role authority observations.
 *
 * For AUTHORITY_UPGRADE surfaces, an actively discovered candidate remains visible while awaiting
 * final authority confirmation. Only genuinely historical candidates keep unavailable=true.
 */
object AuthorityCandidatePolicy {
    const val SNAPSHOT_FRESH_MILLIS = 24L * 60L * 60L * 1000L
    private const val CLOCK_SKEW_MILLIS = 5L * 60L * 1000L

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

    fun authorityConfirmed(item: ComponentCandidate, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val definition = item.rule.kind.surfaceDefinition() ?: return true
        if (definition.availabilityMode != EntryAvailabilityMode.AUTHORITY_UPGRADE) return true

        val observed = item.evidence.firstOrNull { it.startsWith("AUTHORITY_OBSERVED ") } ?: return false
        if ("source=live" in observed) return true
        if ("source=cache_history" !in observed) return false

        val observedAt = item.evidence.asSequence()
            .filter { it.startsWith("observedAt=") }
            .mapNotNull { it.substringAfter('=').toLongOrNull() }
            .firstOrNull()
            ?: return false
        val age = nowMillis - observedAt
        return age in -CLOCK_SKEW_MILLIS..SNAPSHOT_FRESH_MILLIS
    }
}
