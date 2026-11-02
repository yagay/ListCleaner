package com.yagay.ListCleaner.xposed

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import com.yagay.ListCleaner.domain.RuntimeCaptureContract
import com.yagay.ListCleaner.domain.SettingsPageCaptureProtocol
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

/**
 * Temporary control rendered *inside* the visible Settings Activity's own decor.
 *
 * Android 12+ may hide TYPE_APPLICATION_OVERLAY on Settings. This is a normal
 * in-activity child and never changes Window flags, bypasses Settings security
 * permissions or hooks input outside this small view.
 *
 * Deliberately not injected into PermissionController security/permission dialogs.
 */
internal object SettingsPageNativeControl {
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val CONTROL_TAG = "ListCleaner.SettingsCaptureControl"
    private val main = Handler(Looper.getMainLooper())

    fun maybeAttach(activity: Activity, executor: Executor) {
        if (activity.packageName != SETTINGS_PACKAGE) return
        val weak = WeakReference(activity)
        executor.execute {
            val status = call(activity.applicationContext, SettingsPageCaptureProtocol.METHOD_CONTROL_STATUS)
            val enabled = status?.getBoolean(SettingsPageCaptureProtocol.KEY_CONTROL_ENABLED, false) == true
            val active = status?.getBoolean(SettingsPageCaptureProtocol.KEY_CONTROL_ACTIVE, false) == true
            main.post {
                val owner = weak.get() ?: return@post
                if (owner.isFinishing || owner.isDestroyed) return@post
                if (enabled) attach(owner, executor, active) else remove(owner)
            }
        }
    }

    fun showSuccess(activity: Activity) {
        if (activity.packageName != SETTINGS_PACKAGE) return
        val weak = WeakReference(activity)
        main.post {
            val owner = weak.get() ?: return@post
            val root = owner.window.decorView as? FrameLayout ?: return@post
            val view = root.findViewWithTag<TextView>(CONTROL_TAG) ?: return@post
            paint(view, 2)
            main.postDelayed({
                if (view.parent === root) {
                    // The next status query will repaint after pause/resume.
                    paint(view, 1)
                }
            }, 2200L)
        }
    }

    private fun call(context: Context, operation: String): Bundle? =
        runCatching {
            context.contentResolver.call(
                Uri.parse("content://" + RuntimeCaptureContract.AUTHORITY),
                operation, null, null
            )
        }.getOrNull()

    private fun attach(activity: Activity, executor: Executor, active: Boolean) {
        val root = activity.window.decorView as? FrameLayout ?: return
        val old = root.findViewWithTag<TextView>(CONTROL_TAG)
        if (old != null) {
            paint(old, if (active) 1 else 0)
            return
        }
        val size = dp(activity, 48)
        val view = TextView(activity).apply {
            tag = CONTROL_TAG
            gravity = Gravity.CENTER
            textSize = 21f
            setTextColor(Color.WHITE)
            elevation = dp(activity, 7).toFloat()
            contentDescription = "ListCleaner Settings page capture: tap to pause or resume, hold to close"
        }
        root.addView(view, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START))
        paint(view, if (active) 1 else 0)
        root.post {
            if (view.parent === root) {
                view.x = (root.width - size - dp(activity, 18)).toFloat().coerceAtLeast(0f)
                view.y = dp(activity, 100).toFloat().coerceAtMost((root.height - size).toFloat().coerceAtLeast(0f))
            }
        }

        val weak = WeakReference(activity)
        view.setOnClickListener {
            executor.execute {
                val result = call(activity.applicationContext, SettingsPageCaptureProtocol.METHOD_CONTROL_TOGGLE)
                main.post {
                    val owner = weak.get() ?: return@post
                    if (owner.isDestroyed || view.parent == null) return@post
                    if (result?.getBoolean(SettingsPageCaptureProtocol.KEY_CONTROL_ENABLED, false) != true) {
                        remove(owner)
                    } else paint(view,
                        if (result.getBoolean(SettingsPageCaptureProtocol.KEY_CONTROL_ACTIVE, false)) 1 else 0)
                }
            }
        }
        val slop = ViewConfiguration.get(activity).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragged = false
        var held = false
        val hold = Runnable {
            held = true
            executor.execute {
                call(activity.applicationContext, SettingsPageCaptureProtocol.METHOD_CONTROL_CLOSE)
                main.post { weak.get()?.let(::remove) }
            }
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = v.x
                    startY = v.y
                    dragged = false
                    held = false
                    main.postDelayed(hold, 800L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (held) return@setOnTouchListener true
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) {
                        dragged = true
                        main.removeCallbacks(hold)
                    }
                    if (dragged) {
                        v.x = (startX + dx).coerceIn(0f, (root.width - v.width).toFloat().coerceAtLeast(0f))
                        v.y = (startY + dy).coerceIn(0f, (root.height - v.height).toFloat().coerceAtLeast(0f))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(hold)
                    if (!dragged && !held) v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(hold)
                    true
                }
                else -> false
            }
        }
    }

    private fun remove(activity: Activity) {
        (activity.window.decorView as? FrameLayout)
            ?.findViewWithTag<TextView>(CONTROL_TAG)
            ?.let { (it.parent as? FrameLayout)?.removeView(it) }
    }

    /** 0 paused, 1 capturing, 2 verified and saved. */
    private fun paint(view: TextView, state: Int) {
        view.text = when (state) {
            0 -> "Ⅱ"
            2 -> "✓"
            else -> "●"
        }
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(when (state) {
                0 -> Color.rgb(97, 97, 97)
                2 -> Color.rgb(46, 125, 50)
                else -> Color.rgb(25, 118, 210)
            })
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
