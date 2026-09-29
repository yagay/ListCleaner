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
    private class PriorityTarget(
        val read: () -> List<String>,
        val write: (List<String>) -> Unit,
    )

    private fun kindTarget(kind: IntentKind) = PriorityTarget(
        read = { rules.priorities.value.apps[kind].orEmpty() },
        write = { rules.setPriority(kind, it) },
    )

    private fun browserTarget(host: String): PriorityTarget? {
        val normalized = normalizeBrowserHost(host) ?: return null
        return PriorityTarget(
            read = {
                rules.browserLinks.value.priorities[normalized]
                    ?.takeIf { it.isNotEmpty() }
                    ?: rules.priorities.value.apps[IntentKind.DEEP_LINK].orEmpty()
            },
            write = { packages ->
                ensureBrowserHostConfigured(normalized)?.let { rules.setBrowserHostPriority(it, packages) }
            },
        )
    }

    private fun openTarget(preset: OpenPreset) = PriorityTarget(
        read = {
            rules.openTypes.value.priorities[preset]
                ?.takeIf { it.isNotEmpty() }
                ?: rules.priorities.value.apps[IntentKind.OPEN].orEmpty()
        },
        write = { rules.setOpenTypePriority(preset, it) },
    )

    private fun editablePackages(packageNames: Collection<String>, lockScope: String): List<String> =
        packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }

    private fun select(target: PriorityTarget?, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit() || target == null) return
        val current = target.read()
        val editable = editablePackages(packageNames, lockScope)
        val next = (current + editable.filter { it !in current }).take(MAX_PRIORITY_APPS)
        if (next != current) target.write(next)
    }

    private fun deselect(target: PriorityTarget?, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit() || target == null) return
        val editable = editablePackages(packageNames, lockScope).toSet()
        if (editable.isEmpty()) return
        val current = target.read()
        val next = current.filterNot { it in editable }
        if (next != current) target.write(next)
    }

    private fun invert(target: PriorityTarget?, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit() || target == null) return
        val visible = editablePackages(packageNames, lockScope)
        if (visible.isEmpty()) return
        val current = target.read()
        val visibleSet = visible.toSet()
        val next = (current.filterNot { it in visibleSet } + visible.filter { it !in current })
            .take(MAX_PRIORITY_APPS)
        if (next != current) target.write(next)
    }

    private fun pin(target: PriorityTarget?, packageName: String) {
        if (!canEdit() || target == null) return
        val current = target.read()
        if (packageName !in current && current.size < MAX_PRIORITY_APPS) target.write(current + packageName)
    }

    private fun remove(target: PriorityTarget?, packageName: String) {
        if (!canEdit() || target == null) return
        val current = target.read()
        val next = current - packageName
        if (next != current) target.write(next)
    }

    private fun move(
        target: PriorityTarget?,
        packageName: String,
        offset: Int,
        visible: List<String>,
    ) {
        if (!canEdit() || target == null) return
        val current = target.read()
        val updated = moveVisiblePriority(current, visible, packageName, offset)
        if (updated != current) target.write(updated)
    }

    private fun moveTo(
        target: PriorityTarget?,
        packageName: String,
        destination: String,
        visible: List<String>,
        expected: List<String>,
    ) {
        if (!canEdit() || target == null) return
        val current = target.read()
        if (current != expected) return
        val updated = moveVisiblePriorityTo(current, visible, packageName, destination)
        if (updated != current) target.write(updated)
    }

    private fun reset(target: PriorityTarget?) {
        if (canEdit() && target != null) target.write(emptyList())
    }

    fun selectApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) =
        select(kindTarget(kind), packageNames, lockScope)

    fun deselectApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) =
        deselect(kindTarget(kind), packageNames, lockScope)

    fun invertApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) =
        invert(kindTarget(kind), packageNames, lockScope)

    fun pin(kind: IntentKind, packageName: String) = pin(kindTarget(kind), packageName)
    fun remove(kind: IntentKind, packageName: String) = remove(kindTarget(kind), packageName)
    fun move(kind: IntentKind, packageName: String, offset: Int, visible: List<String>) =
        move(kindTarget(kind), packageName, offset, visible)

    fun moveTo(kind: IntentKind, packageName: String, target: String, visible: List<String>, expected: List<String>) =
        moveTo(kindTarget(kind), packageName, target, visible, expected)

    fun selectBrowserHost(host: String, packageNames: Collection<String>, lockScope: String) =
        select(browserTarget(host), packageNames, lockScope)

    fun deselectBrowserHost(host: String, packageNames: Collection<String>, lockScope: String) =
        deselect(browserTarget(host), packageNames, lockScope)

    fun invertBrowserHost(host: String, packageNames: Collection<String>, lockScope: String) =
        invert(browserTarget(host), packageNames, lockScope)

    fun pinBrowserHost(host: String, packageName: String) = pin(browserTarget(host), packageName)
    fun removeBrowserHost(host: String, packageName: String) = remove(browserTarget(host), packageName)
    fun moveBrowserHost(host: String, packageName: String, offset: Int, visible: List<String>) =
        move(browserTarget(host), packageName, offset, visible)

    fun moveBrowserHostTo(
        host: String,
        packageName: String,
        target: String,
        visible: List<String>,
        expected: List<String>,
    ) = moveTo(browserTarget(host), packageName, target, visible, expected)

    fun resetBrowserHost(host: String) {
        if (!canEdit()) return
        val normalized = normalizeBrowserHost(host) ?: return
        if (normalized !in rules.browserLinks.value.hosts) return
        rules.setBrowserHostPriority(normalized, emptyList())
    }

    fun selectOpenType(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) =
        select(openTarget(preset), packageNames, lockScope)

    fun deselectOpenType(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) =
        deselect(openTarget(preset), packageNames, lockScope)

    fun invertOpenType(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) =
        invert(openTarget(preset), packageNames, lockScope)

    fun pinOpenType(preset: OpenPreset, packageName: String) = pin(openTarget(preset), packageName)
    fun removeOpenType(preset: OpenPreset, packageName: String) = remove(openTarget(preset), packageName)
    fun moveOpenType(preset: OpenPreset, packageName: String, offset: Int, visible: List<String>) =
        move(openTarget(preset), packageName, offset, visible)

    fun moveOpenTypeTo(
        preset: OpenPreset,
        packageName: String,
        target: String,
        visible: List<String>,
        expected: List<String>,
    ) = moveTo(openTarget(preset), packageName, target, visible, expected)

    fun resetOpenType(preset: OpenPreset) = reset(openTarget(preset))

    private companion object {
        const val MAX_PRIORITY_APPS = 200
    }
}
