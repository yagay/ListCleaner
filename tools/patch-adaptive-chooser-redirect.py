from pathlib import Path

module = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
text = module.read_text()

def repl(old, new):
    global text
    if old not in text:
        raise SystemExit('anchor not found: ' + old[:140])
    text = text.replace(old, new, 1)

repl(
'''    private val recentChooserLaunches = ConcurrentHashMap<Int, RecentChooserLaunch>()\n    private val learnedChooserHits = ConcurrentHashMap<String, Int>()\n''',
'''    private val recentChooserLaunches = ConcurrentHashMap<Int, RecentChooserLaunch>()\n    private val learnedChooserHits = ConcurrentHashMap<String, Int>()\n    private data class LearnedChooserTemplate(val kind: IntentKind, val action: String?, val mime: String?, val flags: Int)\n    private val learnedChooserTemplates = ConcurrentHashMap<String, LearnedChooserTemplate>()\n''')

old_observe = '''        if (intent.action == Intent.ACTION_CHOOSER) {\n            injectSystemChooserExclusions(intent, callerPackage, uid, current)\n        }\n\n        if (!current.diagnostic) return\n        val component = intent.component ?: return\n        if (component.packageName != callerPackage) return\n'''
new_observe = '''        if (intent.action == Intent.ACTION_CHOOSER) {\n            injectSystemChooserExclusions(intent, callerPackage, uid, current)\n        }\n\n        val component = intent.component\n        if (component != null && component.packageName == callerPackage) {\n            val routeKey = "$callerPackage|${component.flattenToShortString()}"\n            val template = learnedChooserTemplates[routeKey]\n            if (template != null && tryRedirectLearnedChooser(request, intent, view, component, template, current)) return\n        }\n\n        if (!current.diagnostic) return\n        if (component == null || component.packageName != callerPackage) return\n'''
repl(old_observe, new_observe)

old_learn = '''        val confidence = when {\n            score >= 6 && hits >= 2 -> "HIGH"\n            score >= 6 -> "MEDIUM"\n            else -> "LOW"\n        }\n        diagnostic("CHOOSER_LEARNED uid=$callerUid caller=${launch.callerPackage} component=${launch.component} kind=$kind candidates=$candidateCount score=$score hits=$hits confidence=$confidence ageMs=$age")\n'''
new_learn = '''        val confidence = when {\n            score >= 6 && hits >= 2 -> "HIGH"\n            score >= 6 -> "MEDIUM"\n            else -> "LOW"\n        }\n        if (confidence == "HIGH") {\n            val routeKey = "${launch.callerPackage}|${launch.component}"\n            learnedChooserTemplates[routeKey] = LearnedChooserTemplate(kind, intent.action, intent.type, intent.flags)\n            diagnostic("CHOOSER_REDIRECT_ARMED caller=${launch.callerPackage} component=${launch.component} kind=$kind mime=${intent.type}")\n        }\n        diagnostic("CHOOSER_LEARNED uid=$callerUid caller=${launch.callerPackage} component=${launch.component} kind=$kind candidates=$candidateCount score=$score hits=$hits confidence=$confidence ageMs=$age")\n'''
repl(old_learn, new_learn)

