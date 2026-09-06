from pathlib import Path

path = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
text = path.read_text()
old = '''        val confidence = when {\n            score >= 6 && hits >= 2 -> "HIGH"\n            score >= 6 -> "MEDIUM"\n            else -> "LOW"\n        }\n'''
new = '''        val confidence = when {\n            score >= 7 && hits >= 1 -> "HIGH"\n            score >= 6 && hits >= 2 -> "HIGH"\n            score >= 6 -> "MEDIUM"\n            else -> "LOW"\n        }\n'''
if old not in text:
    raise SystemExit('confidence block not found')
text = text.replace(old, new, 1)
path.write_text(text)
print('chooser high-confidence threshold patched')
