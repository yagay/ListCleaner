package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot

/**
 * Single authority for preview list provenance. A registration, historical Root or
 * unrelated manager return is never presented as a verified post-filter result.
 * This class contains no Android services, Compose state or rule simulation.
 */
internal enum class PreviewListSource {
    HOOK_OBSERVATION, HOST_OBSERVATION, DOCUMENT_ROOTS, API_RETURN, DIAGNOSTIC, UNAVAILABLE
}

internal data class PreviewListResult(
    val items: List<SystemProbeItem>,
    val source: PreviewListSource,
)

internal object SystemPreviewSelection {
    private const val MAX_AGE_MS = 120_000L

    fun select(
        request: SystemProbeRequest,
        value: SystemProbeResult?,
        showDiagnostics: Boolean,
        nowMillis: Long,
        launchedAtMillis: Long?,
        currentCallerUid: Int,
        expectedAction: String? = null,
    ): PreviewListResult {
        if (value == null) return PreviewListResult(emptyList(), PreviewListSource.UNAVAILABLE)
        val kind = request.entryKind
        val isDocument = kind == IntentKind.OPEN_DOCUMENT || kind == IntentKind.CREATE_DOCUMENT
        val isManager = kind == IntentKind.ACCESSIBILITY ||
            kind == IntentKind.PRINT || kind == IntentKind.CREDENTIAL_PROVIDER

        if (showDiagnostics) {
            val diagnostic = if (isDocument || isManager)
                value.diagnosticCandidates.ifEmpty { value.items }
            else value.items.ifEmpty { value.diagnosticCandidates }
            return PreviewListResult(diagnostic, PreviewListSource.DIAGNOSTIC)
        }

        if (isDocument) {
            val snapshot = value.matchingRootsSnapshot
            if (snapshot != null && snapshot.userId == request.userId &&
                snapshot.requestKind == kind?.name &&
                value.pickerProbeStatus == PickerProbeStatus.OBSERVED_AFTER_TEST
            ) {
                return PreviewListResult(snapshot.roots.map { root ->
                    SystemProbeItem(root.label, root.packageName, root.syntheticId, root.label)
                }, PreviewListSource.DOCUMENT_ROOTS)
            }
            return PreviewListResult(emptyList(), PreviewListSource.UNAVAILABLE)
        }

        val snapshot = value.runtimeReturn
        val recent = snapshot != null && snapshot.callerUserId == request.userId &&
            nowMillis - snapshot.capturedAtMillis in 0L..MAX_AGE_MS
        val capturedAfterLaunch = launchedAtMillis != null && snapshot != null &&
            snapshot.capturedAtMillis >= launchedAtMillis
        val sourceMatches = when (kind) {
            IntentKind.ACCESSIBILITY, IntentKind.PRINT ->
                snapshot?.source == "SYSTEM_MANAGER_RETURN"
            IntentKind.CREDENTIAL_PROVIDER ->
                snapshot?.source == "CREDENTIAL_MANAGER_RETURN"
            else -> snapshot?.source != null
        }
        val callerMatches = !isManager ||
            (snapshot != null && snapshot.callerUid == currentCallerUid && currentCallerUid >= 0)
        val actionMatches = snapshot?.source != "PACKAGE_MANAGER_FILTERED_RETURN" ||
            (expectedAction != null && snapshot.requestAction == expectedAction)
        val attributable = if (isManager) callerMatches else capturedAfterLaunch
        if (recent && sourceMatches && attributable && actionMatches && snapshot != null) {
            return PreviewListResult(asEntries(snapshot, value.items), PreviewListSource.HOOK_OBSERVATION)
        }

        if (isManager) {
            // The host result is independently classified and only supplied when
            // the repository has verified that its caller UID belongs to a system
            // Settings package. Shared UIDs can still include multiple processes:
            // do NOT call this the final rendered Settings UI.
            val host = value.hostReturn
            val validHostSource = when (kind) {
                IntentKind.ACCESSIBILITY, IntentKind.PRINT ->
                    host?.source == "SYSTEM_MANAGER_RETURN"
                IntentKind.CREDENTIAL_PROVIDER ->
                    host?.source == "CREDENTIAL_MANAGER_RETURN"
                else -> false
            }
            if (host != null && host.callerUid != currentCallerUid &&
                host.callerUid >= 0 && host.callerUserId == request.userId &&
                nowMillis - host.capturedAtMillis in 0L..MAX_AGE_MS && validHostSource
            ) return PreviewListResult(
                asEntries(host, value.items), PreviewListSource.HOST_OBSERVATION
            )
            return PreviewListResult(emptyList(), PreviewListSource.UNAVAILABLE)
        }

        return PreviewListResult(
            value.items.ifEmpty { value.diagnosticCandidates }, PreviewListSource.API_RETURN
        )
    }

    private fun asEntries(
        snapshot: RuntimeInterfaceSnapshot,
        apiItems: List<SystemProbeItem>,
    ): List<SystemProbeItem> {
        if (snapshot.components.isNotEmpty()) {
            return snapshot.components.map { id ->
                val pkg = id.substringBefore('/')
                val klass = id.substringAfter('/')
                val match = apiItems.firstOrNull { it.component == id }
                SystemProbeItem(
                    label = match?.label ?: klass.substringAfterLast('.'),
                    packageName = pkg,
                    className = klass,
                    appLabel = match?.appLabel ?: apiItems.firstOrNull {
                        it.packageName == pkg
                    }?.appLabel ?: pkg,
                )
            }
        }
        return snapshot.packages.map { pkg ->
            val match = apiItems.firstOrNull { it.packageName == pkg }
            SystemProbeItem(
                label = match?.appLabel ?: pkg,
                packageName = pkg,
                className = "@observed/" + snapshot.kind,
                appLabel = match?.appLabel ?: pkg,
            )
        }
    }
}