insert_anchor = '''    private sealed interface PackageNameAccessor {\n'''
helpers = r'''    private fun tryRedirectLearnedChooser(
        request: Any,
        source: Intent,
        view: ActivityStartView,
        component: ComponentName,
        template: LearnedChooserTemplate,
        current: RuleSnapshot,
    ): Boolean {
        // Never redirect the manager itself or a system chooser. A learned route must be HIGH confidence.
        if (view.callerPackage == MANAGER_PACKAGE || source.action == Intent.ACTION_CHOOSER) return false
        val payload = buildAdaptiveChooserPayload(source, template) ?: run {
            if (current.diagnostic) diagnostic("CHOOSER_REDIRECT_SKIP caller=${view.callerPackage} component=${component.flattenToShortString()} reason=no_current_payload kind=${template.kind}")
            return false
        }
        val proxy = Intent().apply {
            component = ComponentName(MANAGER_PACKAGE, ADAPTIVE_CHOOSER_ACTIVITY)
            putExtra(ADAPTIVE_TARGET_EXTRA, payload)
            putExtra(ADAPTIVE_SOURCE_EXTRA, view.callerPackage)
            putExtra(ADAPTIVE_KIND_EXTRA, template.kind.name)
            addFlags(source.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            addFlags(payload.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
        }
        val userId = (readNamedField(request, listOf("userId")) as? Int) ?: 0
        val resolved = resolveRedirectActivity(proxy, userId) ?: run {
            if (current.diagnostic) diagnostic("CHOOSER_REDIRECT_SKIP caller=${view.callerPackage} component=${component.flattenToShortString()} reason=proxy_unresolved")
            return false
        }
        val intentField = allInstanceFields(request.javaClass).firstOrNull { it.name == "intent" && Intent::class.java.isAssignableFrom(it.type) } ?: return false
        val resolveField = allInstanceFields(request.javaClass).firstOrNull { it.name == "resolveInfo" && ResolveInfo::class.java.isAssignableFrom(it.type) } ?: return false
        val activityField = allInstanceFields(request.javaClass).firstOrNull { it.name == "activityInfo" && ActivityInfo::class.java.isAssignableFrom(it.type) } ?: return false
        val resolvedTypeField = allInstanceFields(request.javaClass).firstOrNull { it.name == "resolvedType" }
        val componentSpecifiedField = allInstanceFields(request.javaClass).firstOrNull { it.name == "componentSpecified" }
        val grantField = allInstanceFields(request.javaClass).firstOrNull { it.name == "intentGrants" }
        val grants = grantField?.let { field -> runCatching { field.isAccessible = true; field.get(request) }.getOrNull() }
        if (grants != null && payloadHasUri(payload) && !retargetNeededUriGrants(grants, resolved.activityInfo?.applicationInfo?.uid)) {
            if (current.diagnostic) diagnostic("CHOOSER_REDIRECT_SKIP caller=${view.callerPackage} component=${component.flattenToShortString()} reason=uri_grant_retarget_failed")
            return false
        }
        return runCatching {
            intentField.isAccessible = true
            resolveField.isAccessible = true
            activityField.isAccessible = true
            intentField.set(request, proxy)
            resolveField.set(request, resolved)
            activityField.set(request, resolved.activityInfo)
            resolvedTypeField?.let { it.isAccessible = true; it.set(request, null) }
            componentSpecifiedField?.let { it.isAccessible = true; it.setBoolean(request, true) }
            diagnostic("CHOOSER_REDIRECT_APPLIED uid=${view.uid} caller=${view.callerPackage} from=${component.flattenToShortString()} kind=${template.kind} targetAction=${payload.action} mime=${payload.type} uri=${payload.data != null}")
            true
        }.getOrElse {
            diagnostic("CHOOSER_REDIRECT_FAILED caller=${view.callerPackage} component=${component.flattenToShortString()} error=${it.javaClass.name}")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun buildAdaptiveChooserPayload(source: Intent, template: LearnedChooserTemplate): Intent? {
        fun nested(intent: Intent, depth: Int): Intent? {
            if (depth > 3) return null
            val selector = intent.selector
            if (selector != null) {
                val kind = selector.intentKind(selector.type)
                if (kind == template.kind && payloadHasUri(selector)) return Intent(selector).apply { component = null; setPackage(null) }
                nested(selector, depth + 1)?.let { return it }
            }
            val extraIntent = runCatching { intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) }.getOrNull()
            if (extraIntent != null) {
                val kind = extraIntent.intentKind(extraIntent.type)
                if (kind == template.kind && payloadHasUri(extraIntent)) return Intent(extraIntent).apply { component = null; setPackage(null) }
                nested(extraIntent, depth + 1)?.let { return it }
            }
            val bundle = runCatching { intent.extras }.getOrNull()
            bundle?.keySet()?.forEach { key ->
                val value = runCatching { bundle.get(key) }.getOrNull()
                if (value is Intent) {
                    val kind = value.intentKind(value.type)
                    if (kind == template.kind && payloadHasUri(value)) return Intent(value).apply { component = null; setPackage(null) }
                    nested(value, depth + 1)?.let { return it }
                }
            }
            return null
        }
        nested(source, 0)?.let { target ->
            if (target.action == null) target.action = template.action ?: if (template.kind == IntentKind.OPEN || template.kind == IntentKind.BROWSER) Intent.ACTION_VIEW else target.action
            if (target.type == null && template.mime != null) target.type = template.mime
            target.addFlags(source.flags and URI_GRANT_FLAGS)
            return target
        }
        if (template.kind !in setOf(IntentKind.OPEN, IntentKind.BROWSER)) return null
        val uri = firstUri(source) ?: return null
        return Intent(template.action ?: Intent.ACTION_VIEW).apply {
            if (template.mime != null) setDataAndType(uri, template.mime) else data = uri
            addFlags((source.flags or template.flags) and URI_GRANT_FLAGS)
        }
    }

    @Suppress("DEPRECATION")
    private fun firstUri(intent: Intent, depth: Int = 0): android.net.Uri? {
        if (depth > 3) return null
        intent.data?.let { return it }
        intent.clipData?.let { clip -> if (clip.itemCount > 0) clip.getItemAt(0).uri?.let { return it } }
        val bundle = runCatching { intent.extras }.getOrNull() ?: return null
        bundle.keySet().forEach { key ->
            when (val value = runCatching { bundle.get(key) }.getOrNull()) {
                is android.net.Uri -> return value
                is Intent -> firstUri(value, depth + 1)?.let { return it }
                is Array<*> -> value.filterIsInstance<android.net.Uri>().firstOrNull()?.let { return it }
                is Collection<*> -> value.filterIsInstance<android.net.Uri>().firstOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun payloadHasUri(intent: Intent): Boolean = intent.data != null || intent.clipData != null || firstUri(intent) != null

    private fun resolveRedirectActivity(intent: Intent, userId: Int): ResolveInfo? {
        val identity = Binder.clearCallingIdentity()
        return try {
            val pms = Class.forName("android.app.AppGlobals").getDeclaredMethod("getPackageManager").invoke(null) ?: return null
            val methods = (pms.javaClass.methods.asSequence() + pms.javaClass.interfaces.asSequence().flatMap { it.methods.asSequence() })
                .filter { it.name == "resolveIntent" && it.parameterTypes.firstOrNull() == Intent::class.java }
                .distinctBy(Method::toGenericString)
                .toList()
            methods.firstNotNullOfOrNull { method ->
                runCatching {
                    method.isAccessible = true
                    val args = method.parameterTypes.mapIndexed { index, type ->
                        when {
                            index == 0 -> intent
                            type == String::class.java -> null
                            type == Long::class.javaPrimitiveType || type == Long::class.java -> 0L
                            type == Int::class.javaPrimitiveType || type == Int::class.java -> if (index == method.parameterTypes.lastIndex) userId else 0
                            type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> false
                            else -> null
                        }
                    }.toTypedArray()
                    method.invoke(pms, *args) as? ResolveInfo
                }.getOrNull()
            }?.takeIf { it.activityInfo?.packageName == MANAGER_PACKAGE && it.activityInfo?.name == ADAPTIVE_CHOOSER_ACTIVITY }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun retargetNeededUriGrants(grants: Any, targetUid: Int?): Boolean {
        if (targetUid == null) return false
        var packageChanged = false
        var uidChanged = false
        allInstanceFields(grants.javaClass).forEach { field ->
            runCatching {
                field.isAccessible = true
                when {
                    field.type == String::class.java && field.name.lowercase().contains("target") && (field.name.lowercase().contains("pkg") || field.name.lowercase().contains("package")) -> {
                        field.set(grants, MANAGER_PACKAGE); packageChanged = true
                    }
                    (field.type == Int::class.javaPrimitiveType || field.type == Int::class.java) && field.name.lowercase().contains("target") && field.name.lowercase().contains("uid") -> {
                        field.setInt(grants, targetUid); uidChanged = true
                    }
                }
            }
        }
        // Some platform versions only store targetPkg and infer UID later.
        return packageChanged || uidChanged
    }

'''
repl(insert_anchor, helpers + insert_anchor)

