package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.EmptyResultBehavior
import com.yagay.ListCleaner.domain.EntryRuntimeDefinition
import com.yagay.ListCleaner.domain.EntryRuntimePath
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind
import com.yagay.ListCleaner.domain.runtimeDefinition

/** Builds a machine-friendly cross-check between declared runtime paths and real hook evidence. */
internal object EntryRuntimeAudit {
    private data class Evidence(
        var hookReady: Int = 0,
        var queryObserved: Int = 0,
        var filterObserved: Int = 0,
        var restoreObserved: Int = 0,
        var failed: Int = 0,
    )

    fun report(state: MainState, runtimeLogText: String): String = buildString {
        appendLine("Entry runtime audit")
        appendLine("generatedFrom=current manager state + bounded historical runtime logs")
        appendLine("IMPORTANT: expectedPaths come from source definitions; installedPaths/observedPaths require log evidence from this device.")
        appendLine("Missing installation evidence is UNKNOWN when old startup logs have already rotated; it is not proof of failure.")
        appendLine()

        val lines = runtimeLogText.lineSequence()
            .filter { it.contains("ListCleaner") }
            .take(50_000)
            .toList()

        IntentKind.entries
            .filter(IntentKind::isSelectableEntryKind)
            .forEach { kind ->
                val definition = kind.runtimeDefinition()
                val candidates = state.candidates.filter { it.rule.kind == kind }
                val discovered = candidates.count { !it.unavailable }
                val unavailable = candidates.count { it.unavailable }
                val selected = state.selected.count { it.kind == kind }
                val selectedUnavailable = candidates.count { it.unavailable && it.rule in state.selected }
                val evidence = collectEvidence(kind, definition, lines)
                val installedPaths = definition?.expectedPaths.orEmpty().filterTo(linkedSetOf()) { path ->
                    installationSeen(kind, path, lines)
                }
                val observedPaths = definition?.expectedPaths.orEmpty().filterTo(linkedSetOf()) { path ->
                    runtimeSeen(kind, path, lines)
                }
                val status = when {
                    selected == 0 -> "UNCONFIGURED"
                    evidence.filterObserved > 0 && evidence.restoreObserved > 0 -> "FILTER_OBSERVED_RESTORE_HISTORY"
                    evidence.filterObserved > 0 -> "FILTER_OBSERVED"
                    evidence.restoreObserved > 0 -> "EMPTY_RESULT_RESTORED"
                    evidence.failed > 0 && installedPaths.isEmpty() -> "HOOK_ERROR_SEEN"
                    observedPaths.isNotEmpty() || installedPaths.isNotEmpty() -> "RUNTIME_SEEN_NOT_FILTER_CONFIRMED"
                    else -> "NO_RUNTIME_EVIDENCE"
                }

                val risks = linkedSetOf<String>()
                if (definition == null) {
                    risks += "NO_RUNTIME_DEFINITION"
                } else {
                    if (definition.systemCallerBypassPossible) risks += "SYSTEM_CALLER_BYPASS_POSSIBLE"
                    val canRestoreEmpty = definition.expectedPaths.any { path ->
                        definition.emptyBehavior[path] == EmptyResultBehavior.RESTORE_ORIGINAL
                    }
                    if (selected > 0 && discovered > 0 && selected >= discovered && canRestoreEmpty) {
                        risks += "EMPTY_RESULT_GUARD_MAY_RESTORE"
                    }
                    if (selected > 0) {
                        (definition.expectedPaths - installedPaths).forEach { path ->
                            risks += "INSTALL_EVIDENCE_MISSING_${path.name}"
                        }
                    }
                }
                if (selected > 0 && discovered == 0) risks += "CONFIGURED_NOT_DISCOVERED"
                if (selectedUnavailable > 0) risks += "SELECTED_UNAVAILABLE=$selectedUnavailable"
                if (evidence.restoreObserved > 0) risks += "RESTORE_ALL_SEEN_IN_LOG"
                if (evidence.failed > 0) risks += "HOOK_FAILURE_SEEN"
                if (selected > 0 && installedPaths.isEmpty() && observedPaths.isEmpty()) risks += "NO_RUNTIME_EVIDENCE"

                append("kind=${kind.name}")
                append(" discovered=$discovered unavailable=$unavailable selected=$selected")
                append(" status=$status")
                append(" expectedPaths=${definition?.expectedPaths?.joinToString("+") { it.name } ?: "none"}")
                append(" installedPaths=${installedPaths.joinToString("+") { it.name }.ifEmpty { "none" }}")
                append(" observedPaths=${observedPaths.joinToString("+") { it.name }.ifEmpty { "none" }}")
                append(" hookReady=${evidence.hookReady}")
                append(" queryObserved=${evidence.queryObserved}")
                append(" filterObserved=${evidence.filterObserved}")
                append(" restoreObserved=${evidence.restoreObserved}")
                append(" failed=${evidence.failed}")
                append(" risks=${if (risks.isEmpty()) "none" else risks.joinToString(",")}")
                definition?.roleName?.let { append(" role=$it") }
                appendLine()
            }
    }

