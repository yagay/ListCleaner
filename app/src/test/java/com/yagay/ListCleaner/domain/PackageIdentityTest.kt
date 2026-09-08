package com.yagay.ListCleaner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageIdentityTest {
    @Test
    fun acceptsOrdinaryAndroidPackages() {
        assertTrue(PackageIdentity.valid("com.example.app"))
        assertTrue(PackageIdentity.valid("com.example_2.feature3"))
        assertTrue(PackageIdentity.valid("_vendor.product"))
    }

    @Test
    fun rejectsShellAndMalformedValues() {
        assertFalse(PackageIdentity.valid(""))
        assertFalse(PackageIdentity.valid("android"))
        assertFalse(PackageIdentity.valid("com.example.bad-name"))
        assertFalse(PackageIdentity.valid("com.example.app;id"))
        assertFalse(PackageIdentity.valid("com.example.app name"))
        assertFalse(PackageIdentity.valid("1com.example.app"))
        assertFalse(PackageIdentity.valid("com..example"))
    }

    @Test
    fun rejectsOversizedPackages() {
        val value = "com." + "a".repeat(252)
        assertFalse(PackageIdentity.valid(value))
    }
}
