from pathlib import Path
import re

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

# Remove obsolete APP-layer hot-reload references.
s = s.replace('                    "$HOOK_ID-app" -> Layer.APP\n', '')
s = s.replace('                    Layer.APP -> installApplicationClientHooks(it, baseProcess)\n', '')

# Remove APP enum member if present.
s = re.sub(r'(private enum class Layer\s*\{[^}]*)\bAPP,?\s*', r'\1', s, flags=re.S)

# Remove obsolete app-process virtual visibility functions by balanced braces.
def remove_function(text: str, name: str) -> str:
    m = re.search(r'\n\s*private fun\s+' + re.escape(name) + r'\s*\(', text)
    if not m:
        return text
    start = m.start()
    brace = text.find('{', m.start())
    if brace < 0:
        raise RuntimeError(f'no opening brace for {name}')
    depth = 0
    i = brace
    while i < len(text):
        c = text[i]
        if c == '{': depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                while end < len(text) and text[end] in ' \t': end += 1
                if end < len(text) and text[end] == '\n': end += 1
                return text[:start] + '\n' + text[end:]
        i += 1
    raise RuntimeError(f'unbalanced function {name}')

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

# Drop imports that belonged only to the removed virtual-PM layer when no longer referenced.
for fqcn, simple in [
    ('android.content.pm.ApplicationInfo', 'ApplicationInfo'),
    ('android.content.pm.PackageInfo', 'PackageInfo'),
    ('android.content.pm.PackageManager', 'PackageManager'),
    ('android.content.ComponentName', 'ComponentName'),
]:
    body = re.sub(r'^import .*\n', '', s, flags=re.M)
    if not re.search(r'\b' + re.escape(simple) + r'\b', body):
        s = s.replace(f'import {fqcn}\n', '')

# Guardrails: only system/resolver query filtering + HMA-style system visibility may remain.
for forbidden in [
    'Layer.APP', 'HOOK_ID-app', 'installApplicationClientHooks', 'installVirtualComponentHooks',
    'PROCESS_VISIBILITY_', 'VIRTUAL_ACTIVITY_HOOK_ID', 'VIRTUAL_STATE_HOOK_ID',
    'VIRTUAL_APPLICATION_HOOK_ID', 'VIRTUAL_PACKAGE_HOOK_ID', 'VIRTUAL_INSTALLED_APPS_HOOK_ID',
    'VIRTUAL_INSTALLED_PACKAGES_HOOK_ID', 'VIRTUAL_RESOLVE_HOOK_ID', 'VIRTUAL_OPTIONS_HOOK_ID'
]:
    if forbidden in s:
        raise RuntimeError(f'leftover obsolete token: {forbidden}')

required = ['AppsFilterImpl', 'shouldFilterApplication', 'SYSTEM_VISIBILITY_FILTER', 'hiddenFromApps']
for token in required:
    if token not in s:
        raise RuntimeError(f'missing HMA visibility token: {token}')

p.write_text(s)
