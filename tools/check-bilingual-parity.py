#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

PAIRS = [
    ("README.md", "README.en.md"),
    ("docs/LOCALIZATION.md", "docs/LOCALIZATION.en.md"),
    ("docs/RELEASE.md", "docs/RELEASE.en.md"),
    ("docs/lsposed/README.md", "docs/lsposed/README.en.md"),
    ("docs/lsposed/SUMMARY", "docs/lsposed/SUMMARY.en"),
]

FORBIDDEN_LEGACY = [
    "README.zh-CN.md",
    "RELEASE_NOTES.zh-CN.md",
    "docs/RELEASE.zh-CN.md",
    "docs/lsposed/README.zh-CN.md",
    "docs/lsposed/SUMMARY.zh-CN",
]

errors = []


def headings(path: Path):
    text = path.read_text(encoding="utf-8")
    return [len(m.group(1)) for m in re.finditer(r"^(#{1,6})\s+.+$", text, re.M)]


def section_markers(path: Path):
    text = path.read_text(encoding="utf-8")
    return re.findall(r"<!--\s*section:([a-z0-9_-]+)\s*-->", text, re.I)

for zh_name, en_name in PAIRS:
    zh = ROOT / zh_name
    en = ROOT / en_name
    if not zh.is_file() or not en.is_file():
        errors.append(f"missing bilingual pair: {zh_name} <-> {en_name}")
        continue
    zh_markers, en_markers = section_markers(zh), section_markers(en)
    if zh_markers or en_markers:
        if zh_markers != en_markers:
            errors.append(f"section markers differ: {zh_name} <-> {en_name}: {zh_markers} != {en_markers}")
    elif headings(zh) != headings(en):
        errors.append(f"heading structure differs: {zh_name} <-> {en_name}")

for name in FORBIDDEN_LEGACY:
    if (ROOT / name).exists():
        errors.append(f"legacy duplicate must not return: {name}")


def resources(directory: Path):
    keys = set()
    for path in directory.glob("*.xml"):
        text = path.read_text(encoding="utf-8")
        for tag, name in re.findall(r"<(string|plurals|string-array)\b[^>]*\bname=\"([^\"]+)\"", text):
            keys.add((tag, name))
    return keys


def string_value(directory: Path, name: str):
    for path in directory.glob("*.xml"):
        text = path.read_text(encoding="utf-8")
        match = re.search(rf"<string\b[^>]*\bname=\"{re.escape(name)}\"[^>]*>(.*?)</string>", text, re.S)
        if match:
            return match.group(1)
    return None


default_dir = ROOT / "app/src/main/res/values"
zh_dir = ROOT / "app/src/main/res/values-zh"
default_keys = resources(default_dir)
zh_keys = resources(zh_dir)
missing_zh = sorted(default_keys - zh_keys)
extra_zh = sorted(zh_keys - default_keys)
if missing_zh:
    errors.append("Chinese resources missing: " + ", ".join(f"{t}:{n}" for t, n in missing_zh))
if extra_zh:
    errors.append("Chinese resources have unmatched keys: " + ", ".join(f"{t}:{n}" for t, n in extra_zh))

# Diagnostic human-readable explanations are sourced through normal Android resources in both languages.
for resource_name in ("diagnostic_readme", "diagnostic_evidence_intro"):
    if string_value(default_dir, resource_name) is None:
        errors.append(f"English {resource_name} resource missing")
    if string_value(zh_dir, resource_name) is None:
        errors.append(f"Chinese {resource_name} resource missing")

diag = (ROOT / "app/src/main/java/com/yagay/ListCleaner/ui/DiagnosticCollector.kt").read_text(encoding="utf-8")
for required in (
    'README.zh-CN.txt', 'README.en.txt',
    'analysis/module-evidence.zh-CN.txt', 'analysis/module-evidence.en.txt',
    'localizedString(context, "zh-CN"', 'localizedString(context, "en"',
    'R.string.diagnostic_readme', 'R.string.diagnostic_evidence_intro',
    'evidence.reportBody()'
):
    if required not in diag:
        errors.append(f"diagnostic bilingual output missing marker: {required}")

# RELEASE_NOTES.md is the single bilingual source used by GitHub Release, Telegram and LSPosed sync.
notes_path = ROOT / "RELEASE_NOTES.md"
if not notes_path.is_file():
    errors.append("missing bilingual release notes: RELEASE_NOTES.md")
else:
    notes = notes_path.read_text(encoding="utf-8").replace("\r", "")
    required = ["\n## 中文\n", "\n## English\n"]
    for marker in required:
        if marker not in notes:
            errors.append(f"RELEASE_NOTES.md missing section: {marker.strip()}")
    if all(marker in notes for marker in required):
        if notes.index("\n## 中文\n") > notes.index("\n## English\n"):
            errors.append("RELEASE_NOTES.md must keep Chinese before English")
        zh = notes.split("\n## 中文\n", 1)[1].split("\n## English\n", 1)[0].strip()
        en = notes.split("\n## English\n", 1)[1].strip()
        zh_bullets = len(re.findall(r"^- ", zh, re.M))
        en_bullets = len(re.findall(r"^- ", en, re.M))
        if zh_bullets != en_bullets:
            errors.append(f"release note bullet counts differ: Chinese={zh_bullets}, English={en_bullets}")
        if zh_bullets == 0:
            errors.append("RELEASE_NOTES.md has no release-note bullets")

telegram = (ROOT / "tools/publish-telegram.py").read_text(encoding="utf-8")
for required in ('marker = "\\n## English\\n"', 'return f"中文\\n{zh}\\n\\nEnglish\\n{en}"', 'Path("RELEASE_NOTES.md")'):
    if required not in telegram:
        errors.append(f"Telegram publisher no longer preserves bilingual release notes: {required}")

sync = (ROOT / "tools/sync-lsposed.py").read_text(encoding="utf-8")
if "RELEASE_NOTES.md" not in sync and "release.get(\"body\"" not in sync and "release.get('body'" not in sync:
    errors.append("LSPosed synchronizer no longer derives notes from the source Release/bilingual release notes")

if errors:
    print("Bilingual parity check failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)

print("Bilingual parity check passed.")
for zh, en in PAIRS:
    print(f"- {zh} <-> {en}")
print(f"- Android resource keys: {len(default_keys)} English / {len(zh_keys)} Chinese")
print("- Release Notes: Chinese first <-> English, shared by GitHub/Telegram/LSPosed")
print("- Diagnostic guides and evidence explanations: Chinese <-> English via Android resources")
