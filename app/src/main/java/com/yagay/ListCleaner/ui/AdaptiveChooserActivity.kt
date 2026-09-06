package com.yagay.ListCleaner.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.intentKind

class AdaptiveChooserActivity : Activity() {
    private lateinit var target: Intent
    private lateinit var sourcePackage: String
    private var allItems: List<ResolveInfo> = emptyList()
    private lateinit var adapter: TargetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        @Suppress("DEPRECATION")
        val supplied = runCatching { intent.getParcelableExtra<Intent>(EXTRA_TARGET) }.getOrNull()
        sourcePackage = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        if (supplied == null || sourcePackage.isBlank()) {
            Toast.makeText(this, "无法恢复原始打开请求", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        target = Intent(supplied).apply { setComponent(null); setPackage(null) }
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() }
            ?: target.intentKind(target.type)
            ?: IntentKind.OPEN
        allItems = queryAndFilter(kind)
        render(kind)
        configurePopupWindow()
    }

    private fun configurePopupWindow() {
        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.96f).toInt()
        val maxHeight = (metrics.heightPixels * 0.70f).toInt()
        window.setLayout(width, maxHeight)
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.32f }
    }

    private fun queryAndFilter(kind: IntentKind): List<ResolveInfo> {
        val app = application as ListCleanerApp
        val repo = app.rules
        val selected = repo.rules.value.filter { it.kind == kind }.map(ComponentRule::id).toSet()
        val mode = repo.displayMode.value
        val queried = packageManager.queryIntentActivities(target, 0)
        return queried.asSequence()
            .filter { it.activityInfo != null && it.activityInfo.packageName != packageName }
            .filter { info ->
                val ai = info.activityInfo
                val id = ComponentRule(kind, ai.packageName, ai.name).id
                when (mode) {
                    DisplayMode.SHOW_ALL -> true
                    DisplayMode.HIDE_SELECTED -> id !in selected
                    DisplayMode.SHOW_SELECTED -> id in selected
                }
            }
            .distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.loadLabel(packageManager)?.toString().orEmpty() })
            .toList()
    }

    private fun render(kind: IntentKind) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        val title = TextView(this).apply {
            text = when (kind) {
                IntentKind.SHARE, IntentKind.SHARE_MULTIPLE -> "分享到"
                else -> "打开方式"
            }
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        val search = EditText(this).apply {
            hint = "搜索应用"
            isSingleLine = true
        }
        val list = ListView(this).apply { dividerHeight = 0 }
        adapter = TargetAdapter(this, allItems) { launchTarget(it) }
        list.adapter = adapter
        search.addTextChangedListener(SimpleTextWatcher { q ->
            val query = q.trim()
            val filtered = if (query.isEmpty()) allItems else allItems.filter { info ->
                val label = info.loadLabel(packageManager)?.toString().orEmpty()
                label.contains(query, true) || info.activityInfo.packageName.contains(query, true)
            }
            adapter.replace(filtered)
        })
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (allItems.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "没有可用应用"
                gravity = Gravity.CENTER
                setPadding(0, dp(32), 0, dp(32))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    private fun launchTarget(info: ResolveInfo) {
        val ai = info.activityInfo
        val outgoing = Intent(target).apply {
            setComponent(ComponentName(ai.packageName, ai.name))
            setPackage(null)
        }
        val uris = collectGrantUris(outgoing)
        var grantFlags = outgoing.flags and URI_GRANT_FLAGS
        if (uris.isNotEmpty() && grantFlags == 0) {
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            outgoing.addFlags(grantFlags)
        }
        val grantFailure = runCatching {
            uris.forEach { uri -> grantUriPermission(ai.packageName, uri, grantFlags) }
        }.exceptionOrNull()
        if (grantFailure != null) {
            Log.w(TAG, "URI grant failed source=$sourcePackage target=${ai.packageName} uris=${uris.size} flags=$grantFlags", grantFailure)
        }
        runCatching { startActivity(outgoing) }
            .onSuccess { finish() }
            .onFailure {
                Log.w(TAG, "Launch failed source=$sourcePackage target=${ai.packageName} uris=${uris.size} flags=$grantFlags", it)
                val suffix = if (it is SecurityException) "（URI 权限不足）" else ""
                Toast.makeText(this, "打开失败：${it.javaClass.simpleName}$suffix", Toast.LENGTH_SHORT).show()
            }
    }

    @Suppress("DEPRECATION")
    private fun collectGrantUris(intent: Intent): Set<Uri> {
        val uris = linkedSetOf<Uri>()
        intent.data?.let(uris::add)
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(uris::add)
        }
        runCatching { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }.getOrNull()?.let(uris::add)
        runCatching { intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) }.getOrNull()?.forEach(uris::add)
        return uris
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "ListCleanerChooser"
        private const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        const val EXTRA_TARGET = "com.yagay.ListCleaner.extra.ADAPTIVE_TARGET"
        const val EXTRA_SOURCE = "com.yagay.ListCleaner.extra.ADAPTIVE_SOURCE"
        const val EXTRA_KIND = "com.yagay.ListCleaner.extra.ADAPTIVE_KIND"
    }
}

private class TargetAdapter(
    private val context: Context,
    private var items: List<ResolveInfo>,
    private val click: (ResolveInfo) -> Unit,
) : BaseAdapter() {
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): ResolveInfo = items[position]
    override fun getItemId(position: Int): Long = position.toLong()
    fun replace(next: List<ResolveInfo>) { items = next; notifyDataSetChanged() }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val info = getItem(position)
        val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(ImageView(context), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD) })
                addView(TextView(context).apply { textSize = 12f })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val icon = row.getChildAt(0) as ImageView
        val texts = row.getChildAt(1) as LinearLayout
        val label = texts.getChildAt(0) as TextView
        val detail = texts.getChildAt(1) as TextView
        icon.setImageDrawable(runCatching { info.loadIcon(context.packageManager) }.getOrNull())
        label.text = info.loadLabel(context.packageManager)?.toString() ?: info.activityInfo.packageName
        detail.text = info.activityInfo.packageName
        row.setOnClickListener { click(info) }
        return row
    }
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed(s?.toString().orEmpty())
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
