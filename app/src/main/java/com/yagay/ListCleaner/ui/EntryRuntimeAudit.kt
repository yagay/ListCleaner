package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.EmptyResultBehavior
import com.yagay.ListCleaner.domain.EntryRuntimeDefinition
import com.yagay.ListCleaner.domain.EntryRuntimePath
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind
import com.yagay.ListCleaner.domain.runtimeDefinition

/** Builds a machine-friendly cross-check between manager discovery/config and real hook evidence. */
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
        appendLine("IMPORTANT: missing runtime evidence means UNKNOWN until that authority surface is exercised; it is not proof of failure.")
        appendLine("coverageGap is source-code authority coverage, not a live-device observation.")
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
                val status = when {
                    selected == 0 -> "UNCONFIGURED"
                    evidence.filterObserved > 0 && evidence.restoreObserved > 0 -> "FILTER_OBSERVED_RESTORE_HISTORY"
                    evidence.filterObserved > 0 -> "FILTER_OBSERVED"
                    evidence.restoreObserved > 0 -> "EMPTY_RESULT_RESTORED"
                    evidence.failed > 0 && evidence.hookReady == 0 -> "HOOK_ERROR_SEEN"
                    evidence.queryObserved > 0 || evidence.hookReady > 0 -> "RUNTIME_SEEN_NOT_FILTER_CONFIRMED"
                    else -> "NO_RUNTIME_EVIDENCE"
                }

                val risks = linkedSetOf<String>()
                if (definition == null) {
                    risks += "NO_RUNTIME_DEFINITION"
                } else {
                    definition.missingPaths.forEach { risks += "COVERAGE_GAP_${it.name}" }
                    if (definition.systemCallerBypassPossible) risks += "SYSTEM_CALLER_BYPASS_POSSIBLE"
                    val canRestoreEmpty = definition.coveredPaths.any { path ->
                        definition.emptyBehavior[path] == EmptyResultBehavior.RESTORE_ORIGINAL
                    }
                    if (selected > 0 && discovered > 0 && selected >= discovered && canRestoreEmpty) {
                        risks += "EMPTY_RESULT_GUARD_MAY_RESTORE"
                    }
                }
                if (selected > 0 && discovered == 0) risks += "CONFIGURED_NOT_DISCOVERED"
                if (selectedUnavailable > 0) risks += "SELECTED_UNAVAILABLE=$selectedUnavailable"
                if (evidence.restoreObserved > 0) risks += "RESTORE_ALL_SEEN_IN_LOG"
                if (evidence.failed > 0) risks += "HOOK_FAILURE_SEEN"
                if (selected > 0 && evidence.hookReady == 0 && evidence.queryObserved == 0 && evidence.filterObserved == 0) {
                    risks += "NO_RUNTIME_EVIDENCE"
                }

                append("kind=${kind.name}")
                append(" discovered=$discovered unavailable=$unavailable selected=$selected")
                append(" status=$status")
                append(" expectedPaths=${definition?.expectedPaths?.joinToString("+") { it.name } ?: "none"}")
                append(" coveredPaths=${definition?.coveredPaths?.joinToString("+") { it.name } ?: "none"}")
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

    private fun relevant(kind: IntentKind, definition: EntryRuntimeDefinition?, line: String): Boolean {
        val coveredPaths = definition?.coveredPaths.orEmpty()
        if (Regex("\\bkind=${Regex.escape(kind.name)}\\b").containsMatchIn(line)) return true

        val roleName = definition?.roleName
        if (roleName != null &&
            (line.contains("ListCleaner.RoleController") || line.contains("role=$roleName"))) {
            return line.contains("kind=${kind.name}") || line.contains("role=$roleName") || line.contains("HOOK")
        }
        if (kind == IntentKind.DIRECT_SHARE) {
            if (line.contains("ListCleaner.DirectShare") || line.contains("ListCleaner.EmbeddedDirectShare")) return true
            if (line.contains("ListCleaner.ShortcutSurface") &&
                (line.contains("DIRECT_") || line.contains("DIRECT_SHARE") || line.contains("getShareTargets"))) return true
        }
        if (kind == IntentKind.SHORTCUT_ITEM && line.contains("ListCleaner.ShortcutSurface")) {
            return !line.contains("DIRECT_") && !line.contains("DIRECT_SHARE") && !line.contains("getShareTargets")
        }

        if (line.contains("ListCleaner.SystemManagers") && managerLogMatches(kind, line)) return true
        if (kind == IntentKind.NFC_HCE && line.contains("ListCleaner.NfcPayment")) return true
        if (kind in setOf(IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER) &&
            line.contains("ListCleaner.CombinedProviders")) return true
        if (line.contains("ListCleaner.SettingsAuthority")) {
            if (kind == IntentKind.VPN && line.contains("VPN_")) return true
            if (kind == IntentKind.AUTOFILL && line.contains("AUTOFILL_")) return true
            if (line.contains("HOOK") &&
                (EntryRuntimePath.SETTINGS_VPN in coveredPaths ||
                    EntryRuntimePath.SETTINGS_AUTOFILL_PICKER in coveredPaths)
            ) return true
        }

        if ((EntryRuntimePath.PACKAGE_MANAGER_SERVICE in coveredPaths ||
                EntryRuntimePath.PACKAGE_MANAGER_PROVIDER in coveredPaths ||
                EntryRuntimePath.PACKAGE_MANAGER_ACTIVITY in coveredPaths) &&
            line.contains("ListCleaner.PmEntries")
        ) {
            return line.contains("HOOKS_READY") || line.contains("HOOK_INSTALLED") ||
                line.contains("kind=${kind.name}")
        }

        if (EntryRuntimePath.RESOLVER_ACTIVITY in coveredPaths) {
            if (line.contains(" SYSTEM ${kind.name} ") || line.contains(" RESOLVER ${kind.name} ")) return true
            if ((line.contains("ListCleaner.Diagnostic") || line.contains("ListCleaner:")) &&
                (line.contains("SYSTEM_HOOKS") || line.contains("RESOLVER_HOOKS") || line.contains("HOOK_INSTALLED"))) {
                return true
            }
        }
        return false
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
