package com.yagay.ListCleaner.domain

data class OpenPreviewItem(
    val candidate: ComponentCandidate,
    val selected: Boolean,
    val selectedBy: String?,
    val included: Boolean,
    val rank: Int?
)

data class OpenEffectPreview(
    val preset: OpenPreset?,
    val rawCount: Int,
    val finalCount: Int,
    val restoredEmpty: Boolean,
    val items: List<OpenPreviewItem>
)

/**
 * Local approximation of the resolver result after List Cleaner rules are applied.
 * It intentionally does not model caller-UID same-app protection or vendor-private menus.
 */
fun previewOpenEffect(
    candidates: List<ComponentCandidate>,
    genericSelected: Set<ComponentRule>,
    mode: DisplayMode,
    priorities: PriorityConfig,
    openTypes: OpenTypeConfig,
    mimeType: String?,
    scheme: String?,
    fileNameOrPath: String?
): OpenEffectPreview {
    val preset = matchOpenPreset(IntentKind.OPEN, mimeType, scheme, fileNameOrPath)
    val genericIds = genericSelected.asSequence().filter { it.kind == IntentKind.OPEN }.map { it.id }.toSet()
    val typedIds = preset?.let { openTypes.rules[it].orEmpty() }.orEmpty()
    val hasSelection = genericIds.isNotEmpty() || typedIds.isNotEmpty()

    val raw = candidates.filter { it.rule.kind == IntentKind.OPEN }
    val initiallyIncluded = raw.filter { candidate ->
        val selected = candidate.rule.id in genericIds || candidate.rule.id in typedIds
        mode.includes(selected, hasSelection)
    }
    val restoredEmpty = FilterPolicy.restoreEmpty(IntentKind.OPEN.name, raw.size, initiallyIncluded.size)
    val kept = if (restoredEmpty) raw else initiallyIncluded

    val savedPriority = preset?.let { openTypes.priorities[it].orEmpty() }
        .takeUnless { it.isNullOrEmpty() }
        ?: priorities.apps[IntentKind.OPEN].orEmpty()
    val ranks = savedPriority.filter { pkg -> kept.any { it.rule.packageName == pkg } }
        .mapIndexed { index, pkg -> pkg to index + 1 }.toMap()

    val keptIds = kept.map { it.rule.id }.toSet()
    val items = raw.map { candidate ->
        val inGeneric = candidate.rule.id in genericIds
        val inTyped = candidate.rule.id in typedIds
        OpenPreviewItem(
            candidate = candidate,
            selected = inGeneric || inTyped,
            selectedBy = when {
                inGeneric && inTyped -> "全部 + ${preset?.title ?: "分类型"}"
                inGeneric -> "全部"
                inTyped -> preset?.title ?: "分类型"
                else -> null
            },
            included = candidate.rule.id in keptIds,
            rank = ranks[candidate.rule.packageName]
        )
    }.sortedWith(compareBy<OpenPreviewItem>({ if (it.included) 0 else 1 }, { it.rank ?: Int.MAX_VALUE },
        { it.candidate.appLabel.lowercase() }, { it.candidate.rule.id }))

    return OpenEffectPreview(preset, raw.size, kept.size, restoredEmpty, items)
}
