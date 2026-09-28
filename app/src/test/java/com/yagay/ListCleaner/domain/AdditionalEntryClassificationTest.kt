package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AdditionalEntryClassificationTest {
    @Test
    fun additionalImplicitActionsKeepDistinctRuleKinds() {
        val cases = listOf(
            Triple("android.intent.action.SENDTO", "mailto", "SEND_TO"),
            Triple("android.intent.action.DIAL", "tel", "DIAL"),
            Triple("android.intent.action.GET_CONTENT", null, "GET_CONTENT"),
            Triple("android.media.action.IMAGE_CAPTURE", null, "CAPTURE_IMAGE"),
            Triple("android.media.action.VIDEO_CAPTURE", null, "CAPTURE_VIDEO"),
            Triple("android.provider.MediaStore.RECORD_SOUND", null, "RECORD_AUDIO")
        )
        cases.forEach { (action, scheme, expected) ->
            assertEquals(expected, IntentClassification.classify(action, scheme, null))
        }
    }

    @Test
    fun explicitlySupportedViewSchemesRemainOpenRules() {
        listOf("magnet", "geo", "mailto", "tel", "sms", "smsto").forEach { scheme ->
            assertEquals(
                "OPEN",
                IntentClassification.classify("android.intent.action.VIEW", scheme, null)
            )
        }
    }

    @Test
    fun everyIntentKindHasMatchingVisibilityScope() {
        IntentKind.entries.forEach { kind ->
            assertEquals(kind.name, VisibilityScope.forKind(kind).name)
        }
    }
}
