from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

s = s.replace('method.name == "callActivityOnCreate"', 'method.name == "callActivityOnResume"', 1)
s = s.replace('val key = "APP_PICKER_CREATE#${method.toGenericString()}"', 'val key = "APP_PICKER_RESUME#${method.toGenericString()}"', 1)
s = s.replace('intercept(appPickerCreateHooker(packageName))', 'intercept(appPickerResumeHooker(packageName))', 1)
s = s.replace('APP_PICKER_BRIDGE_CREATE_HOOK_INSTALLED', 'APP_PICKER_BRIDGE_RESUME_HOOK_INSTALLED', 1)
s = s.replace('APP_PICKER_BRIDGE_CREATE_HOOK_FAILED', 'APP_PICKER_BRIDGE_RESUME_HOOK_FAILED', 1)
s = s.replace('private fun appPickerCreateHooker(packageName: String)', 'private fun appPickerResumeHooker(packageName: String)', 1)

# On resume, run the picker attempt before delegating further so the resumed chooser Activity is already valid,
# but the system picker can immediately take focus. Keep fail-open and one-shot protection.
old = '''    private fun appPickerResumeHooker(packageName: String) = XposedInterface.Hooker { chain ->\n        val result = chain.proceed()\n        runCatching {\n'''
new = '''    private fun appPickerResumeHooker(packageName: String) = XposedInterface.Hooker { chain ->\n        val result = chain.proceed()\n        runCatching {\n'''
assert old in s
s = s.replace(old, new, 1)

# Add trace to prove resume trigger is reached even when launch conditions do not match.
needle = '''            val activity = chain.args.firstOrNull { it is Activity } as? Activity ?: return@runCatching\n            val session = recentSelfChooserSessions[packageName] ?: return@runCatching\n'''
replacement = '''            val activity = chain.args.firstOrNull { it is Activity } as? Activity ?: return@runCatching\n            val session = recentSelfChooserSessions[packageName]\n            diagnostic(\n                "APP_PICKER_BRIDGE_RESUME package=$packageName activity=${activity.componentName?.flattenToShortString() ?: \"-\"} " +\n                    "session=${session?.component ?: \"-\"} kind=${session?.kind ?: \"-\"}"\n            )\n            session ?: return@runCatching\n'''
assert needle in s
s = s.replace(needle, replacement, 1)

p.write_text(s)
