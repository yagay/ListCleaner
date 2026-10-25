package com.yagay.ListCleaner.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.VisibilityScope
import com.yagay.ListCleaner.domain.AppType
import com.yagay.ListCleaner.domain.listCleanerAppType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScopeAppEntry(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val normalizedLabel: String = label.lowercase(),
    val normalizedPackage: String = packageName.lowercase(),
)

private object ScopeIconCache {
    val icons = LruCache<String, Bitmap>(128)
}

@Suppress("DEPRECATION")
private fun loadScopeApps(pm: PackageManager, selfPackage: String): List<ScopeAppEntry> {
    val installed = if (android.os.Build.VERSION.SDK_INT >= 33) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        pm.getInstalledApplications(0)
    }
    return installed.asSequence()
        .filter { it.packageName != selfPackage && it.packageName != "android" }
        .map { info ->
            ScopeAppEntry(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                system = info.listCleanerAppType() == AppType.SYSTEM,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<ScopeAppEntry> { it.normalizedLabel }.thenBy { it.packageName })
        .toList()
}

@Composable
private fun scopeAppIcon(packageName: String): Bitmap? {
    val context = LocalContext.current
    val cached = remember(packageName) { ScopeIconCache.icons.get(packageName) }
    val icon by produceState<Bitmap?>(initialValue = cached, packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(64, 64)
                }.getOrNull()?.also { ScopeIconCache.icons.put(packageName, it) }
            }
        }
    }
    return icon
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScopePickerDialog(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ListCleanerApp
    val visibilityScopes by app.rules.visibilityScopes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<ScopeAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadScopeApps(context.packageManager, context.packageName) }
        loading = false
    }

    val visible = remember(apps, query, showSystem, selected) {
        val needle = query.trim().lowercase()
        apps.filter { entry ->
            (showSystem || !entry.system) &&
                (needle.isEmpty() || entry.normalizedLabel.contains(needle) || entry.normalizedPackage.contains(needle))
        }.sortedWith(
            compareBy<ScopeAppEntry> { if (it.packageName in selected) 0 else 1 }
                .thenBy { it.normalizedLabel }
                .thenBy { it.packageName }
        )
    }

    MaterialFullScreenDialog(
        onDismissRequest = dismiss,
        title = { AdaptiveText(stringResource(R.string.visibility_title)) }
    ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = MaterialUiTokens.pageHorizontal)) {
                AdaptiveText(
                    stringResource(R.string.visibility_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AdaptiveText(stringResource(R.string.visibility_match_categories), style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    contentPadding = PaddingValues(vertical = MaterialUiTokens.detailVertical),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(VisibilityScope.entries, key = { it.name }) { scope ->
                        val checked = scope in visibilityScopes
                        AdaptiveFilterChip(
                            selected = checked,
                            onClick = {
                                app.rules.setVisibilityScopes(
                                    if (checked) visibilityScopes - scope else visibilityScopes + scope
                                )
                            },
                            label = { AdaptiveText(stringResource(scope.titleRes())) },
                        )
                    }
                }
                AdaptiveText(
                    if (visibilityScopes.isEmpty()) {
                        stringResource(R.string.visibility_no_categories)
                    } else {
                        stringResource(R.string.visibility_active_targets_plain)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AdaptiveOutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { AdaptiveText(stringResource(R.string.visibility_search_sources)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AdaptiveText(stringResource(R.string.visibility_show_system), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    AdaptiveSwitch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
                Spacer(Modifier.height(6.dp))
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AdaptiveCircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.packageName }) { entry ->
                            val checked = entry.packageName in selected
                            val bitmap = scopeAppIcon(entry.packageName)
                            FlatRowSurface(Modifier.fillMaxWidth()) {
                                MaterialAppRowBody(
                                    icon = bitmap,
                                    title = entry.label,
                                    subtitle = entry.packageName,
                                    modifier = Modifier.toggleable(
                                        value = checked,
                                        role = Role.Checkbox,
                                        onValueChange = { isChecked ->
                                            onSelectedChange(
                                                if (isChecked) selected + entry.packageName
                                                else selected - entry.packageName
                                            )
                                        }
                                    ),
                                    selection = {
                                        AdaptiveCheckbox(checked = checked, onCheckedChange = null)
                                    },
                                    trailing = {
                                        if (checked) AdaptiveText(
                                            stringResource(R.string.visibility_added),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
    }
}
