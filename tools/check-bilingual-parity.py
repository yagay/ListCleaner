#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

PAIRS = [
    ("README.md", "README.en.md"),
    ("docs/LOCALIZATION.md", "docs/LOCALIZATION.en.md"),
    ("docs/lsposed/README.md", "docs/lsposed/README.en.md"),
    ("docs/lsposed/SUMMARY", "docs/lsposed/SUMMARY.en"),
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

# Android resource keys must match exactly for the two maintained languages.
def resources(directory: Path):
    keys = set()
    for path in directory.glob("*.xml"):
        text = path.read_text(encoding="utf-8")
        for tag, name in re.findall(r"<(string|plurals|string-array)\b[^>]*\bname=\"([^\"]+)\"", text):
            keys.add((tag, name))
    return keys

default_keys = resources(ROOT / "app/src/main/res/values")
zh_keys = resources(ROOT / "app/src/main/res/values-zh")
missing_zh = sorted(default_keys - zh_keys)
extra_zh = sorted(zh_keys - default_keys)
if missing_zh:
    errors.append("Chinese resources missing: " + ", ".join(f"{t}:{n}" for t, n in missing_zh))
if extra_zh:
    errors.append("Chinese resources have unmatched keys: " + ", ".join(f"{t}:{n}" for t, n in extra_zh))

# Diagnostic package must ship paired human-readable guides while machine paths stay stable.
diag = (ROOT / "app/src/main/java/com/yagay/ListCleaner/ui/DiagnosticCollector.kt").read_text(encoding="utf-8")
for required in ('README.zh-CN.txt', 'README.en.txt', 'readmeZh()', 'readmeEn()'):
    if required not in diag:
        errors.append(f"diagnostic bilingual guide missing marker: {required}")

if errors:
    print("Bilingual parity check failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)

print("Bilingual parity check passed.")
for zh, en in PAIRS:
    print(f"- {zh} <-> {en}")
print(f"- Android resource keys: {len(default_keys)} English / {len(zh_keys)} Chinese")
print("- Diagnostic guides: README.zh-CN.txt <-> README.en.txt")