    private fun collectEvidence(
        kind: IntentKind,
        definition: EntryRuntimeDefinition?,
        lines: List<String>,
    ): Evidence {
        val evidence = Evidence()
        lines.forEach { line ->
            if (!relevant(kind, definition, line)) return@forEach
            when {
                isFailure(line) -> evidence.failed++
                isRestore(line) -> evidence.restoreObserved++
                isFilter(line) -> evidence.filterObserved++
                isQuery(line) -> evidence.queryObserved++
                isHookReady(line) -> evidence.hookReady++
            }
        }
        return evidence
    }

    private fun installationSeen(kind: IntentKind, path: EntryRuntimePath, lines: List<String>): Boolean =
        lines.any { line -> pathInstallationLine(kind, path, line) }

    private fun runtimeSeen(kind: IntentKind, path: EntryRuntimePath, lines: List<String>): Boolean =
        lines.any { line -> pathRuntimeLine(kind, path, line) }

    private fun pathInstallationLine(kind: IntentKind, path: EntryRuntimePath, line: String): Boolean = when (path) {
        EntryRuntimePath.RESOLVER_ACTIVITY ->
            (line.contains("ListCleaner.Diagnostic") || line.contains("ListCleaner:")) &&
                (line.contains("SYSTEM_HOOKS") || line.contains("RESOLVER_HOOKS") || line.contains("ic-query-filter"))
        EntryRuntimePath.ROLE_CONTROLLER ->
            line.contains("ListCleaner.RoleController") && isHookReady(line)
        EntryRuntimePath.DIRECT_SHARE_CHOOSER ->
            line.contains("ListCleaner.DirectShare") && isHookReady(line)
        EntryRuntimePath.DIRECT_SHARE_EMBEDDED ->
            line.contains("ListCleaner.EmbeddedDirectShare") && isHookReady(line)
        EntryRuntimePath.SHORTCUT_SERVICE ->
            line.contains("ListCleaner.ShortcutSurface") && isHookReady(line)
        EntryRuntimePath.PACKAGE_MANAGER_ACTIVITY,
        EntryRuntimePath.PACKAGE_MANAGER_PROVIDER,
        EntryRuntimePath.PACKAGE_MANAGER_SERVICE ->
            line.contains("ListCleaner.PmEntries") && isHookReady(line)
        EntryRuntimePath.ACCESSIBILITY_MANAGER ->
            line.contains("ListCleaner.SystemManagers") &&
                (line.contains("lc-accessibility-manager") || line.contains("kind=ACCESSIBILITY")) && isHookReady(line)
        EntryRuntimePath.INPUT_METHOD_MANAGER ->
            line.contains("ListCleaner.SystemManagers") &&
                (line.contains("lc-ime-manager") || line.contains("kind=INPUT_METHOD")) && isHookReady(line)
        EntryRuntimePath.PRINT_MANAGER ->
            line.contains("ListCleaner.SystemManagers") &&
                (line.contains("lc-print-manager") || line.contains("kind=PRINT")) && isHookReady(line)
        EntryRuntimePath.CREDENTIAL_MANAGER ->
            line.contains("ListCleaner.SystemManagers") &&
                (line.contains("lc-credential-manager") || line.contains("CREDENTIAL_")) && isHookReady(line)
        EntryRuntimePath.COMBINED_PROVIDER_SETTINGS ->
            line.contains("ListCleaner.CombinedProviders") && isHookReady(line)
        EntryRuntimePath.SETTINGS_AUTOFILL_PICKER ->
            line.contains("ListCleaner.SettingsAuthority") &&
                (line.contains("lc-settings-autofill-picker") || line.contains("AUTOFILL_")) && isHookReady(line)
        EntryRuntimePath.SETTINGS_VPN ->
            line.contains("ListCleaner.SettingsAuthority") &&
                (line.contains("lc-settings-vpn-authority") || line.contains("VPN_")) && isHookReady(line)
        EntryRuntimePath.VPN_APP_OPS -> line.contains("VPN_APPOPS") && isHookReady(line)
        EntryRuntimePath.NFC_CARD_EMULATION -> line.contains("ListCleaner.NfcPayment") && isHookReady(line)
    }

