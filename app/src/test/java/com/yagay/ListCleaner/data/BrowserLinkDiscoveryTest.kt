package com.yagay.ListCleaner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLinkDiscoveryTest {
    @Test
    fun parsesVerifiedAndUserSelectedDomains() {
        val output = """
            com.github.android:
              ID: 123
              Signatures: [abc]
              Domain verification state:
                github.com: verified
                www.github.com: none
              User 0:
                Verification link handling allowed: true
                Selection state:
                  Enabled:
                    github.com
                  Disabled:
                    old.github.com
        """.trimIndent()

        val hosts = parseAppLinkHosts(output)

        assertEquals(listOf("github.com", "www.github.com", "old.github.com"), hosts.toList())
    }

    @Test
    fun ignoresNonDomainNoiseAndNormalizesHosts() {
        val output = """
            sample.package:
              Domain verification state:
                EXAMPLE.COM: approved
                localhost: verified
                bad_host: verified
              User 0:
                Selection state:
                  Enabled:
                    sub.example.com
        """.trimIndent()

        val hosts = parseAppLinkHosts(output)

        assertTrue("example.com" in hosts)
        assertTrue("sub.example.com" in hosts)
        assertTrue("localhost" !in hosts)
        assertTrue("bad_host" !in hosts)
    }
}
