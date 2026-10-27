package com.yagay.ListCleaner.ui

/**
 * The Root checkbox means "disable this component", not LSPosed selection.
 * Do not clear a staged edit unless a fresh system snapshot reports exactly
 * the requested disabled state. null means the system state is unknown.
 *
 * No inference is made from an exit code, batch progress or the previous scan.
 */
internal fun verifiedComponentDraftKeys(
    changes: Map<String, Boolean>,
    observedDisabled: Map<String, Boolean?>,
): Set<String> = changes.keys.filterTo(linkedSetOf()) { id ->
    observedDisabled[id] != null && observedDisabled[id] == changes[id]
}
