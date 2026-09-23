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
    fun keepsPackageOwnershipForDiscoveredDomains() {
        val output = """
            com.github.android:
              ID: 123
              Domain verification state:
                github.com: verified
                www.github.com: none
              User 0:
                Selection state:
                  Enabled:
                    github.com
            com.example.other:
              Domain verification state:
                example.org: approved
        """.trimIndent()

        val result = parseAppLinkDeclarations(output)

        assertEquals(setOf("com.github.android"), result.packagesByHost["github.com"])
        assertEquals(setOf("com.github.android"), result.packagesByHost["www.github.com"])
        assertEquals(setOf("com.example.other"), result.packagesByHost["example.org"])
        assertTrue("com.github.android" in result.packages)
        assertTrue("com.example.other" in result.packages)
    }

    @Test
    fun resolverTableFindsPathRestrictedGithubHandlerWithoutUrlProbe() {
        val output = """
            @@LISTCLEANER_PACKAGE@@com.github.android
            Activity Resolver Table:
              Schemes:
                  https:
                    18eb6b2 com.github.android/.DeepLinkAliasActivity filter 123abcd
                      Action: "android.intent.action.VIEW"
                      Category: "android.intent.category.DEFAULT"
                      Category: "android.intent.category.BROWSABLE"
                      Scheme: "https"
                      Authority: "github.com": -1
                      Path: "PatternMatcher{PREFIX: /notifications}"
                      Path: "PatternMatcher{PREFIX: /settings}"
                      AutoVerify=true
                  github:
                    18eb6b2 com.github.android/.DeepLinkAliasActivity filter 99aa001
                      Action: "android.intent.action.VIEW"
                      Category: "android.intent.category.BROWSABLE"
                      Scheme: "github"
                      Authority: "open": -1
        """.trimIndent()

        val handlers = parseDeclaredWebHandlers(output)

        assertEquals(
            setOf(
                DeclaredWebHandler(
                    host = "github.com",
                    packageName = "com.github.android",
                    className = "com.github.android.DeepLinkAliasActivity"
                )
            ),
            handlers
        )
    }

    @Test
    fun resolverTableIgnoresGenericBrowsersWithoutAuthority() {
        val output = """
            @@LISTCLEANER_PACKAGE@@com.example.browser
            Activity Resolver Table:
              Schemes:
                  https:
                    abc123 com.example.browser/.BrowserActivity filter def456
                      Action: "android.intent.action.VIEW"
                      Category: "android.intent.category.BROWSABLE"
                      Scheme: "https"
        """.trimIndent()

        assertTrue(parseDeclaredWebHandlers(output).isEmpty())
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
