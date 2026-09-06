from pathlib import Path
p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()
s = s.replace('if (target.action == null) target.action = template.action ?: if (template.kind == IntentKind.OPEN || template.kind == IntentKind.BROWSER) Intent.ACTION_VIEW else target.action', 'if (target.action == null) target.setAction(template.action ?: if (template.kind == IntentKind.OPEN || template.kind == IntentKind.BROWSER) Intent.ACTION_VIEW else null)')
s = s.replace('if (target.type == null && template.mime != null) target.type = template.mime', 'if (target.type == null && template.mime != null) target.setType(template.mime)')
s = s.replace('if (template.mime != null) setDataAndType(uri, template.mime) else data = uri', 'if (template.mime != null) setDataAndType(uri, template.mime) else setData(uri)')
p.write_text(s)
print('fixed adaptive chooser redirect intent setters v2')
