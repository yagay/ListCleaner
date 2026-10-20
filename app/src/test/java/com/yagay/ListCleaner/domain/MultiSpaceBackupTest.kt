package com.yagay.ListCleaner.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSpaceBackupTest {
    private fun archive() = MultiSpaceBackup(
        spaces = listOf(
            SpaceBackupEntry(0, "Primary", UserSpaceRules(mode = DisplayMode.SHOW_ALL)),
            SpaceBackupEntry(10, "Work", UserSpaceRules(mode = DisplayMode.HIDE_SELECTED)),
        )
    )

    @Test fun v13RoundTripsWithExplicitDiscriminator() {
        val format = Json { encodeDefaults = true }
        val encoded = format.encodeToString(MultiSpaceBackup.serializer(), archive())
        assertTrue(encoded.contains("\\"version\\":13"))
        assertTrue(encoded.contains("\\"format\\":\\"listcleaner-spaces\\""))
        val decoded = format.decodeFromString(MultiSpaceBackup.serializer(), encoded).validated()
        assertEquals(archive(), decoded)
    }

    @Test fun allowsSelectedSubsetAndCrossDeviceUserRemapping() {
        val all = archive().mapped(mapOf(0 to 11, 10 to 0))
        assertEquals(setOf(0, 11), all.keys)
        assertEquals(DisplayMode.SHOW_ALL, all[11]?.mode)
        assertEquals(DisplayMode.HIDE_SELECTED, all[0]?.mode)
        assertEquals(setOf(11), archive().mapped(mapOf(10 to 11)).keys)
    }

    @Test fun rejectsDuplicateTargetsAndUnknownSources() {
        assertThrows(IllegalArgumentException::class.java) {
            archive().mapped(mapOf(0 to 11, 10 to 11))
        }
        assertThrows(IllegalArgumentException::class.java) {
            archive().mapped(mapOf(99 to 11))
        }
        assertThrows(IllegalArgumentException::class.java) {
            archive().mapped(emptyMap())
        }
    }

    @Test fun rejectsDuplicateSourceIds() {
        assertThrows(IllegalArgumentException::class.java) {
            archive().copy(spaces = archive().spaces + archive().spaces.first()).validated()
        }
    }

    @Test fun validatesRootRecordsAgainstTheirSourceUser() {
        val good = archive().copy(spaces = listOf(
            SpaceBackupEntry(10, "Work", UserSpaceRules(),
                setOf("10|com.example.app|com.example.app.MainActivity"))
        ))
        assertTrue(good.validated().spaces.first().rootDisabledComponents.isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            good.copy(spaces = listOf(good.spaces.first().copy(
                rootDisabledComponents = setOf("0|com.example.app|com.example.app.MainActivity")
            ))).validated()
        }
    }
}
