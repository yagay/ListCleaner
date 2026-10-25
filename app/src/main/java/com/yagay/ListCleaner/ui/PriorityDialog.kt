package com.yagay.ListCleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.supportsTitleEditing
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.entryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.AppType
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.PriorityListFilter
import com.yagay.ListCleaner.domain.matchesOpenPreset
import com.yagay.ListCleaner.domain.matchesBrowserHost
import com.yagay.ListCleaner.domain.priorityAppGroups
import com.yagay.ListCleaner.domain.moveVisiblePriority
import com.yagay.ListCleaner.domain.moveVisiblePriorityTo
 import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriorityDialogContent(
    state: MainState, vm: MainViewModel,
    pendingNavigation: PendingNavigationGuard? = null
) {
    val navigate: (() -> Unit) -> Unit = { action ->
        if (pendingNavigation != null) pendingNavigation.request(action) else action()
    }
    var systemProbeRequest by remember { mutableStateOf<SystemProbeRequest?>(null) }
    systemProbeRequest?.let { request ->
        SystemProbeDialog(request, onDismiss = { systemProbeRequest = null })
    }
    var editingTitle by remember { mutableStateOf<ComponentCandidate?>(null) }
    var showCustomTypes by rememberSaveable { mutableStateOf(false) }
    var showBrowserHosts by rememberSaveable { mutableStateOf(false) }
    editingTitle?.let { item ->
        ComponentTitleDialog(
            item,
            state.priorities.titles[item.rule.id],
            onSave = { vm.setComponentTitle(item.rule.id, it) },
            onDismiss = { editingTitle = null }
        )
    }
    if (showCustomTypes) {
        CustomOpenTypeDialog(
            config = state.openTypesExplicit,
            onSave = vm::setCustomOpenDefinition,
            onDismiss = { showCustomTypes = false }
        )
    }
    if (showBrowserHosts) {
        BrowserHostDialog(
            config = state.browserLinks,
            onSave = vm::setBrowserHosts,
            onDismiss = { showBrowserHosts = false }
        )
    }

    val priorityKinds = HomeLayoutPolicy.sortableKinds
    val kind = HomeLayoutPolicy.priorityKind(state.filter)
    val openPreset by vm.ruleOpenPreset.collectAsStateWithLifecycle()
    val browserHost by vm.ruleBrowserHost.collectAsStateWithLifecycle()
    var confirmPriorityReset by remember { mutableStateOf(false) }
    val prefsContext = LocalContext.current
    val confirmation = rememberSelectionConfirmationController()
    SelectionConfirmationDialog(confirmation)
    var sortHelpExpanded by rememberSaveable { mutableStateOf(UiPreferencesRuntime.current.openHelpByDefault) }
    var viewFilter by rememberSaveable { mutableStateOf(UiPreferencesStore.initialFilter(prefsContext, "priority")) }
    var appTypeFilter by rememberSaveable { mutableStateOf(UiPreferencesStore.initialAppType(prefsContext, "priority")) }
    val bulkLockRevision by vm.bulkLockRevision.collectAsStateWithLifecycle()
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(kind) {
        if (kind != IntentKind.OPEN) vm.setRuleOpenPreset(null)
        if (kind != IntentKind.DEEP_LINK) vm.setRuleBrowserHost(null)
    }
    LaunchedEffect(state.openTypesExplicit.customDefinitions, openPreset) {
        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) vm.setRuleOpenPreset(null)
    }
    LaunchedEffect(state.browserAvailableHosts, browserHost) {
        if (browserHost != null && browserHost !in state.browserAvailableHosts) vm.setRuleBrowserHost(null)
    }

    val typedOpenPreset = openPreset.takeIf { kind == IntentKind.OPEN }
    val deepLinkHost = browserHost.takeIf { kind == IntentKind.DEEP_LINK }

    val typedSelected = typedOpenPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val explicitDeepLinkSelected = deepLinkHost?.let { state.browserLinks.selectedRules(it) }.orEmpty()
    val genericDeepLinkSelected = state.selected.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }
    val deepLinkSelected = genericDeepLinkSelected + explicitDeepLinkSelected
    val deepLinkScoped = deepLinkHost != null
    val scopedCandidates = remember(
        state.candidates, typedOpenPreset, deepLinkHost,
        state.openTypesExplicit.customDefinitions, deepLinkSelected
    ) {
        when {
            typedOpenPreset != null ->
                state.candidates.filter { it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions) }
            deepLinkHost != null -> state.candidates.filter {
                it.rule.kind == IntentKind.DEEP_LINK &&
                    (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
            }
            else -> state.candidates
        }
    }
    val explicitTypedPriority = typedOpenPreset?.let { state.openTypesExplicit.priorities[it].orEmpty() }.orEmpty()
    val genericOpenPriority = state.priorities.apps[IntentKind.OPEN].orEmpty()
    val inheritsOpenPriority = typedOpenPreset != null && explicitTypedPriority.isEmpty() && genericOpenPriority.isNotEmpty()
    val hasExplicitOpenPriority = typedOpenPreset != null && explicitTypedPriority.isNotEmpty()
    val explicitDeepLinkPriority = deepLinkHost?.let { state.browserLinks.priorities[it].orEmpty() }.orEmpty()
    val genericDeepLinkPriority = state.priorities.apps[IntentKind.DEEP_LINK].orEmpty()
    val inheritsDeepLinkPriority = deepLinkScoped && explicitDeepLinkPriority.isEmpty() && genericDeepLinkPriority.isNotEmpty()
    val hasExplicitDeepLinkPriority = deepLinkHost != null && explicitDeepLinkPriority.isNotEmpty()
    val rankedRaw = when {
        typedOpenPreset != null -> state.openTypes.priorities[typedOpenPreset].orEmpty()
        deepLinkHost != null && explicitDeepLinkPriority.isNotEmpty() -> explicitDeepLinkPriority
        deepLinkHost != null -> genericDeepLinkPriority
        else -> state.priorities.apps[kind].orEmpty()
    }
    val lockScope = deepLinkHost?.let(::browserPriorityBulkLockScope) ?: priorityBulkLockScope(kind, openPreset)
    val orderDraft = remember(kind, typedOpenPreset, deepLinkHost, vm.selectedSpace.value) {
        PendingPriorityOrder()
    }
    val stagedOrder = orderDraft.effective(rankedRaw)
    var priorityApplyConflict by remember { mutableStateOf(false) }
    val applyOrder: () -> Boolean = {
        val ok = when {
            typedOpenPreset != null -> vm.applyOpenTypePriorityDraft(typedOpenPreset, rankedRaw, stagedOrder, lockScope)
            deepLinkHost != null -> vm.applyBrowserHostPriorityDraft(deepLinkHost, rankedRaw, stagedOrder, lockScope)
            else -> vm.applyPriorityDraft(kind, rankedRaw, stagedOrder, lockScope)
        }
        if (ok) orderDraft.discard() else priorityApplyConflict = true
        ok
    }
    if (confirmPriorityReset) {
        AdaptiveAlertDialog(
            onDismissRequest = { confirmPriorityReset = false },
            title = { AdaptiveText(stringResource(R.string.priority_reset_confirm_title)) },
            text = { AdaptiveText(stringResource(R.string.priority_reset_confirm_body)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    confirmPriorityReset = false
                    orderDraft.stage(rankedRaw, emptyList())
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirmPriorityReset = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (priorityApplyConflict) {
        AdaptiveAlertDialog(
            onDismissRequest = { priorityApplyConflict = false },
            title = { AdaptiveText(stringResource(R.string.lc_apply_conflict_title)) },
            text = { AdaptiveText(stringResource(R.string.lc_apply_conflict_body)) },
            confirmButton = {
                AdaptiveTextButton(onClick = { priorityApplyConflict = false }) {
                    AdaptiveText(stringResource(R.string.common_ok))
                }
            }
        )
    }
    val scopedExtraSelected = if (deepLinkScoped) explicitDeepLinkSelected else typedSelected
    RegisterPendingNavigation(
        pendingNavigation, orderDraft,
        if (orderDraft.hasChanges()) 1 else 0,
        applyOrder, orderDraft::discard
    )
    val priorityListFilter = when (viewFilter) {
        UiFilter.ALL, UiFilter.LOCKED -> PriorityListFilter.ALL
        UiFilter.HIDE_SELECTED -> PriorityListFilter.UNSELECTED
        UiFilter.SHOW_SELECTED -> PriorityListFilter.SELECTED
    }
    // Locks are a separate overlay. Changing a lock must not regroup and sort
    // the full priority catalog; staged priority changes still update its order.
    val baseGroups = remember(
        scopedCandidates, state.selected, scopedExtraSelected, state.displayMode,
        kind, stagedOrder, state.query, priorityListFilter
    ) {
        priorityAppGroups(
            scopedCandidates, state.selected, state.displayMode, kind,
            stagedOrder, state.query, priorityListFilter, scopedExtraSelected
        )
    }
    val lockFilteredGroups = remember(baseGroups, viewFilter, bulkLockRevision, lockScope) {
        if (viewFilter == UiFilter.LOCKED) {
            baseGroups.filter { vm.bulkLockState(lockScope, it.packageName, emptyList()) != BulkLockState.NONE }
        } else baseGroups
    }
    val groups = remember(lockFilteredGroups, appTypeFilter) {
        lockFilteredGroups.filter { appTypeFilter.matches(it.appType) }
    }
    LaunchedEffect(kind, openPreset, browserHost, groups.firstOrNull()?.packageName, UiPreferencesRuntime.current.expandFirstApp) {
        if (UiPreferencesRuntime.current.expandFirstApp && expandedKey == null) {
            groups.firstOrNull()?.let { group ->
                expandedKey = "${kind.name}|${openPreset?.name ?: browserHost ?: "ALL"}|${group.packageName}"
            }
        }
    }
    val moveTargets = remember(groups) {
        groups.filter { it.rank != null }.sortedBy { it.rank }.map { it.packageName }
    }

    val listState = rememberLazyListState()
    val dragState = remember(listState) { PriorityDragState(listState) }
    val drag = dragState.session
    val currentSaved by rememberUpdatedState(stagedOrder)
    val currentVisible by rememberUpdatedState(moveTargets)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val speed = with(density) { 640.dp.toPx() }
    LaunchedEffect(kind, openPreset, browserHost, viewFilter, appTypeFilter, state.query, stagedOrder, moveTargets) {
        dragState.cancel()
    }
    DisposableEffect(dragState) { onDispose { dragState.cancel() } }
    LaunchedEffect(drag?.packageName) {
        if (dragState.session != null) {
            var previous = withFrameNanos { it }
            while (dragState.session != null) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                val delta = dragState.scrollSpeed(edge, speed) * seconds
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    dragState.retarget()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = if (orderDraft.hasChanges()) 90.dp else 16.dp)
        ) {
            stickyHeader(key = "controls") {
                PageFilterSection {
                    Column {
                        ListControls(
                            state.copy(filter = kind, uiFilter = viewFilter),
                            onFilter = { entry -> if (entry != null) navigate { vm.setFilter(entry); expandedKey = null } },
                            onUiFilter = {
                                viewFilter = it
                                UiPreferencesStore.rememberFilter(prefsContext, it, "priority")
                            },
                            appTypeFilter = appTypeFilter,
                            onAppTypeFilter = {
                                appTypeFilter = it
                                UiPreferencesStore.rememberAppType(prefsContext, it, "priority")
                            },
                            includeAllKinds = false,
                            showEntryGroups = true,
                            availableEntryKinds = priorityKinds,
                            previewActions = listOf(
                                WorkspaceMenuAction(
                                    label = stringResource(kind.titleRes()),
                                    onClick = {
                                        systemProbeRequest = SystemProbeRequest(
                                            entryKind = kind,
                                            userId = vm.selectedSpace.value
                                        )
                                    }
                                )
                            ),
                            viewTitle = {
                                stringResource(
                                    when (it) {
                                        UiFilter.ALL -> R.string.common_all
                                        UiFilter.HIDE_SELECTED -> R.string.priority_unselected
                                        UiFilter.SHOW_SELECTED -> R.string.priority_selected
                                        UiFilter.LOCKED -> R.string.filter_locked
                                    }
                                )
                            },
                            subtypeTitle = when (kind) {
                                IntentKind.OPEN -> openPreset?.let { state.openTypesExplicit.localizedTitle(it) }
                                IntentKind.DEEP_LINK -> browserHost
                                else -> null
                            },
                            subtypeAvailable = when (kind) {
                                IntentKind.OPEN -> state.openTypesExplicit.configuredPresets().isNotEmpty()
                                IntentKind.DEEP_LINK -> state.browserAvailableHosts.isNotEmpty() ||
                                    state.browserLinks.hosts.isNotEmpty() || browserHost != null
                                else -> false
                            },
                            extraFilter = {
                                when (kind) {
                                    IntentKind.OPEN -> OpenPresetFilterMenu(
                                        selected = openPreset,
                                        config = state.openTypesExplicit,
                                        onSelected = { preset -> navigate { vm.setRuleOpenPreset(preset) } },
                                        onManageCustom = { showCustomTypes = true },
                                        iconOnly = true
                                    )
                                    IntentKind.DEEP_LINK -> BrowserHostFilterMenu(
                                        selected = browserHost,
                                        config = state.browserLinks,
                                        availableHosts = state.browserAvailableHosts,
                                        onSelected = { host -> navigate { vm.setRuleBrowserHost(host) } },
                                        onManage = { showBrowserHosts = true },
                                        iconOnly = true
                                    )
                                    else -> Unit
                                }
                            }
                        )
                    }
                }
            }
            item(key = "summary") {
                Column(Modifier.padding(vertical = 6.dp)) {
                    val presetTitle = when {
                        typedOpenPreset != null -> state.openTypes.localizedTitle(typedOpenPreset)
                        deepLinkHost != null -> deepLinkHost
                        else -> null
                    }
                    if (state.loading) {
                        AdaptiveText(stringResource(R.string.priority_count_scanning), style = MaterialTheme.typography.labelLarge)
                    }
                    WorkspaceListSummary(
                        title = stringResource(R.string.app_list_count, groups.size),
                        secondary = stringResource(R.string.priority_drag_help),
                        secondaryMaxLines = 2,
                            actions = listOf(
                                WorkspaceMenuAction(stringResource(R.string.select_all), {
                                    val current = orderDraft.effective(rankedRaw)
                                    val eligible = groups.map { it.packageName }
                                        .filterNot { vm.isBulkAppLocked(lockScope, it) }
                                    val next = (current + eligible.filterNot { it in current }).take(200)
                                    val systemPackages = groups.filter { it.appType == AppType.SYSTEM }
                                        .map { it.packageName }.toSet()
                                    val warnCount = next.count { it !in current && it in systemPackages }
                                    confirmation.request(
                                        prefsContext.getString(R.string.select_all),
                                        systemAppCount = warnCount,
                                        risk = SystemSelectionRisk.PRIORITY
                                    ) { orderDraft.stage(rankedRaw, next) }
                                }, groups.isNotEmpty()),
                                WorkspaceMenuAction(stringResource(R.string.select_none), {
                                    confirmation.request(prefsContext.getString(R.string.select_none)) {
                                        val current = orderDraft.effective(rankedRaw)
                                        val eligible = groups.map { it.packageName }
                                            .filterNot { vm.isBulkAppLocked(lockScope, it) }
                                        orderDraft.stage(rankedRaw, current.filterNot { it in eligible })
                                    }
                                }, groups.isNotEmpty()),
                                WorkspaceMenuAction(stringResource(R.string.invert_selection), {
                                    val current = orderDraft.effective(rankedRaw)
                                    val eligible = groups.map { it.packageName }
                                        .filterNot { vm.isBulkAppLocked(lockScope, it) }
                                    val next = (current.filterNot { it in eligible } +
                                        eligible.filterNot { it in current }).take(200)
                                    val systemPackages = groups.filter { it.appType == AppType.SYSTEM }
                                        .map { it.packageName }.toSet()
                                    val warnCount = next.count { it !in current && it in systemPackages }
                                    confirmation.request(
                                        prefsContext.getString(R.string.invert_selection),
                                        systemAppCount = warnCount,
                                        risk = SystemSelectionRisk.PRIORITY
                                    ) { orderDraft.stage(rankedRaw, next) }
                                }, groups.isNotEmpty())
                            ),
                        helpExpanded = sortHelpExpanded,
                        onHelpToggle = { sortHelpExpanded = !sortHelpExpanded }
                    )
                    WorkspaceHelpSection {

                    if (sortHelpExpanded) {
                        AdaptiveText(
                            stringResource(R.string.priority_intro),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (typedOpenPreset != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AdaptiveText(
                                when {
                                    inheritsOpenPriority -> stringResource(R.string.priority_source_inherited)
                                    hasExplicitOpenPriority -> stringResource(R.string.priority_source_dedicated, presetTitle.orEmpty())
                                    else -> stringResource(R.string.priority_source_none)
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (inheritsOpenPriority) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasExplicitOpenPriority) {
                                AdaptiveTextButton(onClick = { confirmPriorityReset = true }) {
                                    AdaptiveText(stringResource(R.string.priority_restore_inheritance))
                                }
                            }
                        }
                        if (sortHelpExpanded) AdaptiveText(
                            stringResource(R.string.priority_inheritance_help),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (deepLinkHost != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AdaptiveText(
                                when {
                                    inheritsDeepLinkPriority -> stringResource(R.string.priority_browser_source_inherited)
                                    hasExplicitDeepLinkPriority -> stringResource(R.string.priority_source_dedicated, deepLinkHost)
                                    else -> stringResource(R.string.priority_source_none)
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (inheritsDeepLinkPriority) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasExplicitDeepLinkPriority) {
                                AdaptiveTextButton(onClick = { confirmPriorityReset = true }) {
                                    AdaptiveText(stringResource(R.string.priority_restore_inheritance))
                                }
                            }
                        }
                        if (sortHelpExpanded) AdaptiveText(
                            stringResource(R.string.priority_browser_inheritance_help),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (sortHelpExpanded) AdaptiveText(
                            stringResource(R.string.priority_general_help),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val compatibility = when {
                        !state.module.connected -> stringResource(R.string.priority_compat_disconnected)
                        state.module.detection.hosts.isEmpty() -> stringResource(R.string.priority_compat_unknown_host)
                        state.module.detection.hosts.any {
                            it.packageName != "system" &&
                                !it.className.startsWith("com.android.internal.app.") &&
                                !it.className.startsWith("com.android.intentresolver.")
                        } -> stringResource(R.string.priority_compat_vendor_path)
                        else -> stringResource(R.string.priority_compat_aosp)
                    }
                    if (sortHelpExpanded || !state.runtime.ready) AdaptiveText(
                        if (!state.runtime.ready) state.runtime.message
                        else if (kind == IntentKind.PROCESS_TEXT) stringResource(R.string.priority_process_text_note)
                        else compatibility,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (stagedOrder.size >= 200) {
                        AdaptiveText(
                            stringResource(R.string.priority_limit_reached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.error != null) {
                        AdaptiveText(stringResource(R.string.rules_refresh_incomplete), color = MaterialTheme.colorScheme.error)
                    }
                    }
                }
            }

            groups.forEachIndexed { index, group ->
                if (index == 0 || (groups[index - 1].rank == null) != (group.rank == null)) {
                    item(key = "priority-section-${if (group.rank == null) "available" else "pinned"}") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.groupHorizontal)) {
                            PageSectionTitle(
                                stringResource(
                                    if (group.rank != null) R.string.priority_selected
                                    else R.string.priority_unselected
                                )
                            )
                        }
                    }
                }
                val packageName = group.packageName
                val key = "${kind.name}|${openPreset?.name ?: browserHost ?: "ALL"}|$packageName"
                val expanded = expandedKey == key
                val onExpand = { expandedKey = if (expanded) null else key }
                val first = group.components.first()
                item(key = "app|$key", contentType = "app") {
                    val marker = MaterialTheme.colorScheme.primary
                    val lockState = vm.bulkLockState(lockScope, packageName, emptyList())
                    val rankText = group.rank?.let {
                        stringResource(
                            if (inheritsOpenPriority || inheritsDeepLinkPriority)
                                R.string.priority_rank_inherited else R.string.priority_rank,
                            it
                        )
                    } ?: stringResource(R.string.priority_not_prioritized)
                    WorkspaceAppRow(
                        icon = first.appIcon,
                        title = first.appLabel,
                        packageName = packageName,
                        subtitle = stringResource(
                            R.string.priority_app_summary, rankText, group.components.size
                        ),
                        expanded = expanded,
                        lockState = lockState,
                        onExpand = onExpand,
                        onLock = {
                            vm.setBulkAppLocked(lockScope, packageName, emptyList(), true)
                        },
                        onUnlock = {
                            vm.setBulkAppLocked(lockScope, packageName, emptyList(), false)
                        },
                        modifier = Modifier
                            .alpha(if (drag?.packageName == packageName) 0.3f else 1f)
                            .drawWithContent {
                                drawContent()
                                if (drag?.target == packageName && drag.packageName != packageName) {
                                    val y = if (drag.movingDown) size.height - 2.dp.toPx() else 2.dp.toPx()
                                    drawLine(marker, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
                                }
                            },
                        selection = {
                            WorkspaceAppSelectionCheckbox(
                                state = if (packageName in stagedOrder) ToggleableState.On
                                    else ToggleableState.Off,
                                enabled = packageName in stagedOrder || stagedOrder.size < 200,
                                onClick = {
                                    val checked = packageName !in stagedOrder
                                    if (!vm.isBulkAppLocked(lockScope, packageName)) {
                                        val current = orderDraft.effective(rankedRaw)
                                        val next = if (checked) (current + packageName).distinct().take(200)
                                            else current - packageName
                                        val warnCount = if (checked && packageName !in current &&
                                            packageName in next && group.appType == AppType.SYSTEM) 1 else 0
                                        confirmation.requestForSystemSelection(
                                            first.appLabel, warnCount, SystemSelectionRisk.PRIORITY
                                        ) {
                                            orderDraft.stage(rankedRaw, next)
                                        }
                                    }
                                }
                            )
                        },
                        trailing = {
                            if (packageName in stagedOrder) {
                                Box(
                                    modifier = Modifier.size(40.dp)
                                        .pointerInput(packageName, moveTargets, rankedRaw, kind, openPreset, browserHost) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { touch ->
                                                    val rowKey = "app|$key"
                                                    val row = listState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.key == rowKey }
                                                    val rowY = row?.let {
                                                        it.offset + (it.size - 40.dp.toPx()) / 2f + touch.y
                                                    }
                                                    if (rowY != null && dragState.start(
                                                            rowY, kind.name, currentVisible, currentSaved
                                                        )) {
                                                        if (UiPreferencesRuntime.current.dragHaptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                },
                                                onDrag = { change, delta ->
                                                    if (dragState.session != null) {
                                                        change.consume()
                                                        dragState.move(delta.y)
                                                    }
                                                },
                                                onDragCancel = { dragState.cancel() },
                                                onDragEnd = {
                                                    dragState.finish()?.let { finished ->
                                                        val next = moveVisiblePriorityTo(
                                                            orderDraft.effective(rankedRaw),
                                                            finished.visible, finished.packageName, finished.target
                                                        )
                                                        orderDraft.stage(rankedRaw, next)
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AdaptiveIcon(
                                        Icons.Rounded.DragHandle,
                                        stringResource(R.string.priority_drag_help),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    )
                }
                if (expanded) {
                    if (packageName in stagedOrder) {
                        item(key = "order|$key") {
                            val index = moveTargets.indexOf(packageName)
                            Row(
                                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AdaptiveText(
                                    stringResource(
                                        if (inheritsOpenPriority) R.string.priority_rank_inherited_short else R.string.priority_rank,
                                        group.rank ?: (stagedOrder.indexOf(packageName) + 1)
                                    ),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                AdaptiveTextButton(
                                    onClick = {
                                        orderDraft.stage(
                                            rankedRaw, moveVisiblePriority(stagedOrder, moveTargets, packageName, -1)
                                        )
                                    },
                                    enabled = index > 0
                                ) {
                                    AdaptiveIcon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp))
                                    AdaptiveText(stringResource(R.string.priority_move_up))
                                }
                                AdaptiveTextButton(
                                    onClick = {
                                        orderDraft.stage(
                                            rankedRaw, moveVisiblePriority(stagedOrder, moveTargets, packageName, 1)
                                        )
                                    },
                                    enabled = index >= 0 && index < moveTargets.lastIndex
                                ) {
                                    AdaptiveIcon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp))
                                    AdaptiveText(stringResource(R.string.priority_move_down))
                                }
                            }
                        }
                    }
                    itemsIndexed(group.components, key = { _, item -> "component|${item.rule.id}" }, contentType = { _, _ -> "component" }) { componentIndex, item ->
                        ComponentInfoRow(
                            item = item,
                            customTitle = state.priorities.titles[item.rule.id],
                            position = if (componentIndex == group.components.lastIndex)
                                ListRowPosition.LAST else ListRowPosition.MIDDLE,
                            onEditTitle = { editingTitle = item },
                            onPreview = {
                                systemProbeRequest = SystemProbeRequest(
                                    entryKind = item.rule.kind,
                                    userId = vm.selectedSpace.value,
                                    focusPackage = item.rule.packageName,
                                    focusClass = item.rule.className
                                )
                            }
                        )
                    }
                }
            }

            if (!state.loading && groups.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        AdaptiveText(
                            stringResource(
                                when {
                                    state.query.isNotBlank() -> R.string.priority_empty_search
                                    viewFilter == UiFilter.SHOW_SELECTED -> R.string.priority_empty_selected
                                    viewFilter == UiFilter.HIDE_SELECTED -> R.string.priority_empty_unselected
                                    viewFilter == UiFilter.LOCKED -> R.string.no_matching_components
                                    else -> R.string.priority_empty_category
                                }
                            )
                        )
                    }
                }
            }
        }

        drag?.let { moving ->
            groups.firstOrNull { it.packageName == moving.packageName }?.let { group ->
                val first = group.components.first()
                AdaptiveSurface(
                    Modifier.fillMaxWidth().offset { IntOffset(0, moving.top.roundToInt()) }.zIndex(1f),
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        Modifier.heightIn(min = MaterialUiTokens.listRowHeight).padding(
                            horizontal = MaterialUiTokens.pageHorizontal,
                            vertical = MaterialUiTokens.contentGap
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(first.appIcon, first.appLabel)
                        Column(Modifier.weight(1f).padding(start = MaterialUiTokens.appLabelInset)) {
                            AdaptiveText(first.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            AdaptiveText(
                                stringResource(
                                    R.string.priority_drag_target,
                                    groups.firstOrNull { it.packageName == moving.target }?.rank?.toString() ?: "?"
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        PendingChangesBar(
            count = if (orderDraft.hasChanges()) 1 else 0,
            onDiscard = orderDraft::discard,
            onApply = { applyOrder() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ComponentInfoRow(
    item: ComponentCandidate,
    customTitle: String?,
    position: ListRowPosition,
    onEditTitle: () -> Unit,
    onPreview: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    WorkspaceReadOnlyDetailRow(position = position) {
        Column(Modifier.weight(1f)) {
            WorkspaceDetailHeading(
                item.activityLabel,
                stringResource(item.rule.kind.titleRes())
            )
            if (!customTitle.isNullOrBlank()) {
                WorkspaceDetailCaption(
                    stringResource(R.string.component_shown_as, customTitle),
                    emphasized = true
                )
            }
            WorkspaceDetailCaption(item.rule.className)
        }
        WorkspaceDetailActionsMenu(
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it },
            onPreview = onPreview,
            locked = false,
            lockToggleEnabled = false,
            onLock = {},
            onUnlock = {},
            onEdit = onEditTitle.takeIf { item.rule.kind.supportsTitleEditing() }
        )
    }
}
