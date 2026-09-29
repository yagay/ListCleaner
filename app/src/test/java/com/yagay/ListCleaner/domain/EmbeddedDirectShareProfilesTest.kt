package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedDirectShareProfilesTest {
    @Test
    fun oplusAliasesResolveToSameProfile() {
        val ids = listOf(
            "com.oneplus.gallery",
            "com.oplus.gallery",
            "com.coloros.gallery3d",
        ).map { packageName ->
            EmbeddedDirectShareProfiles.matching(packageName).single().id
        }.toSet()

        assertEquals(setOf("oplus-gallery"), ids)
    }

    @Test
    fun embeddedProfilesUseStructuralRefreshSignatures() {
        val profile = EmbeddedDirectShareProfiles.matching("com.oneplus.gallery").single()
        assertTrue(profile.refreshMethods.any {
            it.returnTypeName == "void" &&
                it.parameterTypeNames == listOf("android.content.Intent")
        })
    }

    @Test
    fun oplusProfileDeclaresEmptySurfaceResources() {
        val profile = EmbeddedDirectShareProfiles.matching("com.oneplus.gallery").single()
        assertTrue("direct_share_fl" in profile.collapseWhenEmptyResourceNames)
        assertTrue("direct_share_divider" in profile.collapseWhenEmptyResourceNames)
        assertTrue("direct_share_no_data_tv" in profile.collapseWhenEmptyResourceNames)
    }
}
