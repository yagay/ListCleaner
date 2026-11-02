package com.yagay.ListCleaner.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.CapturedSettingsPage

/**
 * User-controlled floating switch for short-lived Settings page identification.
 * The overlay is purely a control: no input interception or screen content reading.
 * Only the observer in the hooked Settings process supplies Activity/Fragment metadata.
 */
class SettingsPageFloatingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var successUntil = 0L
    private var capturedName: String = ""
    private var lastNotificationState = ""

    private val refresh = object : Runnable {
        override fun run() {
            if (bubble == null) return
            if (successUntil > 0 && android.os.SystemClock.elapsedRealtime() >= successUntil) {
                successUntil = 0
                capturedName = ""
            }
            render()
            updateNotificationIfChanged()
            handler.postDelayed(this, 750L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // A foreground capture session must still work when Android hides or refuses
        // third-party overlay windows on protected Settings pages.
        val foregroundReady = runCatching {
            createChannel()
            startForeground(NOTIFICATION_ID, notification())
        }.isSuccess
        if (!foregroundReady) {
            stopSelf()
            return
        }
        running = this
        windowManager = getSystemService(WindowManager::class.java)
        if (!Settings.canDrawOverlays(this)) {
            // Notification actions remain accessible without overlay permission.
            if (!SettingsPageCaptureStore.active(this)) SettingsPageCaptureStore.begin(this)
            updateNotificationIfChanged(force = true)
            return
        }
        val size = dp(52)
        val initial = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(78)
            y = resources.displayMetrics.heightPixels / 3
        }
        val view = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            elevation = dp(6).toFloat()
            contentDescription = getString(R.string.page_capture_float_active)
        }
        if (runCatching { windowManager.addView(view, initial) }.isFailure) {
            // Do not destroy the capture session when the OEM restricts overlays.
            if (!SettingsPageCaptureStore.active(this)) SettingsPageCaptureStore.begin(this)
            updateNotificationIfChanged(force = true)
            return
        }
        bubble = view
        params = initial
        attachTouchControls(view)
        // Starting from ListCleaner arms capture before navigating to the actual page.
        if (!SettingsPageCaptureStore.active(this)) SettingsPageCaptureStore.begin(this)
        render()
        handler.post(refresh)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_CAPTURE -> toggle()
            ACTION_STOP_CAPTURE -> stopSelf()
            else -> {
                if (!SettingsPageCaptureStore.active(this) && capturedName.isEmpty()) {
                    SettingsPageCaptureStore.begin(this)
                }
            }
        }
        updateNotificationIfChanged(force = true)
        return START_NOT_STICKY
    }

    private fun attachTouchControls(view: TextView) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var pointerX = 0f
        var pointerY = 0f
        var moved = false
        var longPressed = false
        val stopOnHold = Runnable {
            longPressed = true
            Toast.makeText(this, R.string.page_capture_float_closed, Toast.LENGTH_SHORT).show()
            stopSelf()
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val p = params ?: return@setOnTouchListener false
                    initialX = p.x
                    initialY = p.y
                    pointerX = event.rawX
                    pointerY = event.rawY
                    moved = false
                    longPressed = false
                    handler.postDelayed(stopOnHold, 800L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressed) return@setOnTouchListener true
                    val dx = (event.rawX - pointerX).toInt()
                    val dy = (event.rawY - pointerY).toInt()
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) {
                        moved = true
                        handler.removeCallbacks(stopOnHold)
                    }
                    if (moved) {
                        params?.let { p ->
                            p.x = (initialX + dx).coerceAtLeast(0)
                            p.y = (initialY + dy).coerceAtLeast(0)
                            runCatching { windowManager.updateViewLayout(view, p) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(stopOnHold)
                    if (!moved && !longPressed) toggle()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(stopOnHold)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggle() {
        if (SettingsPageCaptureStore.active(this)) {
            SettingsPageCaptureStore.stop(this)
            Toast.makeText(this, R.string.page_capture_float_paused, Toast.LENGTH_SHORT).show()
        } else {
            val ok = SettingsPageCaptureStore.begin(this)
            Toast.makeText(this, if (ok) R.string.page_capture_float_active else R.string.page_capture_start_failed,
                Toast.LENGTH_SHORT).show()
        }
        successUntil = 0L
        capturedName = ""
        render()
        updateNotificationIfChanged(force = true)
    }

    private fun onCaptured(page: CapturedSettingsPage) {
        if (!SettingsPageCaptureStore.active(this)) return
        capturedName = (page.fragmentClass.ifEmpty { page.activityClass }).substringAfterLast('.')
        successUntil = android.os.SystemClock.elapsedRealtime() + 2_800L
        render()
        Toast.makeText(this, getString(R.string.page_capture_float_success, capturedName),
            Toast.LENGTH_SHORT).show()
        updateNotificationIfChanged(force = true)
    }

    private fun render() {
        val view = bubble ?: return
        val isActive = SettingsPageCaptureStore.active(this)
        val showSuccess = successUntil > android.os.SystemClock.elapsedRealtime()
        val color = when {
            showSuccess -> Color.rgb(46, 125, 50)
            isActive -> Color.rgb(25, 118, 210)
            else -> Color.rgb(97, 97, 97)
        }
        val title = when {
            showSuccess -> getString(R.string.page_capture_float_success_badge)
            isActive -> getString(R.string.page_capture_float_active_badge)
            else -> getString(R.string.page_capture_float_paused_badge)
        }
        view.text = title
        view.contentDescription = when {
            showSuccess -> getString(R.string.page_capture_float_success, capturedName)
            isActive -> getString(R.string.page_capture_float_active)
            else -> getString(R.string.page_capture_float_paused)
        }
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun updateNotificationIfChanged(force: Boolean = false) {
        val active = SettingsPageCaptureStore.active(this)
        val state = "$active/$capturedName"
        if (!force && state == lastNotificationState) return
        lastNotificationState = state
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    private fun notification(): Notification {
        val active = SettingsPageCaptureStore.active(this)
        val toggleIntent = PendingIntent.getService(
            this, 1, Intent(this, SettingsPageFloatingService::class.java).setAction(ACTION_TOGGLE_CAPTURE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, SettingsPageFloatingService::class.java).setAction(ACTION_STOP_CAPTURE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, com.yagay.ListCleaner.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(
                if (capturedName.isNotBlank()) getString(R.string.page_capture_float_success, capturedName)
                else getString(R.string.page_capture_float_notification_title)
            )
            .setContentText(
                if (active) getString(R.string.page_capture_float_notification_running)
                else getString(R.string.page_capture_float_notification_paused)
            )
            .setContentIntent(openApp)
            .addAction(
                if (active) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                getString(if (active) R.string.page_capture_float_paused_badge_long else R.string.page_capture_float_resume),
                toggleIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.page_capture_float_stop), stopIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, getString(R.string.page_capture_float_notification_title),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        handler.removeCallbacksAndMessages(null)
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        SettingsPageCaptureStore.stop(this)
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "settings-page-floating"
        const val ACTION_TOGGLE_CAPTURE = "com.yagay.ListCleaner.action.TOGGLE_PAGE_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.yagay.ListCleaner.action.STOP_PAGE_CAPTURE"
        private const val NOTIFICATION_ID = 33021
        @Volatile private var running: SettingsPageFloatingService? = null

        fun isShowing(): Boolean = running != null

        fun launch(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            return runCatching {
                ContextCompat.startForegroundService(context,
                    Intent(context, SettingsPageFloatingService::class.java))
                true
            }.getOrDefault(false)
        }

        fun close(context: Context) {
            context.stopService(Intent(context, SettingsPageFloatingService::class.java))
        }

        /** Called only after the authenticated provider has committed this observation. */
        fun captured(page: CapturedSettingsPage) {
            running?.let { service ->
                service.handler.post { if (running === service) service.onCaptured(page) }
            }
        }
    }
}
