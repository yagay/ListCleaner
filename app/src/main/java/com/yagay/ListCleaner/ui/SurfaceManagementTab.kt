package com.yagay.ListCleaner.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R

/** Actual cleanup workflows, kept separate from application appearance/configuration settings. */
internal enum class SurfaceManagerGroup(
    @StringRes val title: Int, @StringRes val description: Int, val icon: ImageVector,
) {
    MAINTENANCE(R.string.surface_group_maintenance, R.string.surface_group_maintenance_help,
        Icons.Rounded.CleaningServices),
}

internal enum class SurfaceManagerPage(
    val group: SurfaceManagerGroup,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val kind: SurfaceKind? = null,
) {
    INVALID_RULES(SurfaceManagerGroup.MAINTENANCE, R.string.surface_rules,
        R.string.surface_rules_help, Icons.Rounded.Description),
}

/**
 * Lists V2: one selection screen and one shared editor host.
 * A single tap opens each AOSP/notification/maintenance editor.
 * Backend implementations remain separate.
 */
@Composable
internal fun SurfaceManagementTab(
    state: MainState,
    vm: MainViewModel,
    pendingNavigation: PendingNavigationGuard? = null,
    onSearchStateChanged: ((SurfaceWorkspaceUiState?) -> Unit)? = null,
    refreshRegistry: ListsRefreshRegistry? = null,
) {
    var page by rememberSaveable { mutableStateOf<SurfaceManagerPage?>(null) }
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val editorStateHolder = rememberSaveableStateHolder()
    val navigateBack: () -> Unit = {
        val next = { page = null }
        if (pendingNavigation != null) pendingNavigation.request(next) else next()
    }

    val current = page
    if (current == null) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            SurfaceManagerGroup.entries.forEach { section ->
                item(key = "group-${section.name}") {
                    FlatSectionHeader(
                        stringResource(section.title),
                        modifier = Modifier.padding(horizontal = MaterialUiTokens.pageHorizontal),
                    )
                }
                SurfaceManagerPage.entries.filter { it.group == section }.forEach { option ->
                    item(key = "page-${option.name}") {
                        FlatCategoryRow(
                            icon = option.icon,
                            title = stringResource(option.title),
                            description = stringResource(option.description),
                            onClick = { page = option },
                        )
                    }
                }
            }
        }
        return
    }

    BackHandler(onBack = navigateBack)
    Column(Modifier.fillMaxSize()) {
        SurfaceNavigationHeading(
            title = stringResource(current.title),
            onBack = navigateBack,
        )
        Box(Modifier.weight(1f)) {
            editorStateHolder.SaveableStateProvider("lists-v2-${userId}-${current.name}") {
                when (current) {
                    SurfaceManagerPage.INVALID_RULES ->
                        StaleRuleManagement(state, vm, pendingNavigation, onSearchStateChanged, refreshRegistry)
                }
            }
        }
    }
}

@Composable
private fun SurfaceNavigationHeading(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = MaterialUiTokens.pageHorizontal
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.sectionGap),
    ) {
        AdaptiveBackButton(onClick = onBack)
        AdaptiveText(title, style = MaterialTheme.typography.titleMedium)
    }
}
