from pathlib import Path
import re

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

# The custom Activity no longer needs the system-server grant bridge.
s = s.replace('        installAdaptiveGrantBridge()\n', '')

start = s.index('    private fun tryRedirectLearnedChooser(')
end = s.index('    @Suppress("DEPRECATION")\n    private fun buildAdaptiveChooserPayload', start)
new_func = r'''    private fun tryRedirectLearnedChooser(
        request: Any,
        source: Intent,
        view: ActivityStartView,
        component: ComponentName,
        template: LearnedChooserTemplate,
        current: RuleSnapshot,
    ): Boolean {
        // Learned custom chooser entry points are converted back into Android's own chooser.
        // Keeping the launch inside the original ActivityStarter request preserves the source UID
        // so Resolver/ChooserActivity can perform URI grant handling as the real caller.
        if (view.callerPackage == MANAGER_PACKAGE || source.action == Intent.ACTION_CHOOSER) return false
        val payload = buildAdaptiveChooserPayload(source, template) ?: run {
            if (current.diagnostic) diagnostic("CHOOSER_SYSTEM_REDIRECT_SKIP caller=${view.callerPackage} component=${component.flattenToShortString()} reason=no_current_payload kind=${template.kind}")
            return false
        }
        if (payloadHasUri(payload) && payload.flags and URI_GRANT_FLAGS == 0) {
            payload.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(
            payload,
            if (template.kind in setOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE)) "分享到" else "打开方式",
        ).apply {
            // Preserve task semantics only. URI grant flags belong to the nested target Intent.
            addFlags(source.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        // Reuse the component-precise rules for standard ChooserActivity as an extra safety layer.
        injectSystemChooserExclusions(chooser, view.callerPackage, view.uid, current)

        val userId = (readNamedField(request, listOf("userId")) as? Int) ?: 0
        val resolved = resolveRedirectActivity(chooser, userId) ?: run {
            if (current.diagnostic) diagnostic("CHOOSER_SYSTEM_REDIRECT_SKIP caller=${view.callerPackage} component=${component.flattenToShortString()} reason=system_chooser_unresolved")
            return false
        }
        val intentField = allInstanceFields(request.javaClass).firstOrNull { it.name == "intent" && Intent::class.java.isAssignableFrom(it.type) } ?: return false
        val resolveField = allInstanceFields(request.javaClass).firstOrNull { it.name == "resolveInfo" && ResolveInfo::class.java.isAssignableFrom(it.type) } ?: return false
        val activityField = allInstanceFields(request.javaClass).firstOrNull { it.name == "activityInfo" && ActivityInfo::class.java.isAssignableFrom(it.type) } ?: return false
        val resolvedTypeField = allInstanceFields(request.javaClass).firstOrNull { it.name == "resolvedType" }
        val componentSpecifiedField = allInstanceFields(request.javaClass).firstOrNull { it.name == "componentSpecified" }

        return runCatching {
            intentField.isAccessible = true
            resolveField.isAccessible = true
            activityField.isAccessible = true
            intentField.set(request, chooser)
            resolveField.set(request, resolved)
            activityField.set(request, resolved.activityInfo)
            resolvedTypeField?.let { it.isAccessible = true; it.set(request, null) }
            componentSpecifiedField?.let { it.isAccessible = true; it.setBoolean(request, false) }
            diagnostic(
                "CHOOSER_SYSTEM_REDIRECT_APPLIED uid=${view.uid} caller=${view.callerPackage} " +
                    "from=${component.flattenToShortString()} kind=${template.kind} targetAction=${payload.action} " +
                    "mime=${payload.type} uri=${payloadHasUri(payload)} resolver=${resolved.activityInfo?.packageName}/${resolved.activityInfo?.name}"
            )
            true
        }.getOrElse {
            diagnostic("CHOOSER_SYSTEM_REDIRECT_FAILED caller=${view.callerPackage} component=${component.flattenToShortString()} error=${it.javaClass.name}")
            false
        }
    }

'''
s = s[:start] + new_func + s[end:]

# resolveRedirectActivity used to accept only List Cleaner's proxy Activity. It now resolves the
# platform chooser itself, so remove that manager-only validation.
old = '''            }?.takeIf { it.activityInfo?.packageName == MANAGER_PACKAGE && it.activityInfo?.name == ADAPTIVE_CHOOSER_ACTIVITY }\n'''
if old not in s:
    raise SystemExit('manager-only resolve validation not found')
s = s.replace(old, '''            }\n''', 1)

# Remove the old bridge implementation and related state now that system ChooserActivity owns grants.
state_pattern = re.compile(r'''    private data class ChooserGrantSession\(.*?    @Volatile private var adaptiveGrantReceiver: BroadcastReceiver\? = null\n''', re.S)
s, n = state_pattern.subn('', s, count=1)
if n != 1:
    raise SystemExit('grant session state block not found')

# Remove hot-reload receiver cleanup.
s = re.sub(r'''        adaptiveGrantReceiver\?\.let \{ receiver ->\n            runCatching \{ adaptiveGrantContext\?\.unregisterReceiver\(receiver\) \}\n        \}\n        adaptiveGrantReceiver = null\n        adaptiveGrantContext = null\n        chooserGrantSessions\.clear\(\)\n''', '', s, count=1)

bridge_start = s.find('    private fun installAdaptiveGrantBridge()')
if bridge_start != -1:
    bridge_end = s.index('    private sealed interface PackageNameAccessor', bridge_start)
    s = s[:bridge_start] + s[bridge_end:]

# Remove imports that only existed for the bridge/session implementation.
for imp in [
    'import android.content.BroadcastReceiver\n',
    'import android.content.IntentFilter\n',
    'import java.util.UUID\n',
]:
    s = s.replace(imp, '')

p.write_text(s)
print('patched', p)
