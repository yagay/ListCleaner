package com.yagay.ListCleaner.runtime

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Explicit, user-authorized notification dismissal. Does NOT claim to hide notifications
 * while keeping them posted. Stores package/channel metadata only, never message text.
 */
data class ActiveNotificationRow(
    val key: String,
    val packageName: String,
    val channelId: String,
    val userId: Int,
    val protected: Boolean,
)

object NotificationCleanupController {
    private val mutableRows = MutableStateFlow<List<ActiveNotificationRow>>(emptyList())
    val rows: StateFlow<List<ActiveNotificationRow>> = mutableRows.asStateFlow()
    private val mutableConnected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
    @Volatile private var listener: ListCleanerNotificationListener? = null

    internal fun attach(value: ListCleanerNotificationListener) {
        listener = value
        mutableConnected.value = true
        refresh(value)
    }

    internal fun detach(value: ListCleanerNotificationListener) {
        if (listener !== value) return
        listener = null
        mutableConnected.value = false
        mutableRows.value = emptyList()
    }

    internal fun refresh(value: ListCleanerNotificationListener) {
        if (listener !== value) return
        val snapshot = runCatching { value.activeNotifications?.toList() }.getOrNull()
            ?: return
        mutableRows.value = snapshot.mapNotNull(::toRow).sortedWith(
            compareBy(ActiveNotificationRow::packageName, ActiveNotificationRow::channelId,
                ActiveNotificationRow::key)
        )
    }

    private fun toRow(item: StatusBarNotification): ActiveNotificationRow? {
        val notification = item.notification ?: return null
        return ActiveNotificationRow(
            key = item.key,
            packageName = item.packageName,
            channelId = notification.channelId ?: "(default)",
            userId = item.userId,
            protected = NotificationCleanupPolicy.protected(notification.flags, notification.category),
        )
    }

    /** Never dismiss protected, changed or no-longer-active notifications. */
    fun dismiss(keys: Set<String>): Int {
        val current = listener ?: return 0
        if (keys.isEmpty()) return 0
        refresh(current)
        val allowed = mutableRows.value.asSequence()
            .filter { !it.protected && it.key in keys }
            .map { it.key }.distinct().toList()
        if (allowed.isEmpty()) return 0
        val ok = runCatching { current.cancelNotifications(allowed.toTypedArray()) }.isSuccess
        if (ok) refresh(current)
        return if (ok) allowed.size else 0
    }
}

class ListCleanerNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = NotificationCleanupController.attach(this)
    override fun onListenerDisconnected() = NotificationCleanupController.detach(this)
    override fun onNotificationPosted(sbn: StatusBarNotification?) = NotificationCleanupController.refresh(this)
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = NotificationCleanupController.refresh(this)
    override fun onDestroy() {
        NotificationCleanupController.detach(this)
        super.onDestroy()
    }
}

/** Pure safety contract so preview and execution cannot classify protected rows differently. */
internal object NotificationCleanupPolicy {
    fun protected(flags: Int, category: String?): Boolean =
        category in setOf(
            Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_SERVICE
        ) || (flags and (Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR)) != 0
}
