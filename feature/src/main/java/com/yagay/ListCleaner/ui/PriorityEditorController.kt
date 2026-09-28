package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.moveVisiblePriority
import com.yagay.ListCleaner.domain.moveVisiblePriorityTo
import com.yagay.ListCleaner.domain.normalizeBrowserHost

internal class PriorityEditorController(
    private val rules: RuleRepository,
    private val bulkLocks: BulkLockStore,
    private val canEdit: () -> Boolean,
    private val ensureBrowserHostConfigured: (String) -> String?,
) {
    private fun deepLinkHostPriorityBase(host: String): List<String> {
        val normalized = normalizeBrowserHost(host) ?: return emptyList()
        val explicit = rules.browserLinks.value.priorities[normalized].orEmpty()
        return if (explicit.isNotEmpty()) explicit else rules.priorities.value.apps[IntentKind.DEEP_LINK].orEmpty()
    }

    private fun openTypePriorityBase(preset: OpenPreset): List<String> {
        val explicit = rules.openTypes.value.priorities[preset].orEmpty()
        return if (explicit.isNotEmpty()) explicit else rules.priorities.value.apps[IntentKind.OPEN].orEmpty()
    }

    fun selectApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val current = rules.priorities.value.apps[kind].orEmpty()
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        val next = (current + editable.filter { it !in current }).take(MAX_PRIORITY_APPS)
        if (next != current) rules.setPriority(kind, next)
    }

    fun deselectApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }.toSet()
        if (editable.isEmpty()) return
        val current = rules.priorities.value.apps[kind].orEmpty()
        val next = current.filterNot { it in editable }
        if (next != current) rules.setPriority(kind, next)
    }

    fun invertApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val visible = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        if (visible.isEmpty()) return
        val current = rules.priorities.value.apps[kind].orEmpty()
        val next = (current.filterNot { it in visible.toSet() } + visible.filter { it !in current }).take(MAX_PRIORITY_APPS)
        if (next != current) rules.setPriority(kind, next)
    }

    fun pin(kind: IntentKind, packageName: String) {
        if (!canEdit()) return
        val current = rules.priorities.value.apps[kind].orEmpty()
        if (packageName !in current && current.size < MAX_PRIORITY_APPS) rules.setPriority(kind, current + packageName)
    }

    fun remove(kind: IntentKind, packageName: String) {
        if (canEdit()) rules.setPriority(kind, rules.priorities.value.apps[kind].orEmpty() - packageName)
    }

    fun move(kind: IntentKind, packageName: String, offset: Int, visible: List<String>) {
        if (!canEdit()) return
        rules.setPriority(kind, moveVisiblePriority(rules.priorities.value.apps[kind].orEmpty(), visible, packageName, offset))
    }

    fun moveTo(kind: IntentKind, packageName: String, target: String, visible: List<String>, expected: List<String>) {
        if (!canEdit()) return
        val current = rules.priorities.value.apps[kind].orEmpty()
        if (current != expected) return
        val updated = moveVisiblePriorityTo(current, visible, packageName, target)
        if (updated != current) rules.setPriority(kind, updated)
    }

    fun selectBrowserHost(host: String, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        val next = (current + editable.filter { it !in current }).take(MAX_PRIORITY_APPS)
        if (next != current) ensureBrowserHostConfigured(host)?.let { rules.setBrowserHostPriority(it, next) }
    }

    fun deselectBrowserHost(host: String, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }.toSet()
        if (editable.isEmpty()) return
        val current = deepLinkHostPriorityBase(host)
        val next = current.filterNot { it in editable }
        if (next != current) ensureBrowserHostConfigured(host)?.let { rules.setBrowserHostPriority(it, next) }
    }

    fun invertBrowserHost(host: String, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val visible = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        if (visible.isEmpty()) return
        val current = deepLinkHostPriorityBase(host)
        val next = (current.filterNot { it in visible.toSet() } + visible.filter { it !in current }).take(MAX_PRIORITY_APPS)
        if (next != current) ensureBrowserHostConfigured(host)?.let { rules.setBrowserHostPriority(it, next) }
    }

    fun pinBrowserHost(host: String, packageName: String) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        if (packageName !in current && current.size < MAX_PRIORITY_APPS) {
            ensureBrowserHostConfigured(host)?.let { rules.setBrowserHostPriority(it, current + packageName) }
        }
    }

    fun removeBrowserHost(host: String, packageName: String) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        val next = current - packageName
        if (next != current) ensureBrowserHostConfigured(host)?.let { rules.setBrowserHostPriority(it, next) }
    }

    fun moveBrowserHost(host: String, packageName: String, offset: Int, visible: List<String>) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        val updated = moveVisiblePriority(current, visible, packageName, offset)
        if (updated != current) ensureBrowserHostConfigured(host)?.let { rules.setBrowserHostPriority(it, updated) }
    }

    fun moveBrowserHostTo(host: String, packageName: String, target: String, visible: List<String>, expected: List<String>) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        if (current != expected) return
        val updated = moveVisiblePriorityTo(current, visible, packageName, target)
        if (updated != current) ensureBrowserHostConfigured(host)?.let { rules.setBrowserHostPriority(it, updated) }
    }

    fun resetBrowserHost(host: String) {
        if (!canEdit()) return
        val normalized = normalizeBrowserHost(host) ?: return
        if (normalized !in rules.browserLinks.value.hosts) return
        rules.setBrowserHostPriority(normalized, emptyList())
    }

    fun selectOpenType(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        val next = (current + editable.filter { it !in current }).take(MAX_PRIORITY_APPS)
        if (next != current) rules.setOpenTypePriority(preset, next)
    }

    fun deselectOpenType(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }.toSet()
        if (editable.isEmpty()) return
        val current = openTypePriorityBase(preset)
        val next = current.filterNot { it in editable }
        if (next != current) rules.setOpenTypePriority(preset, next)
    }

    fun invertOpenType(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val visible = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        if (visible.isEmpty()) return
        val current = openTypePriorityBase(preset)
        val next = (current.filterNot { it in visible.toSet() } + visible.filter { it !in current }).take(MAX_PRIORITY_APPS)
        if (next != current) rules.setOpenTypePriority(preset, next)
    }

    fun pinOpenType(preset: OpenPreset, packageName: String) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        if (packageName !in current && current.size < MAX_PRIORITY_APPS) rules.setOpenTypePriority(preset, current + packageName)
    }

    fun removeOpenType(preset: OpenPreset, packageName: String) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        val next = current - packageName
        if (next != current) rules.setOpenTypePriority(preset, next)
    }

    fun moveOpenType(preset: OpenPreset, packageName: String, offset: Int, visible: List<String>) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        val updated = moveVisiblePriority(current, visible, packageName, offset)
        if (updated != current) rules.setOpenTypePriority(preset, updated)
    }

    fun moveOpenTypeTo(preset: OpenPreset, packageName: String, target: String, visible: List<String>, expected: List<String>) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        if (current != expected) return
        val updated = moveVisiblePriorityTo(current, visible, packageName, target)
        if (updated != current) rules.setOpenTypePriority(preset, updated)
    }

    fun resetOpenType(preset: OpenPreset) {
        if (canEdit()) rules.setOpenTypePriority(preset, emptyList())
    }

    private companion object {
        const val MAX_PRIORITY_APPS = 200
    }
}