    private fun pathRuntimeLine(kind: IntentKind, path: EntryRuntimePath, line: String): Boolean {
        if (pathInstallationLine(kind, path, line) && !isQuery(line) && !isFilter(line)) return false
        return when (path) {
            EntryRuntimePath.RESOLVER_ACTIVITY ->
                (line.contains("ListCleaner.Diagnostic") || line.contains("ListCleaner:")) &&
                    (line.contains(" $kind ") || line.contains("kind=$kind")) && (isQuery(line) || isFilter(line))
            EntryRuntimePath.ROLE_CONTROLLER ->
                line.contains("ListCleaner.RoleController") && line.contains("kind=$kind") && (isQuery(line) || isFilter(line))
            EntryRuntimePath.DIRECT_SHARE_CHOOSER ->
                line.contains("ListCleaner.DirectShare") && (isQuery(line) || isFilter(line))
            EntryRuntimePath.DIRECT_SHARE_EMBEDDED ->
                line.contains("ListCleaner.EmbeddedDirectShare") && (isQuery(line) || isFilter(line))
            EntryRuntimePath.SHORTCUT_SERVICE -> {
                if (!line.contains("ListCleaner.ShortcutSurface")) false
                else if (kind == IntentKind.DIRECT_SHARE) line.contains("DIRECT_") || line.contains("getShareTargets")
                else line.contains("SHORTCUT_") || line.contains("getShortcuts") || line.contains("RESTORE_ALL_SHORTCUTS")
            }
            EntryRuntimePath.PACKAGE_MANAGER_ACTIVITY,
            EntryRuntimePath.PACKAGE_MANAGER_PROVIDER,
            EntryRuntimePath.PACKAGE_MANAGER_SERVICE ->
                line.contains("ListCleaner.PmEntries") && line.contains("kind=$kind") && (isQuery(line) || isFilter(line))
            EntryRuntimePath.ACCESSIBILITY_MANAGER,
            EntryRuntimePath.INPUT_METHOD_MANAGER,
            EntryRuntimePath.PRINT_MANAGER,
            EntryRuntimePath.CREDENTIAL_MANAGER ->
                line.contains("ListCleaner.SystemManagers") && managerLogMatches(kind, line) && (isQuery(line) || isFilter(line))
            EntryRuntimePath.COMBINED_PROVIDER_SETTINGS ->
                line.contains("ListCleaner.CombinedProviders") && (isQuery(line) || isFilter(line))
            EntryRuntimePath.SETTINGS_AUTOFILL_PICKER ->
                line.contains("ListCleaner.SettingsAuthority") && line.contains("AUTOFILL_") && (isQuery(line) || isFilter(line))
            EntryRuntimePath.SETTINGS_VPN ->
                line.contains("ListCleaner.SettingsAuthority") && line.contains("VPN_") && (isQuery(line) || isFilter(line))
            EntryRuntimePath.VPN_APP_OPS -> line.contains("VPN_APPOPS") && (isQuery(line) || isFilter(line))
            EntryRuntimePath.NFC_CARD_EMULATION ->
                line.contains("ListCleaner.NfcPayment") && (isQuery(line) || isFilter(line) || line.contains("AUTHORITY_SNAPSHOT"))
        }
    }

