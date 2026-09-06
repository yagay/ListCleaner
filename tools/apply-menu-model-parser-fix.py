from pathlib import Path

path = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
text = path.read_text()
old = '''    private fun extractMenuModelComponent(value: Any?): MenuModelComponent? {
        value ?: return null
        when (value) {
            is ResolveInfo -> return value.activityInfo?.let { MenuModelComponent(it.packageName,it.name) }
            is ActivityInfo -> return MenuModelComponent(value.packageName,value.name)
            is ComponentName -> return MenuModelComponent(value.packageName,value.className)
        }
        val strings=mutableListOf<String>()
        allInstanceFields(value.javaClass).asSequence().filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.take(APP_MENU_MAX_MODEL_FIELDS).forEach { f ->
            runCatching { f.isAccessible=true; f.get(value) as? String }.getOrNull()?.let(strings::add)
        }
        if (strings.none { it.startsWith("android.intent.action.") }) return null
        val vals=strings.filterNot { it.startsWith("android.intent.action.") }
        val cls=vals.firstOrNull { it.startsWith(".") || it.substringAfterLast('.').any(Char::isUpperCase) } ?: return null
        val pkg=vals.firstOrNull { it!=cls && looksLikePackageName(it) && it.substringAfterLast('.').all { ch -> ch.isLowerCase() || ch.isDigit() || ch=='_' } }
            ?: vals.firstOrNull { it!=cls && looksLikePackageName(it) }
        return pkg?.let { MenuModelComponent(it,cls) }
    }
'''
new = '''    private fun extractMenuModelComponent(value: Any?): MenuModelComponent? {
        value ?: return null
        when (value) {
            is ResolveInfo -> return value.activityInfo?.let { MenuModelComponent(it.packageName, it.name) }
            is ActivityInfo -> return MenuModelComponent(value.packageName, value.name)
            is ComponentName -> return MenuModelComponent(value.packageName, value.className)
        }

        val strings = mutableListOf<Pair<String, String>>()
        allInstanceFields(value.javaClass).asSequence()
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .take(APP_MENU_MAX_MODEL_FIELDS)
            .forEach { field ->
                runCatching {
                    field.isAccessible = true
                    field.get(value) as? String
                }.getOrNull()?.takeIf { it.isNotBlank() }?.let { strings += field.name to it }
            }

        if (strings.none { (_, s) -> s.startsWith("android.intent.action.") }) return null
        val values = strings.map { it.second }.filterNot { it.startsWith("android.intent.action.") }

        fun looksLikeStrictPackage(s: String): Boolean {
            if (!looksLikePackageName(s) || s.startsWith(".") || '/' in s || ':' in s) return false
            val segments = s.split('.')
            if (segments.size < 2 || segments.any { it.isBlank() }) return false
            return segments.all { segment ->
                segment.firstOrNull()?.let { it.isLowerCase() || it == '_' } == true &&
                    segment.all { ch -> ch.isLowerCase() || ch.isDigit() || ch == '_' }
            }
        }

        fun looksLikeActivityClass(s: String): Boolean {
            if (s.isBlank() || s.startsWith("android.intent.action.") || '/' in s || ':' in s || ' ' in s) return false
            if (s.startsWith(".")) {
                val tail = s.substringAfterLast('.')
                return tail.isNotBlank() && tail.any(Char::isUpperCase)
            }
            if ('.' !in s) return false
            val tail = s.substringAfterLast('.')
            if (tail.isBlank()) return false
            return tail.any(Char::isUpperCase)
        }

        val classCandidates = values.filter(::looksLikeActivityClass)
        if (classCandidates.isEmpty()) return null

        val packageCandidates = values.filter(::looksLikeStrictPackage)
        if (packageCandidates.isEmpty()) return null

        val className = classCandidates.firstOrNull { cls ->
            packageCandidates.any { pkg -> cls == pkg || cls.startsWith("$pkg.") }
        } ?: classCandidates.first()

        val packageName = packageCandidates
            .filter { pkg -> className.startsWith("$pkg.") }
            .maxByOrNull(String::length)
            ?: packageCandidates.first()

        if (snapshot.diagnostic) {
            diagnostic(
                "APP_MENU_MODEL_PARSE type=${value.javaClass.name} package=$packageName class=$className " +
                    "strings=[${strings.joinToString(",") { (name, s) -> "$name=$s" }}]"
            )
        }
        return MenuModelComponent(packageName, className)
    }
'''
if old not in text:
    raise SystemExit('target parser block not found')
path.write_text(text.replace(old, new))
