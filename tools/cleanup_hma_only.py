from pathlib import Path
import re

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

# Remove obsolete APP-layer hot-reload references left behind by the first system visibility migration.
s = s.replace('                    "$HOOK_ID-app" -> Layer.APP\n', '')
s = s.replace('                    Layer.APP -> installApplicationClientHooks(it, baseProcess)\n', '')
s = s.replace('    private enum class Layer { SYSTEM, RESOLVER, APP }', '    private enum class Layer { SYSTEM, RESOLVER }')

# Remove the now-dead APP-only branch inside query processing.
def remove_if_block(text: str, marker: str) -> str:
    pos = text.find(marker)
    if pos < 0:
        return text
    brace = text.find('{', pos)
    if brace < 0:
        raise RuntimeError(f'no opening brace for {marker}')
    depth = 0
    i = brace
    while i < len(text):
        if text[i] == '{': depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                while end < len(text) and text[end] in ' \t': end += 1
                if end < len(text) and text[end] == '\n': end += 1
                return text[:pos] + text[end:]
        i += 1
    raise RuntimeError(f'unbalanced if block {marker}')

s = remove_if_block(s, '        if (layer == Layer.APP) {')
s = s.replace('        return layer == Layer.APP && current.hasPackageSelection(kind, activity.packageName)\n', '        return false\n')
s = s.replace('                val packageFallback = layer == Layer.APP && !exactSelected && current.hasPackageSelection(kind, activity.packageName)\n', '                val packageFallback = false\n')

# Remove a private function/declaration, supporting both block and expression bodies.
def remove_function(text: str, name: str) -> str:
    m = re.search(r'\n\s*private fun\s+' + re.escape(name) + r'\s*\(', text)
    if not m:
        return text
    start = m.start()
    next_decl = text.find('\n\n    private ', m.end())
    if next_decl < 0:
        next_decl = text.find('\n\n    companion object', m.end())
    if next_decl < 0:
        raise RuntimeError(f'cannot find declaration boundary for {name}')
    segment = text[m.start():next_decl]
    if '{' in segment and segment.count('{') != segment.count('}'):
        raise RuntimeError(f'unbalanced declaration {name}')
    return text[:start] + '\n' + text[next_decl:]

obsolete = [
    'installApplicationClientHooks',
    'installVirtualComponentHooks',
    'processVisibilityHidden',
    'virtualActivityInfoHooker',
    'virtualComponentStateHooker',
    'virtualApplicationInfoHooker',
    'virtualPackageInfoHooker',
    'virtualInstalledApplicationsHooker',
    'virtualInstalledPackagesHooker',
    'virtualResolveActivityHooker',
    'virtualQueryIntentActivityOptionsHooker',
    'isActivityInfoMethod',
    'isComponentEnabledSettingMethod',
    'isApplicationInfoMethod',
    'isPackageInfoMethod',
    'isInstalledApplicationsMethod',
    'isInstalledPackagesMethod',
    'isResolveActivityMethod',
    'isQueryIntentActivityOptionsMethod',
]
for name in obsolete:
    s = remove_function(s, name)

# Remove stale virtual hook constants.
s = re.sub(r'^\s*const val VIRTUAL_[A-Z0-9_]+\s*=.*\n', '', s, flags=re.M)

# Drop imports that belonged only to the removed app-process PM virtualization layer.
for fqcn, simple in [
    ('android.content.pm.ApplicationInfo', 'ApplicationInfo'),
    ('android.content.pm.PackageInfo', 'PackageInfo'),
    ('android.content.pm.PackageManager', 'PackageManager'),
    ('android.content.ComponentName', 'ComponentName'),
]:
    body = re.sub(r'^import .*\n', '', s, flags=re.M)
    if not re.search(r'\b' + re.escape(simple) + r'\b', body):
        s = s.replace(f'import {fqcn}\n', '')

# Guardrails: keep normal system/resolver component filtering and HMA-style system visibility,
# but remove the abandoned third-party process virtualization path.
for forbidden in [
    'Layer.APP', 'HOOK_ID-app', 'installApplicationClientHooks', 'installVirtualComponentHooks',
    'PROCESS_VISIBILITY_', 'VIRTUAL_ACTIVITY_HOOK_ID', 'VIRTUAL_STATE_HOOK_ID',
    'VIRTUAL_APPLICATION_HOOK_ID', 'VIRTUAL_PACKAGE_HOOK_ID', 'VIRTUAL_INSTALLED_APPS_HOOK_ID',
    'VIRTUAL_INSTALLED_PACKAGES_HOOK_ID', 'VIRTUAL_RESOLVE_HOOK_ID', 'VIRTUAL_OPTIONS_HOOK_ID'
]:
    if forbidden in s:
        raise RuntimeError(f'leftover obsolete token: {forbidden}')

for token in ['AppsFilterImpl', 'shouldFilterApplication', 'SYSTEM_VISIBILITY_FILTER', 'hiddenFromApps']:
    if token not in s:
        raise RuntimeError(f'missing HMA visibility token: {token}')

p.write_text(s)
