#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "java"
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")

violations = []
for path in sorted(ROOT.rglob("*")):
    if path.suffix not in {".kt", ".java"}:
        continue
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if CJK.search(line):
            violations.append((path.relative_to(ROOT.parents[3]), number, line.strip()))

if violations:
    print("CJK text found in production Java/Kotlin source. User-visible language must live in Android resources; internal diagnostics should use stable language-neutral tokens.")
    for path, number, line in violations:
        print(f"{path}:{number}: {line}")
    sys.exit(1)

print("Localization source check passed: no CJK text in app/src/main/java.")
