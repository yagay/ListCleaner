package com.yagay.ListCleaner.runtime

import android.app.Notification
import android.content.Context
import com.yagay.ListCleaner.data.ComponentRootCommand
import org.json.JSONArray
import org.json.JSONObject
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val category: String? = null,
)

data class RegisteredChannelRow(
    val packageName: String,
    val channelId: String,
    val label: String,
    val importance: Int,
    val groupId: String? = null,
    val groupLabel: String? = null,
)

enum class ChannelInventoryStatus { COMPLETE, OBSERVED_ONLY, DISCONNECTED }
enum class NotificationScanStatus { DISCONNECTED, CONNECTED, FAILED }
data class NotificationScanState(val status: NotificationScanStatus, val active: Int, val observed: Int)


data class ChannelInventory(
    val items: List<RegisteredChannelRow>,
    val status: ChannelInventoryStatus,
)

/** Retrieves all registered channels only where platform listener privileges permit it. */
object NotificationCleanupController {
    private val mutableRows = MutableStateFlow<List<ActiveNotificationRow>>(emptyList())
    val rows: StateFlow<List<ActiveNotificationRow>> = mutableRows.asStateFlow()
    private val mutableConnected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
    @Volatile private var listener: ListCleanerNotificationListener? = null
    private const val HISTORY_PREF = "notification_observation"
    private const val HISTORY_KEY = "channels_v1"

    @Synchronized private fun loadHistory(context: Context) {
        val encoded = context.getSharedPreferences(HISTORY_PREF, Context.MODE_PRIVATE)
            .getString(HISTORY_KEY, null) ?: return
        val array = runCatching { JSONArray(encoded) }.getOrNull() ?: return
        for (index in 0 until minOf(array.length(), 4000)) {
            val item = array.optJSONObject(index) ?: continue
            val pkg = item.optString("pkg")
            val channel = item.optString("channel")
            if (pkg.isBlank() || channel.isBlank()) continue
            val row = ActiveNotificationRow(
                key = "observed:$index", packageName = pkg,
                channelId = channel, userId = item.optInt("user", 0),
                protected = item.optBoolean("protected", false),
                category = item.optString("category").ifEmpty { null },
            )
            history.putIfAbsent(observationKey(row), row)
        }
        mutableHistory.value = history.values.toList()
    }

    @Synchronized private fun saveHistory(context: Context) {
        val array = JSONArray()
        history.values.toList().takeLast(4000).forEach { row ->
            array.put(JSONObject().apply {
                put("pkg", row.packageName)
                put("channel", row.channelId)
                put("user", row.userId)
                put("protected", row.protected)
                put("category", row.category.orEmpty())
            })
        }
        context.getSharedPreferences(HISTORY_PREF, Context.MODE_PRIVATE)
            .edit().putString(HISTORY_KEY, array.toString()).apply()
    }

    private val history = linkedMapOf<String, ActiveNotificationRow>()
    private val mutableHistory = MutableStateFlow<List<ActiveNotificationRow>>(emptyList())
    val observedRows: StateFlow<List<ActiveNotificationRow>> = mutableHistory.asStateFlow()
    private val mutableScan = MutableStateFlow(NotificationScanState(NotificationScanStatus.DISCONNECTED, 0, 0))
    val scan: StateFlow<NotificationScanState> = mutableScan.asStateFlow()
    private fun observationKey(row: ActiveNotificationRow) =
        "${row.userId}|${row.packageName}|${row.channelId}|${row.category.orEmpty()}"
    @Synchronized private fun recordObservation(current: List<ActiveNotificationRow>) {
        current.forEach { history[observationKey(it)] = it }
        while (history.size > 4000) history.remove(history.keys.first())
        mutableHistory.value = history.values.toList()
        mutableScan.value = NotificationScanState(
            NotificationScanStatus.CONNECTED, current.size, history.size)
    }


    internal fun attach(value: ListCleanerNotificationListener) {
        listener = value
        loadHistory(value.applicationContext)
        mutableConnected.value = true
        refresh(value)
    }

    internal fun detach(value: ListCleanerNotificationListener) {
        if (listener !== value) return
        listener = null
        mutableConnected.value = false
        mutableRows.value = emptyList()
        mutableScan.value = mutableScan.value.copy(status = NotificationScanStatus.DISCONNECTED, active = 0)
    }

    internal fun refresh(value: ListCleanerNotificationListener) {
        if (listener !== value) return
        val snapshot = runCatching { value.activeNotifications?.toList() }.getOrNull()
        if (snapshot == null) {
            mutableScan.value = mutableScan.value.copy(status = NotificationScanStatus.FAILED)
            return
        }
        mutableRows.value = snapshot.mapNotNull(::toRow).sortedWith(
            compareBy(ActiveNotificationRow::packageName, ActiveNotificationRow::channelId,
                ActiveNotificationRow::key)
        )
        recordObservation(mutableRows.value)
        saveHistory(value.applicationContext)
    }

    private fun toRow(item: StatusBarNotification): ActiveNotificationRow? {
        val notification = item.notification ?: return null
        return ActiveNotificationRow(
            key = item.key,
            packageName = item.packageName,
            channelId = notification.channelId ?: "(default)",
            userId = item.userId,
            protected = NotificationCleanupPolicy.protected(notification.flags, notification.category),
            category = notification.category,
        )
    }

