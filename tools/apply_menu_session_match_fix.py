from pathlib import Path

p=Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s=p.read_text()
old='''                val cls = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(c.packageName, c.className, null)
                val selected = "${kind.name}|${c.packageName}|$cls" in snapshot.configured
                !snapshot.displayMode.includes(selected, snapshot.hasSelection(kind))
'''
new='''                val rawClass = c.className
                val expandedClass = when {
                    rawClass.startsWith(".") -> c.packageName + rawClass
                    '.' !in rawClass -> "${c.packageName}.$rawClass"
                    else -> rawClass
                }
                val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(c.packageName, expandedClass, null)
                val candidateIds = linkedSetOf(
                    "${kind.name}|${c.packageName}|$rawClass",
                    "${kind.name}|${c.packageName}|$expandedClass",
                    "${kind.name}|${c.packageName}|$canonicalClass",
                )
                val selected = candidateIds.any(snapshot.configured::contains)
                if (snapshot.diagnostic) diagnostic(
                    "APP_MENU_MODEL_DECISION package=$sourcePackage kind=$kind target=${c.packageName}/$rawClass " +
                        "expanded=$expandedClass canonical=$canonicalClass selected=$selected"
                )
                !snapshot.displayMode.includes(selected, snapshot.hasSelection(kind))
'''
if old not in s: raise SystemExit('match block not found')
s=s.replace(old,new,1)
p.write_text(s)
print('patched menu session rule matching')