repl(
'''        const val CHOOSER_DISCOVERY_WINDOW_MS = 3_000L\n        const val MANAGER_PACKAGE = "com.yagay.ListCleaner"\n''',
'''        const val CHOOSER_DISCOVERY_WINDOW_MS = 3_000L\n        const val MANAGER_PACKAGE = "com.yagay.ListCleaner"\n        const val ADAPTIVE_CHOOSER_ACTIVITY = "com.yagay.ListCleaner.ui.AdaptiveChooserActivity"\n        const val ADAPTIVE_TARGET_EXTRA = "com.yagay.ListCleaner.extra.ADAPTIVE_TARGET"\n        const val ADAPTIVE_SOURCE_EXTRA = "com.yagay.ListCleaner.extra.ADAPTIVE_SOURCE"\n        const val ADAPTIVE_KIND_EXTRA = "com.yagay.ListCleaner.extra.ADAPTIVE_KIND"\n        const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION\n''')

module.write_text(text)

manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text()
anchor = '''        <activity android:name=".ui.MainActivity" android:exported="true">\n'''
if anchor not in m: raise SystemExit('manifest anchor missing')
m = m.replace(anchor, '''        <activity\n            android:name=".ui.AdaptiveChooserActivity"\n            android:exported="true"\n            android:excludeFromRecents="true"\n            android:launchMode="singleTop" />\n\n''' + anchor, 1)
manifest.write_text(m)

activity = Path('app/src/main/java/com/yagay/ListCleaner/ui/AdaptiveChooserActivity.kt')
activity.write_text(r'''package com.yagay.ListCleaner.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
        @Suppress("DEPRECATION")
        val supplied = runCatching { intent.getParcelableExtra<Intent>(EXTRA_TARGET) }.getOrNull()
        sourcePackage = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        if (supplied == null || sourcePackage.isBlank()) {
            Toast.makeText(this, "无法恢复原始打开请求", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        target = Intent(supplied).apply { component = null; setPackage(null) }
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() }
            ?: target.intentKind(target.type)
            ?: IntentKind.OPEN
        allItems = queryAndFilter(kind)
        render(kind)
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
            component = ComponentName(ai.packageName, ai.name)
            setPackage(null)
        }
        runCatching { startActivity(outgoing) }
            .onSuccess { finish() }
            .onFailure { Toast.makeText(this, "打开失败：${it.javaClass.simpleName}", Toast.LENGTH_SHORT).show() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
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
''')

print('adaptive chooser redirect patch applied')
