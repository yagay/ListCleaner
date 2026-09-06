package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.Apps
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
    @Composable
    internal fun AppRow(group: AppGroup, selected: Set<com.yagay.ListCleaner.domain.ComponentRule>, expanded: Boolean, onExpand: () -> Unit, onSelect: (Boolean) -> Unit) {
        val selectedCount = group.components.count { it.rule in selected }
        val selectionState = when {
            selectedCount == 0 -> ToggleableState.Off
            selectedCount == group.components.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "折叠" else "展开", onClick = onExpand)
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TriStateCheckbox(
                state = selectionState,
                onClick = { onSelect(selectionState != ToggleableState.On) }
            )
            AppIcon(bitmap = group.appIcon, appLabel = group.appLabel)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    group.appLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "已选 $selectedCount/${group.components.size} · ${group.components.map { it.rule.kind.shortTitle }.distinct().joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val missing = group.components.count { it.unavailable || it.restricted }
                if (missing > 0) Text("含 $missing 条当前不可用规则", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onExpand) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "折叠" else "展开")
            }
        }
        HorizontalDivider()
    }

    @Composable
    internal fun ComponentRow(item: ComponentCandidate, checked: Boolean, customTitle: String?, onToggle: () -> Unit, onEditTitle: () -> Unit) {

            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { onToggle() }
                    )
                    .heightIn(min = 48.dp)
                    .padding(start = 24.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ComponentSelectionMark(checked)
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.activityLabel,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.rule.kind.shortTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!customTitle.isNullOrBlank()) Text(
                        "显示为：$customTitle",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.rule.className,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.unavailable) Text("当前未找到组件 · 可取消规则", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                    else if (item.restricted) Text("非公开组件 · 可取消已有规则", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onEditTitle) {
                    Icon(Icons.Rounded.Edit, contentDescription = "修改显示名称")
                }
            }
        }


    @Composable
    private fun ComponentSelectionMark(checked: Boolean) {
        val colors = MaterialTheme.colorScheme
        // The parent row owns the checkbox semantics and the full touch target.
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(20.dp)
                    .background(if (checked) colors.primary else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (checked) colors.primary else colors.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onPrimary)
                }
            }
        }
    }

    @Composable
    internal fun AppIcon(bitmap: Bitmap?, appLabel: String) {
        if (bitmap == null) {
            Icon(Icons.Rounded.Apps, null, Modifier.size(40.dp))
            return
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "$appLabel 图标",
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Fit
        )
    }
