package com.yagay.ListCleaner.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** A gesture holds a snapshot; no preferences are written until a valid drop. */
internal data class PriorityDragSession(
    val packageName: String,
    val target: String,
    val saved: List<String>,
    val visible: List<String>,
    val keys: Map<String, String>,
    val pointerY: Float,
    val grabOffset: Float,
    val height: Int
) {
    val top: Float get() = pointerY - grabOffset
    val movingDown: Boolean get() = visible.indexOf(target) > visible.indexOf(packageName)
}

internal class PriorityDragState(private val list: LazyListState) {
    var session by mutableStateOf<PriorityDragSession?>(null)
        private set

    fun start(y: Float, kind: String, visible: List<String>, saved: List<String>): Boolean {
        val visibleSet = visible.toSet()
        val layout = list.layoutInfo
        val top = contentTop()
        if (y < top || y >= layout.viewportEndOffset) return false
        val row = layout.visibleItemsInfo.firstOrNull { info ->
            val key = info.key as? String ?: return@firstOrNull false
            if (!key.startsWith("app|$kind|") || y < info.offset || y >= info.offset + info.size) {
                return@firstOrNull false
            }
            key.substringAfterLast('|') in visibleSet
        } ?: return false
        val key = row.key as String
        val pkg = key.substringAfterLast('|')
        val keys = layout.visibleItemsInfo.mapNotNull { info ->
            val rowKey = info.key as? String ?: return@mapNotNull null
            if (!rowKey.startsWith("app|$kind|")) return@mapNotNull null
            val candidate = rowKey.substringAfterLast('|')
            if (candidate !in visibleSet) return@mapNotNull null
            rowKey to candidate
        }.toMap()
        session = PriorityDragSession(
            pkg,
            pkg,
            saved.toList(),
            visible.toList(),
            keys,
            y,
            y - row.offset,
            row.size,
        )
        return true
    }

    fun move(delta: Float) {
        session = session?.let { it.copy(pointerY = it.pointerY + delta) }
        retarget()
    }

    fun retarget() {
        val drag = session ?: return
        val layout = list.layoutInfo
        val top = contentTop()
        val center = drag.top + drag.height / 2f
        val target = layout.visibleItemsInfo.filter {
            it.key in drag.keys && it.offset + it.size > top && it.offset < layout.viewportEndOffset
        }.minByOrNull { abs(it.offset + it.size / 2f - center) } ?: return
        val pkg = drag.keys.getValue(target.key as String)
        if (pkg != drag.target) session = drag.copy(target = pkg)
    }

    /** Account for the pinned filter bar rather than scrolling beneath it. */
    private fun contentTop(): Int {
        val layout = list.layoutInfo
        val header = layout.visibleItemsInfo.firstOrNull { it.key == "controls" }
        return maxOf(layout.viewportStartOffset, header?.let { it.offset + it.size } ?: 0)
    }

    fun scrollSpeed(edge: Float, maximum: Float): Float {
        val drag = session ?: return 0f
        val bottom = list.layoutInfo.viewportEndOffset
        val top = contentTop()
        return when {
            drag.pointerY < top + edge -> -maximum * ((top + edge - drag.pointerY) / edge).coerceIn(0f, 1f)
            drag.pointerY > bottom - edge -> maximum * ((drag.pointerY - bottom + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    fun finish(): PriorityDragSession? = session.also { session = null }
    fun cancel() { session = null }
}