    /** Root probe is read-only. Failure is not proof of an empty channel registry. */
    // Resolve UIDs in the selected Android user; reuse one Root package listing.
    private val rootPackageUids = mutableMapOf<Int, Map<String, Int>>()

    @Synchronized
    private fun targetPackageUid(pkg: String, userId: Int): Int? {
        if (userId !in 0..9999) return null
        val uids = rootPackageUids[userId] ?: run {
            val result = runCatching {
                ComponentRootCommand.runReadOnly("cmd package list packages --user $userId -U")
            }.getOrNull() ?: return null
            if (result.exitCode != 0 || result.timedOut) return null
            val parsed = mutableMapOf<String, Int>()
            val entry = Regex("^package:([^\\s]+)\\s+uid:(\\d+)")
            result.output.lineSequence().forEach { line ->
                val match = entry.find(line.trim()) ?: return@forEach
                val uid = match.groupValues[2].toIntOrNull() ?: return@forEach
                parsed[match.groupValues[1]] = uid
            }
            if (parsed.isEmpty()) return null
            rootPackageUids[userId] = parsed
            parsed
        }
        return uids[pkg]
    }

    suspend fun channelsForPackages(packages: List<String>, userId: Int): Map<String, ChannelInventory> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, ChannelInventory>()
            val context = listener?.applicationContext
            val apk = context?.applicationInfo?.sourceDir
            val valid = packages.distinct().filter {
                it.matches(Regex("[A-Za-z0-9_]+([.][A-Za-z0-9_]+)+"))
            }
            if (apk != null && apk.startsWith("/") && !apk.contains("'") && !apk.contains("\\n")) {
                for (batch in valid.chunked(32)) {
                    val pairs = batch.mapNotNull { pkg ->
                        targetPackageUid(pkg, userId)?.let { uid -> "$pkg $uid" }
                    }
                    if (pairs.isEmpty()) continue
                    val command = "CLASSPATH='$apk' app_process /system/bin " +
                        "com.yagay.ListCleaner.runtime.RootNotificationChannelProbe " +
                        pairs.joinToString(" ")
                    val response = runCatching { ComponentRootCommand.runReadOnly(command) }.getOrNull()
                    if (response?.exitCode != 0 || response.timedOut) continue
                    val line = response.output.lineSequence().firstOrNull {
                        it.startsWith("LC_CHANNELS:")
                    } ?: continue
                    runCatching {
                        val rows = JSONArray(line.removePrefix("LC_CHANNELS:"))
                        for (i in 0 until rows.length()) {
                            val entry = rows.getJSONObject(i)
                            val pkg = entry.getString("pkg")
                            val channels = entry.optJSONArray("channels") ?: continue
                            val items = (0 until minOf(channels.length(), 2000)).map { n ->
                                val ch = channels.getJSONObject(n)
                                RegisteredChannelRow(pkg, ch.getString("id"), ch.getString("name"),
                                    ch.getInt("importance"), ch.optString("group").ifBlank { null })
                            }
                            result[pkg] = ChannelInventory(items, ChannelInventoryStatus.COMPLETE)
                        }
                    }
                }
            }
            for (pkg in packages.distinct()) {
                if (pkg !in result) result[pkg] = channelsForPackageFallback(pkg, userId)
            }
            result
        }

    private suspend fun channelsForPackageFallback(pkg: String, userId: Int): ChannelInventory =
        withContext(Dispatchers.IO) {
            val current = listener
            val all = runCatching {
                val available = current ?: throw IllegalStateException("Listener disconnected")
                val handle = if (userId == android.os.Process.myUid() / 100000)
                    android.os.Process.myUserHandle()
                else throw SecurityException("Cross-user registry is privileged")
                val groups = runCatching {
                    available.getNotificationChannelGroups(pkg, handle)
                        ?.associate { it.id to it.name?.toString().orEmpty() }
                }.getOrNull().orEmpty()
                (available.getNotificationChannels(pkg, handle)
                    ?: throw SecurityException("Channel registry not available"))
                    .map { channel ->
                        RegisteredChannelRow(pkg, channel.id, channel.name?.toString() ?: channel.id,
                            channel.importance, channel.group, groups[channel.group])
                    }.distinctBy { it.channelId }.sortedBy { it.label.lowercase() }
            }.getOrNull()
            if (all != null) ChannelInventory(all, ChannelInventoryStatus.COMPLETE)
            else {
                val observed = observedRows.value.filter { it.packageName == pkg && it.userId == userId }
                    .map { RegisteredChannelRow(pkg, it.channelId, it.channelId, -1) }
                    .distinctBy { it.channelId }.sortedBy { it.channelId }
                ChannelInventory(observed, if (current == null) ChannelInventoryStatus.DISCONNECTED
                    else ChannelInventoryStatus.OBSERVED_ONLY)
            }
        }

    suspend fun channelsForPackage(pkg: String, userId: Int): ChannelInventory =
        channelsForPackages(listOf(pkg), userId)[pkg]
            ?: ChannelInventory(emptyList(), ChannelInventoryStatus.DISCONNECTED)

    /** User-requested rescan; does not claim access to channels never observed. */
    fun rescan(): Boolean {
        val current = listener ?: return false
        refresh(current)
        return mutableScan.value.status == NotificationScanStatus.CONNECTED
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
