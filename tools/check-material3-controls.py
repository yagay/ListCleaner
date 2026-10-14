#!/usr/bin/env python3
"""Guard the single Material 3 navigation/button contract against divergent widgets."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1] / "app/src/main/java/com/yagay/ListCleaner/ui"
direct_button = re.compile(
    r"(?<![A-Za-z0-9_])(?:Button|OutlinedButton|TextButton|IconButton|FilterChip)\s*\("
)
errors = []
for file in sorted(root.glob("*.kt")):
    source = file.read_text(encoding="utf-8")
    if "top.yukonga.miuix" in source or "UiThemeStyle" in source:
        errors.append(f"{file.name}: legacy dual-theme code is not allowed")
    if file.name == "AdaptiveWidgets.kt":
        continue
    for line_no, line in enumerate(source.splitlines(), 1):
        code = line.split("//", 1)[0]
        if direct_button.search(code):
            errors.append(f"{file.name}:{line_no}: use the shared Adaptive material control")
        if "Icons.AutoMirrored.Rounded.ArrowBack" in code:
            errors.append(f"{file.name}:{line_no}: use AdaptiveBackButton")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Material 3 shared navigation and button contract OK")
