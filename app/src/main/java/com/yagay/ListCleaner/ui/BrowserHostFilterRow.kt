package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.normalizeBrowserHost

/** Secondary BROWSER host tabs using the same visual language as OPEN typed tabs. */
@Composable
fun BrowserHostFilterRow(
    selected: String?,
    config: BrowserLinkConfig,
    onSelected: (String?) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 12.dp)) {
        item("all") {
            BrowserHostTab(stringResource(R.string.common_all), selected == null) { onSelected(null) }
        }
        items(config.hosts.sorted(), key = { it }) { host ->
            BrowserHostTab(host, selected == host) { onSelected(host) }
        }
        item("manage") {
            BrowserHostTab(stringResource(R.string.browser_hosts_manage), false, onManage)
        }
    }
}

@Composable
private fun BrowserHostTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onClick) {
            Text(
                title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier.height(2.dp).width(24.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

@Composable
fun BrowserHostDialog(
    config: BrowserLinkConfig,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var hosts by remember(config.hosts) { mutableStateOf(config.hosts.sorted()) }
    var input by remember { mutableStateOf("") }
    val normalized = normalizeBrowserHost(input)
    val canAdd = normalized != null && normalized !in hosts && hosts.size < BrowserLinkConfig.MAX_HOSTS

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        title = { Text(stringResource(R.string.browser_hosts_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.browser_hosts_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.browser_host_input)) },
                        placeholder = { Text("github.com") },
                        isError = input.isNotBlank() && normalized == null
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canAdd,
                        onClick = {
                            val host = normalized ?: return@Button
                            hosts = (hosts + host).distinct().sorted()
                            input = ""
                        }
                    ) { Text(stringResource(R.string.common_add)) }
                }
                if (input.isNotBlank() && normalized == null) {
                    Text(
                        stringResource(R.string.browser_host_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (hosts.isEmpty()) {
                    Text(
                        stringResource(R.string.browser_hosts_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(hosts, key = { it }) { host ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(host, Modifier.weight(1f))
                                IconButton(onClick = { hosts = hosts - host }) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(hosts.toSet()); onDismiss() }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
