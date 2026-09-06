package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibilitySignatureTest {
    @Test fun android12AppsFilterLayoutIsParsed() {
        assertEquals(VisibilityLayout(0, 1, 2), VisibilitySignature.parse(listOf(
            "int", "com.android.server.pm.SettingBase", "com.android.server.pm.PackageSetting", "int"
        )))
    }

    @Test fun modernAppsFilterLayoutIsParsed() {
        assertEquals(VisibilityLayout(1, 2, 3), VisibilitySignature.parse(listOf(
            "com.android.server.pm.Computer", "int", "java.lang.Object",
            "com.android.server.pm.pkg.PackageStateInternal", "int"
        )))
    }

    @Test fun unknownLayoutFailsOpen() {
        assertNull(VisibilitySignature.parse(listOf("java.lang.String", "int", "int")))
    }
}
