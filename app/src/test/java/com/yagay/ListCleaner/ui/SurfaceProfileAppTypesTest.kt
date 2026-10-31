package com.yagay.ListCleaner.ui

import org.junit.Assert.*
import org.junit.Test

class SurfaceProfileAppTypesTest {
    @Test fun parseValidPackagesAndIgnoreNoise() {
        val found = SurfaceProfileAppTypes.parsePackageOutput(
            "package:com.example.mail\npackage:bad package\npackage:com.example.chat\nError:\n")
        assertTrue("com.example.mail" in found)
        assertTrue("com.example.chat" in found)
        assertFalse("bad" in found)
    }
    @Test fun duplicatePackagesAreCollapsed() {
        assertEquals(setOf("com.example.same"),
            SurfaceProfileAppTypes.parsePackageOutput(
                "package:com.example.same\npackage:com.example.same"))
    }
}