from pathlib import Path

module = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = module.read_text()
s = s.replace('component = ComponentName(MANAGER_PACKAGE, ADAPTIVE_CHOOSER_ACTIVITY)', 'setComponent(ComponentName(MANAGER_PACKAGE, ADAPTIVE_CHOOSER_ACTIVITY))')
s = s.replace('Intent(selector).apply { component = null; setPackage(null) }', 'Intent(selector).apply { setComponent(null); setPackage(null) }')
s = s.replace('Intent(extraIntent).apply { component = null; setPackage(null) }', 'Intent(extraIntent).apply { setComponent(null); setPackage(null) }')
s = s.replace('Intent(value).apply { component = null; setPackage(null) }', 'Intent(value).apply { setComponent(null); setPackage(null) }')
module.write_text(s)

activity = Path('app/src/main/java/com/yagay/ListCleaner/ui/AdaptiveChooserActivity.kt')
a = activity.read_text()
a = a.replace('Intent(supplied).apply { component = null; setPackage(null) }', 'Intent(supplied).apply { setComponent(null); setPackage(null) }')
a = a.replace('component = ComponentName(ai.packageName, ai.name)', 'setComponent(ComponentName(ai.packageName, ai.name))')
activity.write_text(a)
print('fixed adaptive chooser component setters v3')
