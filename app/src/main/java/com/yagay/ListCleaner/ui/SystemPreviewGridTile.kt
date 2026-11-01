package com.yagay.ListCleaner.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AndroidUid
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview v2 presentation only. It never queries, merges or changes resolver results. */
private val systemPreviewGridIconCache = LruCache<String, Bitmap>(160)

@Composable
internal fun SystemPreviewGridTile(
    item: SystemProbeItem,
    focused: Boolean,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cacheKey = item.displayIdentity
    val icon by produceState<Bitmap?>(
        initialValue = systemPreviewGridIconCache.get(cacheKey),
        key1 = cacheKey,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                systemPreviewGridIconCache.get(cacheKey) ?: runCatching {
                    val pm = context.packageManager
                    val drawable = runCatching {
                        pm.getActivityIcon(ComponentName(item.packageName, item.className))
                    }.getOrNull() ?: pm.getApplicationIcon(item.packageName)
                    val drawableForUser = item.userId?.takeIf {
                        it != AndroidUid.userId(Process.myUid())
                    }?.let { user ->
                        val profile = runCatching {
                            context.getSystemService(UserManager::class.java)
                                ?.userProfiles.orEmpty().firstOrNull { handle ->
                                    val method = handle.javaClass.getMethod("getIdentifier")
                                    (method.invoke(handle) as? Int) == user
                                }
                        }.getOrNull()
                        profile?.let { runCatching { pm.getUserBadgedIcon(drawable, it) }
                            .getOrNull() }
                    } ?: drawable
                    drawableForUser.toBitmap(width = 88, height = 88)
                }.getOrNull()?.also { systemPreviewGridIconCache.put(cacheKey, it) }
            }
        }
    }
    Column(
        modifier = modifier
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onInspect)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            item.appLabel,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.userId != null && item.userId != AndroidUid.userId(Process.myUid())) {
            Text(
                androidx.compose.ui.res.stringResource(R.string.preview_v4_nfc_profile_label, item.userId),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        if (item.label.isNotBlank() && item.label != item.appLabel) {
            Text(
                item.label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
