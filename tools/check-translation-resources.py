#!/usr/bin/env python3
from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
DEFAULT_DIR = RES / "values"
LOCALES_CONFIG = RES / "xml" / "locales_config.xml"
FORMAT_RE = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[tT]?[a-zA-Z%]")
RESOURCE_TAGS = {"string", "plurals", "string-array"}


def text_of(element: ET.Element) -> str:
    return "".join(element.itertext())


def placeholders(text: str) -> Counter[str]:
    values = [item for item in FORMAT_RE.findall(text or "") if item != "%%"]
    return Counter(values)


def load_dir(path: Path):
    resources = {}
    duplicates = []
    parse_errors = []
    for xml_path in sorted(path.glob("*.xml")):
        try:
            root = ET.parse(xml_path).getroot()
        except ET.ParseError as exc:
            parse_errors.append(f"{xml_path.relative_to(ROOT)}: {exc}")
            continue
        if root.tag != "resources":
            continue
        for element in root:
            if element.tag not in RESOURCE_TAGS:
                continue
            name = element.get("name")
            if not name:
                continue
            key = (element.tag, name)
            if key in resources:
                duplicates.append(f"{path.name}: duplicate {element.tag} {name}")
            resources[key] = {
                "element": element,
                "file": xml_path,
                "text": text_of(element),
                "translatable": element.get("translatable", "true").lower() != "false",
            }
    return resources, duplicates, parse_errors


def locale_dirs():
    return sorted(
        path for path in RES.iterdir()
        if path.is_dir() and path.name.startswith("values-") and not path.name.startswith("values-night")
    )


def declared_locales():
    try:
        root = ET.parse(LOCALES_CONFIG).getroot()
    except (ET.ParseError, FileNotFoundError) as exc:
        return set(), [f"{LOCALES_CONFIG.relative_to(ROOT)}: {exc}"]
    android_name = "{http://schemas.android.com/apk/res/android}name"
    return {item.get(android_name) for item in root if item.get(android_name)}, []


def qualifier_to_locale(name: str):
    qualifier = name.removeprefix("values-")
    # Android qualifiers such as zh-rCN are represented as BCP-47 zh-CN in locale-config.
    return qualifier.replace("-r", "-")


def main() -> int:
    default, errors, parse_errors = load_dir(DEFAULT_DIR)
    failures = list(errors) + list(parse_errors)

    non_translatable = {key for key, value in default.items() if not value["translatable"]}
    declared, locale_config_errors = declared_locales()
    failures.extend(locale_config_errors)

    print(f"Default resources: {len(default)}")

    for path in locale_dirs():
        translated, duplicates, parse_errors = load_dir(path)
        failures.extend(duplicates)
        failures.extend(parse_errors)

        unknown = sorted(set(translated) - set(default))
        for tag, name in unknown:
            failures.append(f"{path.name}: unknown {tag} {name} (missing from default values/)")

        forbidden = sorted(set(translated) & non_translatable)
        for tag, name in forbidden:
            failures.append(f"{path.name}: {tag} {name} overrides translatable=false resource")

        for key in sorted(set(default) & set(translated)):
            source = default[key]
            target = translated[key]
            source_placeholders = placeholders(source["text"])
            target_placeholders = placeholders(target["text"])
            if source_placeholders != target_placeholders:
                tag, name = key
                failures.append(
                    f"{path.name}: placeholder mismatch for {tag} {name}: "
                    f"source={dict(source_placeholders)} translation={dict(target_placeholders)}"
                )

        translated_count = len(set(default) & set(translated))
        missing_count = len(set(default) - set(translated))
        coverage = 100.0 if not default else translated_count * 100.0 / len(default)
        print(f"{path.name}: {translated_count}/{len(default)} ({coverage:.1f}%), missing {missing_count}")

        locale = qualifier_to_locale(path.name)
        if locale not in declared:
            failures.append(f"{path.name}: locale {locale} is not declared in res/xml/locales_config.xml")

    for locale in sorted(declared):
        if locale == "en":
            continue
        expected = "values-" + locale.replace("-", "-r", 1) if "-" in locale else "values-" + locale
        simple = RES / ("values-" + locale)
        android_legacy = RES / expected
        if not simple.exists() and not android_legacy.exists():
            failures.append(f"locales_config.xml declares {locale}, but no matching values directory exists")

    if failures:
        print("\nTranslation resource validation failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Translation resource validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
