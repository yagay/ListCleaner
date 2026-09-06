from pathlib import Path
import re
p = Path('app/src/main/java/com/yagay/ListCleaner/data/RuleRepository.kt')
s = p.read_text()
if 'fun setHiddenFromApps(packages: Set<String>)' not in s:
    insert = '''
    @Synchronized fun setHiddenFromApps(packages: Set<String>) {
        val self = "com.yagay.ListCleaner"
        val valid = packages.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it != "android" && it != self && it.length <= 255 && it.none { ch -> ch.isWhitespace() || ch.isISOControl() || ch == '|' } }
            .take(2_001).toSet()
        require(valid.size <= 2_000) { "隐藏应用列表数量过多" }
        mutableHiddenFromApps.value = valid
        prefs.edit().putStringSet(KEY_HIDDEN_FROM_APPS, valid).apply()
        mutableRevision.value++
    }

'''
    pattern = r'\n\s*@Synchronized\s*\n\s*fun setDiagnosticMode\(enabled: Boolean\) \{'
    replacement = '\n' + insert + '    @Synchronized\n    fun setDiagnosticMode(enabled: Boolean) {'
    s, count = re.subn(pattern, replacement, s, count=1)
    if count != 1:
        raise SystemExit('setDiagnosticMode anchor not found')
p.write_text(s)
