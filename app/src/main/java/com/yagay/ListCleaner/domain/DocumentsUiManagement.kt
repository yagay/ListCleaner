package com.yagay.ListCleaner.domain

/** One management policy for Android's SAF documents, not for the unrelated Photo Picker. */
val DOCUMENTS_UI_SOURCE_KINDS: Set<IntentKind> = setOf(
    IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT,
    IntentKind.DOCUMENT_PROVIDER, IntentKind.DOCUMENT_ROOT,
)
val DOCUMENTS_UI_PREVIEW_KINDS: List<IntentKind> = listOf(
    IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT, IntentKind.GET_CONTENT,
    IntentKind.DOCUMENT_PROVIDER, IntentKind.DOCUMENT_ROOT,
)
fun IntentKind.isLegacyDocumentsUiCategory(): Boolean =
    this in DOCUMENTS_UI_SOURCE_KINDS && this != IntentKind.DOCUMENT_PROVIDER

fun documentsUiRule(packageName: String): ComponentRule =
    ComponentRule(IntentKind.DOCUMENT_PROVIDER, packageName, "@documentsui")

/** Aggregate independent real scans, without implying every app implements every action. */
fun unifyDocumentsUiCandidates(items: List<ComponentCandidate>): List<ComponentCandidate> {
    val doc = items.filter { it.rule.kind in DOCUMENTS_UI_SOURCE_KINDS }
    if (doc.isEmpty()) return items
    val unified = doc.groupBy { it.rule.packageName }.map { (pkg, matches) ->
        val first = matches.firstOrNull { it.isCatalogCandidate } ?: matches.first()
        first.copy(
            rule = documentsUiRule(pkg),
            activityLabel = first.appLabel,
            evidence = matches.flatMap { item ->
                listOf("DOCUMENTS_UI_SOURCE kind=${item.rule.kind} component=${item.rule.className}") +
                    item.evidence
            }.distinct().take(256),
            unavailable = matches.all { it.unavailable },
            restricted = matches.all { it.restricted },
            broadMatch = matches.all { it.broadMatch },
        )
    }
    return (items.filterNot { it.rule.kind in DOCUMENTS_UI_SOURCE_KINDS } + unified)
        .sortedWith(compareBy({ it.rule.kind.ordinal }, { it.appLabel.lowercase() }, { it.rule.id }))
}

/** Repeated validation is idempotent; UserSpaceRules goes through the same migration. */
fun ModuleConfig.unifiedDocumentsUiManagement(): ModuleConfig {
    val old = rules.filter { it.kind in DOCUMENTS_UI_SOURCE_KINDS }
    val newRules = (rules.filterNot { it.kind in DOCUMENTS_UI_SOURCE_KINDS } +
        old.map { documentsUiRule(it.packageName) }).toSet()
    val newOrders = priorities.apps.toMutableMap()
    val mergedOrder = (newOrders[IntentKind.DOCUMENT_PROVIDER].orEmpty() +
        DOCUMENTS_UI_PREVIEW_KINDS.flatMap { priorities.apps[it].orEmpty() })
        .distinct().take(200)
    DOCUMENTS_UI_SOURCE_KINDS.forEach { newOrders.remove(it) }
    if (mergedOrder.isNotEmpty()) newOrders[IntentKind.DOCUMENT_PROVIDER] = mergedOrder
    val overrides = modeOverrides.toMutableMap()
    val docMode = overrides[IntentKind.DOCUMENT_PROVIDER]
        ?: DOCUMENTS_UI_PREVIEW_KINDS.firstNotNullOfOrNull { overrides[it] }
    DOCUMENTS_UI_SOURCE_KINDS.forEach { overrides.remove(it) }
    if (docMode != null) overrides[IntentKind.DOCUMENT_PROVIDER] = docMode
    val disabled = disabledEntryKinds.filterNot { it.isLegacyDocumentsUiCategory() }.toSet()
    if (newRules == rules && newOrders == priorities.apps &&
        overrides == modeOverrides && disabled == disabledEntryKinds) return this
    return copy(
        rules = newRules, priorities = priorities.copy(apps = newOrders),
        modeOverrides = overrides, disabledEntryKinds = disabled,
    )
}
