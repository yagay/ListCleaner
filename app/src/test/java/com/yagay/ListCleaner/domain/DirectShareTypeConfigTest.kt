package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectShareTypeConfigTest {
    private val person = ComponentRule(
        IntentKind.DIRECT_SHARE, "com.example.chat",
        "com.example.chat.Main#direct#0123456789abcdef01234567"
    )
    private val generic = person.id

    @Test fun oneFamilyDoesNotChangeOtherFamilies() {
        val config = DirectShareTypeConfig(
            rules = mapOf(DirectShareType.IMAGE to setOf(generic))
        ).validated()
        assertEquals(setOf(person), config.selectedRules(DirectShareType.IMAGE))
        assertTrue(config.selectedRules(DirectShareType.TEXT).isEmpty())
        assertTrue(config.selectedRules(DirectShareType.VIDEO).isEmpty())
    }

    @Test fun compiledRulesKeepGenericAndMimeRulesIndependent() {
        val typed = DirectShareTypeConfig(
            mapOf(DirectShareType.IMAGE to setOf(generic))
        ).runtimeIds()
        assertEquals(setOf(DirectShareTypeRules.typed(person, DirectShareType.IMAGE).id), typed)
        val combined = typed + generic
        assertEquals(setOf(generic),
            DirectShareTypeRules.selectedFor(combined, listOf(generic), DirectShareType.TEXT))
        assertEquals(setOf(generic),
            DirectShareTypeRules.selectedFor(typed, listOf(generic), DirectShareType.IMAGE))
        assertTrue(DirectShareTypeRules.selectedFor(typed, listOf(generic), DirectShareType.VIDEO).isEmpty())
        assertTrue(DirectShareTypeRules.selectedFor(typed, listOf(generic), null).isEmpty())
    }

    @Test fun malformedAndWrongKindIdsAreRejected() {
        val wrong = ComponentRule(IntentKind.SHARE, "com.example.chat", "com.example.Main").id
        assertTrue(runCatching {
            DirectShareTypeConfig(mapOf(DirectShareType.TEXT to setOf(wrong))).validated()
        }.isFailure)
        assertTrue(runCatching {
            DirectShareTypeConfig(mapOf(DirectShareType.TEXT to setOf(generic + "#mime#IMAGE"))).validated()
        }.isFailure)
    }

    @Test fun onlyVerifiedRecentSharesheetMatchIsCurrent() {
        val now = 1_500_000L
        val verified = ObservedEntryRecord(
            kind = IntentKind.DIRECT_SHARE.name,
            packageName = person.packageName,
            syntheticClass = person.className,
            directShareFamilies = setOf(DirectShareType.IMAGE.name),
            observedAt = now - 300_000L,
        )
        assertTrue(DirectShareTypeRules.isRecentMatch(verified, now))
        assertFalse(DirectShareTypeRules.isRecentMatch(
            verified.copy(directShareFamilies = emptySet()), now))
        assertFalse(DirectShareTypeRules.isRecentMatch(
            verified.copy(kind = IntentKind.SHORTCUT_ITEM.name), now))
        assertFalse(DirectShareTypeRules.isRecentMatch(
            verified.copy(observedAt = now - 600_001L), now))
        assertFalse(DirectShareTypeRules.isRecentMatch(
            verified.copy(observedAt = now + 1000L), now))
    }

    @Test fun familyEvidenceOnlyFromMatchedMime() {
        assertEquals(DirectShareType.TEXT, DirectShareType.fromMime("text/plain"))
        assertEquals(DirectShareType.IMAGE, DirectShareType.fromMime("image/jpeg; charset=utf-8"))
        assertEquals(DirectShareType.FILE, DirectShareType.fromMime("application/pdf"))
        assertEquals(null, DirectShareType.fromMime("*/*"))
        assertEquals(setOf(DirectShareType.TEXT),
            DirectShareTypeRules.observed(listOf(DirectShareTypeRules.evidence(DirectShareType.TEXT))))
        assertFalse(DirectShareType.IMAGE in
            DirectShareTypeRules.observed(listOf("categories=chat.share")))
    }
}
