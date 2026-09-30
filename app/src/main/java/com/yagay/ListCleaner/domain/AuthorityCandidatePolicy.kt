package com.yagay.ListCleaner.domain

/**
 * Keeps manager-owned Settings surfaces honest: PackageManager/AppOps guesses may provide evidence,
 * but only the owning manager (or a recent observed authority snapshot) may enter the normal list.
 */
object AuthorityCandidatePolicy {
    const val SNAPSHOT_FRESH_MILLIS = 24L * 60L * 60L * 1000L
    private const val CLOCK_SKEW_MILLIS = 5L * 60L * 1000L

    private val authorityRequiredKinds = setOf(
        IntentKind.ASSISTANT,
        IntentKind.HOME,
        IntentKind.BROWSER,
        IntentKind.CALL_SCREENING,
        IntentKind.INPUT_METHOD,
        IntentKind.ACCESSIBILITY,
        IntentKind.PRINT,
        IntentKind.VPN,
        IntentKind.AUTOFILL,
        IntentKind.CREDENTIAL_PROVIDER,
        IntentKind.NFC_HCE,
    )

    fun normalize(
        items: List<ComponentCandidate>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ComponentCandidate> = items.map { item ->
        if (item.rule.kind !in authorityRequiredKinds) return@map item
        val confirmed = authorityConfirmed(item, nowMillis)
        when {
            confirmed && item.unavailable -> item.copy(
                unavailable = false,
                evidence = (item.evidence + "AUTHORITY_POLICY confirmed=true").distinct(),
            )
            !confirmed && !item.unavailable -> item.copy(
                unavailable = true,
                evidence = (item.evidence + "AUTHORITY_POLICY confirmed=false fallback_only=true").distinct(),
            )
            else -> item
        }
    }

    fun authorityConfirmed(item: ComponentCandidate, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (item.rule.kind !in authorityRequiredKinds) return true

        // These manager APIs are already the same source used by Settings.
        if (item.rule.kind == IntentKind.INPUT_METHOD &&
            item.evidence.any { it.startsWith("INPUT_METHOD_MANAGER") }
        ) return true
        if (item.rule.kind == IntentKind.ACCESSIBILITY && item.evidence.any {
                it.startsWith("ACCESSIBILITY_MANAGER") ||
                    it.startsWith("ACCESSIBILITY_SHORTCUT authority=AccessibilityManager")
            }
        ) return true

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
