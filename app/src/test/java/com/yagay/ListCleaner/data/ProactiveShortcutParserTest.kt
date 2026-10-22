package com.yagay.ListCleaner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveShortcutParserTest {
    private val sample = """
        LC_SHORTCUT_PACKAGE:com.example.chat
        ShortcutInfo {id=chat-alice, flags=0x9 [Dyn]
          packageName=com.example.chat
          activity=ComponentInfo{com.example.chat/com.example.chat.Main}
          shortLabel=Conversation A, resId=0[null]
          categories=[com.example.SHARE]
          intents=[private data suppressed]
        }
        ShortcutInfo {id=launcher-only, flags=0x1 [Dyn]
          packageName=com.example.chat
          activity=ComponentInfo{com.example.chat/com.example.chat.Main}
          shortLabel=Normal shortcut, resId=0[null]
          categories=null
        }
        LC_SHORTCUT_PACKAGE:com.other
        ShortcutInfo {id=other-id, flags=0x9 [Man]
          packageName=com.other
          activity=ComponentInfo{com.other/com.other.Home}
          shortLabel=Other, resId=0[null]
          categories=[com.other.SHARE]
        }
        Success
    """.trimIndent()

    @Test fun onlyBoundedCategorizedShortcutsFromAllowedPackagesAreReturned() {
        val rows = ProactiveShortcutParser.parse(sample, setOf("com.example.chat"))
        assertEquals(1, rows.size)
        assertEquals("com.example.chat", rows.single().packageName)
        assertEquals("chat-alice", rows.single().shortcutId)
        assertEquals("Conversation A", rows.single().label)
        assertTrue(rows.single().activity!!.startsWith("ComponentInfo{"))
    }

    @Test fun handlesOtherUserScopeAndDoesNotParseRawPrivateDataAsAShortcut() {
        val rows = ProactiveShortcutParser.parse(sample, setOf("com.other"))
        assertEquals(1, rows.size)
        assertEquals("other-id", rows.single().shortcutId)
        assertFalse(rows.any { it.packageName == "com.example.chat" })
    }

    @Test fun emptyOrUnsupportedShellOutputNeverMeansVerifiedZero() {
        assertTrue(ProactiveShortcutParser.parse("Unknown command get-shortcuts", setOf("com.other")).isEmpty())
        assertTrue(ProactiveShortcutParser.parse("Success", setOf("com.other")).isEmpty())
    }
}
