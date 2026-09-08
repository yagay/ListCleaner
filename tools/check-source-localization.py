#!/usr/bin/env python3
from pathlib import Path
import re
import sys

PROJECT = Path(__file__).resolve().parents[1]
JAVA_ROOT = PROJECT / "app" / "src" / "main" / "java"
MANIFEST = PROJECT / "app" / "src" / "main" / "AndroidManifest.xml"

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
LATIN_TEXT = r'"(?:[^"\\]|\\.)*[A-Za-z](?:[^"\\]|\\.)*"'

# These patterns intentionally target only APIs/properties that directly expose text
# to users. Technical literals such as package names, MIME types, Intent actions,
# diagnostic field names and protocol tokens are not rejected.
USER_VISIBLE_PATTERNS = (
    ("Compose Text literal", re.compile(rf"\b(?:Text|BasicText)\s*\(\s*({LATIN_TEXT})", re.MULTILINE)),
    ("contentDescription literal", re.compile(rf"\bcontentDescription\s*=\s*({LATIN_TEXT})", re.MULTILINE)),
    ("toast helper literal", re.compile(rf"\btoast\s*\(\s*({LATIN_TEXT})", re.MULTILINE)),
    ("Snackbar literal", re.compile(rf"\bshowSnackbar\s*\(\s*({LATIN_TEXT})", re.MULTILINE)),
    ("setText literal", re.compile(rf"\bsetText\s*\(\s*({LATIN_TEXT})", re.MULTILINE)),
    ("setTitle literal", re.compile(rf"\bsetTitle\s*\(\s*({LATIN_TEXT})", re.MULTILINE)),
    ("setMessage literal", re.compile(rf"\bsetMessage\s*\(\s*({LATIN_TEXT})", re.MULTILINE)),
)

# Developer/runtime exception details are useful in Log.e and diagnostics, but must not
# become user-facing text. Keep the check deliberately focused on known UI sinks so
# developer-only reports such as IntentCatalog ERROR=<ExceptionClass> remain allowed.
EXCEPTION_UI_PATTERNS = (
    ("exception message in toast", re.compile(r"\btoast\s*\([^\n]*(?:failure|it)\.(?:message|localizedMessage)")),
    ("exception message in visible state", re.compile(
        r"\b(?:mutable[A-Za-z0-9_]*(?:Message|Status|Notice)|error|message)\.value\s*=\s*[^\n]*(?:failure|it)\.(?:message|localizedMessage)"
    )),
    ("exception message in UI copy", re.compile(
        r"\bcopy\s*\([^)]*\b(?:error|message)\s*=\s*(?:failure|it)\.(?:message|localizedMessage)", re.DOTALL
    )),
    ("exception message in RuntimeStatus", re.compile(
        r"\bRuntimeStatus\s*\([^)]*\bmessage\s*=\s*(?:failure|it)\.(?:message|localizedMessage)", re.DOTALL
    )),
    ("exception detail passed to localized UI string", re.compile(
        r"\bgetString\s*\([^)]*(?:failure|it)\.(?:message|localizedMessage|javaClass\.simpleName)", re.DOTALL
    )),
    ("framework result message passed to UI", re.compile(
        r"\bgetString\s*\([^)]*\b(?:result|reply)\??\.message\s*\(\)", re.DOTALL
    )),
)

IGNORE_MARKER = "localization:ignore"
violations = []


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def ignored(text: str, offset: int) -> bool:
    start = text.rfind("\n", 0, offset) + 1
    end = text.find("\n", offset)
    if end < 0:
        end = len(text)
    current = text[start:end]
    previous_start = text.rfind("\n", 0, max(0, start - 1)) + 1
    previous = text[previous_start:max(0, start - 1)]
    return IGNORE_MARKER in current or IGNORE_MARKER in previous


for path in sorted(JAVA_ROOT.rglob("*")):
    if path.suffix not in {".kt", ".java"}:
        continue
    text = path.read_text(encoding="utf-8")
    relative = path.relative_to(PROJECT)

    # Chinese/Japanese/Korean ideographs must never live directly in production
    # Java/Kotlin. This broad check also protects non-Compose user-visible paths.
    for number, line in enumerate(text.splitlines(), 1):
        if CJK.search(line) and IGNORE_MARKER not in line:
            violations.append((relative, number, "CJK source text", line.strip()))

    # English source text is allowed for technical/internal data, so only flag
    # literals passed directly to known user-facing UI APIs.
    for label, pattern in USER_VISIBLE_PATTERNS + EXCEPTION_UI_PATTERNS:
        for match in pattern.finditer(text):
            if ignored(text, match.start()):
                continue
            number = line_number(text, match.start())
            line = text.splitlines()[number - 1].strip()
            violations.append((relative, number, label, line))

if MANIFEST.is_file():
    text = MANIFEST.read_text(encoding="utf-8")
    # Android component labels/titles must reference resources. Resource, theme,
    # class and permission attributes are intentionally outside this check.
    for match in re.finditer(r'android:(label|title|description)\s*=\s*"([^"]+)"', text):
        value = match.group(2).strip()
        if value.startswith("@"):
            continue
        number = line_number(text, match.start())
        violations.append((MANIFEST.relative_to(PROJECT), number,
                           f"Manifest android:{match.group(1)} literal", value))

if violations:
    print("Localization source check failed. User-visible text must live in Android string resources, and developer exception details must not leak into UI.")
    print(f"Use stringResource(R.string.*) in Compose or Context.getString(R.string.*) elsewhere. "
          f"Log full exceptions separately with Log.e. Only stable machine/internal text may use // {IGNORE_MARKER} when a false positive is unavoidable.")
    for path, number, label, line in violations:
        print(f"{path}:{number}: [{label}] {line}")
    sys.exit(1)

print("Localization source check passed: no CJK source text, hardcoded high-risk UI literals, or developer exception details in user-facing sinks found.")