    private fun relevant(kind: IntentKind, definition: EntryRuntimeDefinition?, line: String): Boolean {
        if (Regex("\\bkind=${Regex.escape(kind.name)}\\b").containsMatchIn(line)) return true
        val roleName = definition?.roleName
        if (roleName != null && line.contains("ListCleaner.RoleController") && line.contains(roleName)) return true
        return definition?.expectedPaths.orEmpty().any { path ->
            pathInstallationLine(kind, path, line) || pathRuntimeLine(kind, path, line)
        }
    }

    private fun managerLogMatches(kind: IntentKind, line: String): Boolean = when (kind) {
        IntentKind.ACCESSIBILITY -> line.contains("kind=ACCESSIBILITY") || line.contains("lc-accessibility-manager")
        IntentKind.INPUT_METHOD -> line.contains("kind=INPUT_METHOD") || line.contains("lc-ime-manager") || line.contains("IME_BRIDGE")
        IntentKind.PRINT -> line.contains("kind=PRINT") || line.contains("lc-print-manager")
        IntentKind.CREDENTIAL_PROVIDER -> line.contains("CREDENTIAL_") || line.contains("lc-credential-manager")
        IntentKind.VPN -> line.contains("VPN_APPOPS") || line.contains("lc-vpn-appops")
        else -> false
    }

    private fun isHookReady(line: String): Boolean {
        if (line.contains("HOOK_INSTALLED") || line.contains("SYSTEM_HOOKS") ||
            line.contains("RESOLVER_HOOKS") || line.contains("PROFILE_READY")
        ) return true
        if (line.contains("HOOKS_READY")) return !ZERO_TOTAL.containsMatchIn(line)
        return false
    }

    private fun isQuery(line: String): Boolean =
        line.contains(" QUERY ") || line.contains(" HIT ") || line.contains("_HIT ") ||
            line.contains("MANAGER_HIT") || line.contains("IME_BRIDGE") ||
            line.contains("LISTS_EMPTY") || line.contains("AUTHORITY_OBSERVED") ||
            line.contains("AUTHORITY_SNAPSHOT")

    private fun isFilter(line: String): Boolean =
        line.contains(" FILTER ") || line.contains("DIRECT_FILTER") || line.contains("FILTERED") ||
            line.contains("SHORTCUT_FILTER") || line.contains("MANAGER_FILTER") ||
            line.contains("CREDENTIAL_FILTER") || line.contains("VPN_APPOPS_FILTER") ||
            line.contains("VPN_FILTER") || line.contains("AUTOFILL_LEGACY_FILTER") ||
            line.contains("RESULT kind=") ||
            (line.contains(" before=") && line.contains(" after=")) ||
            ARROW_FILTER.containsMatchIn(line)

    private fun isRestore(line: String): Boolean =
        line.contains("RESTORE_ALL") || line.contains("RESTORE_ORIGINAL")

    private fun isFailure(line: String): Boolean =
        line.contains("HOOK_FAILED") || line.contains("HOT_RELOAD_FAILED") ||
            line.contains("UNSUPPORTED") || line.contains("CLASS_UNAVAILABLE") ||
            line.contains("ADAPTER_CLASS_UNAVAILABLE") || line.contains("ROLE_MODEL_UNAVAILABLE") ||
            line.contains("VPN_OPS_UNAVAILABLE") || line.contains("AUTHORITY_CLASSES_UNAVAILABLE")

    private val ZERO_TOTAL = Regex("\\btotal=0\\b")
    private val ARROW_FILTER = Regex("\\b\\d+\\s*->\\s*\\d+\\b")
}
