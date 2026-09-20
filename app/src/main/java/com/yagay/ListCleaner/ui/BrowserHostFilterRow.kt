package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.normalizeBrowserHost

/** Compact BROWSER-domain selector shown inside the shared top filter row. */
@Composable
fun BrowserHostFilterMenu(
    selected: String?,
    config: BrowserLinkConfig,
    onSelected: (String?) -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = selected ?: stringResource(R.string.common_all)

    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            Text(
                stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.browser_domain_filter),
                    selectedTitle
                ),
                modifier = Modifier.widthIn(max = 170.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Rounded.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_all)) },
                leadingIcon = { if (selected == null) Icon(Icons.Rounded.Check, null) },
                onClick = {
                    expanded = false
                    onSelected(null)
                }
            )
            config.hosts.sorted().forEach { host ->
                DropdownMenuItem(
                    text = { Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { if (selected == host) Icon(Icons.Rounded.Check, null) },
                    onClick = {
                        expanded = false
                        onSelected(host)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_hosts_manage)) },
                onClick = {
                    expanded = false
                    onManage()
                }
            )
        }
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
                        placeholder = { Text(stringResource(R.string.browser_host_example)) },
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
