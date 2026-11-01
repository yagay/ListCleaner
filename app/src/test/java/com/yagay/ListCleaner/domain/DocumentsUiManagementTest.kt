package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class DocumentsUiManagementTest {
    private val drive = "com.example.drive"
    private val photo = "com.example.photos"

    @Test fun scansShareOnePackageRowWithoutLosingSources() {
        val open = ComponentCandidate(ComponentRule(IntentKind.OPEN_DOCUMENT, drive, "$drive.Open"), "Drive", "Open",
            evidence = listOf("OPEN_DOCUMENT mime=application/pdf"))
        val save = ComponentCandidate(ComponentRule(IntentKind.CREATE_DOCUMENT, drive, "$drive.Save"), "Drive", "Save",
            evidence = listOf("CREATE_DOCUMENT mime=text/plain"))
        val provider = ComponentCandidate(ComponentRule(IntentKind.DOCUMENT_PROVIDER, drive, "$drive.Provider"), "Drive", "Storage",
            evidence = listOf("PROVIDER_INTERFACE"))
        val unrelated = ComponentCandidate(ComponentRule(IntentKind.PHOTO_SOURCE, photo, "$photo.Media"), "Photos", "Photos")
        val result = unifyDocumentsUiCandidates(listOf(open, save, provider, unrelated))
        assertEquals(2, result.size)
        val merged = result.single { it.rule.packageName == drive }
        assertEquals(documentsUiRule(drive), merged.rule)
        assertTrue(merged.evidence.any { it.contains("OPEN_DOCUMENT mime") })
        assertTrue(merged.evidence.any { it.contains("CREATE_DOCUMENT mime") })
        assertTrue(merged.evidence.any { it.contains("PROVIDER_INTERFACE") })
        assertEquals(IntentKind.PHOTO_SOURCE, result.single { it.rule.packageName == photo }.rule.kind)
    }

    @Test fun oldDocumentSelectionsAndPrioritiesMigrateForEveryAndroidUser() {
        val oldOpen = ComponentRule(IntentKind.OPEN_DOCUMENT, drive, "$drive.Open")
        val oldSave = ComponentRule(IntentKind.CREATE_DOCUMENT, drive, "$drive.Save")
        val work = ComponentRule(IntentKind.DOCUMENT_ROOT, "com.example.work", "@docroot_abcdef")
        val config = ModuleConfig(
            rules = setOf(oldOpen, oldSave),
            mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(apps = mapOf(
                IntentKind.OPEN_DOCUMENT to listOf(drive),
                IntentKind.CREATE_DOCUMENT to listOf("com.example.another", drive)
            )),
            diagnostic = false,
            modeOverrides = mapOf(IntentKind.OPEN_DOCUMENT to DisplayMode.SHOW_SELECTED),
            userSpaces = mapOf(10 to UserSpaceRules(
                rules = setOf(work), priorities = PriorityConfig(apps = mapOf(
                    IntentKind.DOCUMENT_ROOT to listOf("com.example.work")
                ))
            ))
        )
        val normalized = config.validated()
        assertEquals(setOf(documentsUiRule(drive)), normalized.rules)
        assertEquals(listOf(drive, "com.example.another"),
            normalized.priorities.apps[IntentKind.DOCUMENT_PROVIDER])
        assertFalse(normalized.priorities.apps.containsKey(IntentKind.CREATE_DOCUMENT))
        assertEquals(DisplayMode.SHOW_SELECTED, normalized.modeOverrides[IntentKind.DOCUMENT_PROVIDER])
        assertEquals(setOf(documentsUiRule("com.example.work")),
            normalized.userSpaces.getValue(10).rules)
        assertEquals(listOf("com.example.work"),
            normalized.userSpaces.getValue(10).priorities.apps[IntentKind.DOCUMENT_PROVIDER])
        assertEquals(normalized, normalized.validated())
    }

    @Test fun everyDocumentActionReusesTheSameSavedPriority() {
        val priority = PriorityConfig(apps = mapOf(
            IntentKind.DOCUMENT_PROVIDER to listOf("com.example.first", drive)
        ))
        for (kind in DOCUMENTS_UI_SOURCE_KINDS) {
            assertEquals(listOf("com.example.first", drive),
                effectiveResolverPriorities(kind, null, null, priority, OpenTypeConfig(), BrowserLinkConfig()))
        }
    }
}
