package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable

/**
 * Two Hook-backed Entry categories with one Entry interaction contract.
 * The Settings Apps and Notification source/rule providers remain separate in
 * EntryManagedSurfaceEditor; neither uses Root pm component mutations.
 */
@Composable
internal fun SettingsAppsEntryScreen(
    state: MainState,
    vm: MainViewModel,
    pendingNavigation: PendingNavigationGuard?,
    refreshRegistry: ListsRefreshRegistry?,
    onEntry: () -> Unit,
    onManagedSurface: (ManagedComponentSurface) -> Unit,
    onOpenRoot: () -> Unit,
) = EntryManagedSurfaceEditor(
    state, vm, ManagedComponentSurface.SETTINGS_APPS,
    pendingNavigation, refreshRegistry,
    onEntry = onEntry, onManagedSurface = onManagedSurface, onOpenRoot = onOpenRoot,
)

@Composable
internal fun NotificationCleanupEntryScreen(
    state: MainState,
    vm: MainViewModel,
    pendingNavigation: PendingNavigationGuard?,
    refreshRegistry: ListsRefreshRegistry?,
    onEntry: () -> Unit,
    onManagedSurface: (ManagedComponentSurface) -> Unit,
    onOpenRoot: () -> Unit,
) = EntryManagedSurfaceEditor(
    state, vm, ManagedComponentSurface.NOTIFICATIONS,
    pendingNavigation, refreshRegistry,
    onEntry = onEntry, onManagedSurface = onManagedSurface, onOpenRoot = onOpenRoot,
)
