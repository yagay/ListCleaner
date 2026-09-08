#!/usr/bin/env python3
"""Publish a verified GitHub Release APK to the List Cleaner Telegram channel.

The bot token is read only from TELEGRAM_BOT_TOKEN. A marker asset stores the
Release-body digest after Telegram confirms delivery, so reruns do not resend
identical release notes but can republish when the published notes change.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.error
import urllib.request
import uuid

SOURCE = "yagay/ListCleaner"
DEFAULT_CHAT_ID = "@LISTCLEANER"
MARKER_NAME = "telegram-published-v2.json"


def gh(*args):
    env = dict(os.environ, GH_TOKEN=os.environ["GITHUB_TOKEN"], GH_PROMPT_DISABLED="1")
    result = subprocess.run(["gh", *args], text=True, capture_output=True, env=env, timeout=180)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "GitHub operation failed")
    return result.stdout


def checked_tag(value):
    if value and not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", value):
        raise ValueError("Release tag must be a stable version such as v1.6.4")
    return value


def release_info(tag):
    endpoint = f"repos/{SOURCE}/releases/tags/{tag}" if tag else f"repos/{SOURCE}/releases/latest"
    return json.loads(gh("api", endpoint))


def find_asset(release, name):
    matches = [asset for asset in release.get("assets", []) if asset.get("name") == name and asset.get("state") == "uploaded"]
    if len(matches) > 1:
        raise ValueError(f"Duplicate Release asset: {name}")
    return matches[0] if matches else None


def _compact(text, limit):
    text = re.sub(r"\n{3,}", "\n\n", (text or "").replace("\r", "").strip())
    if len(text) <= limit:
        return text
    return text[: limit - 1].rstrip() + "…"


def compact_notes(body, limit=620):
    """Keep both languages visible; RELEASE_NOTES.md is Chinese first, English second."""
    text = (body or "").replace("\r", "").strip()
    marker = "\n## English\n"
    if marker not in text:
        return _compact(text, limit)
    chinese, english = text.split(marker, 1)
    chinese = re.sub(r"^#.*?\n+", "", chinese, count=1).strip()
    chinese = re.sub(r"^## 中文\n+", "", chinese, count=1).strip()
    english = english.strip()
    # Telegram captions are short; reserve space for both sections while keeping Chinese first.
    zh = _compact(chinese, 350)
    en = _compact(english, 230)
    return f"中文\n{zh}\n\nEnglish\n{en}"


def make_caption(version, body, release_url):
    notes = compact_notes(body)
    parts = [f"📢 List Cleaner {version} 发布 / Release"]
    if notes:
        parts += ["", notes]
    parts += [
        "",
        f"GitHub Release：{release_url}",
        "LSPosed 官方仓库 / Official repository：https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/releases",
        "",
        "#ListCleaner #LSPosed",
    ]
    caption = "\n".join(parts)
    return caption[:900]


def multipart(fields, file_field, filename, payload):
    boundary = "----ListCleaner" + uuid.uuid4().hex
    chunks = []
    for key, value in fields.items():
        chunks += [
            f"--{boundary}\r\n".encode(),
            f'Content-Disposition: form-data; name="{key}"\r\n\r\n'.encode(),
            str(value).encode("utf-8"),
            b"\r\n",
        ]
    chunks += [
        f"--{boundary}\r\n".encode(),
        f'Content-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'.encode(),
        b"Content-Type: application/vnd.android.package-archive\r\n\r\n",
        payload,
        b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ]
    return boundary, b"".join(chunks)


def send_document(token, chat_id, apk, caption):
    boundary, body = multipart({"chat_id": chat_id, "caption": caption}, "document", apk.name, apk.read_bytes())
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendDocument",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = ""
        try:
            payload = json.loads(error.read().decode("utf-8"))
            detail = payload.get("description", "")
        except Exception:
            pass
        message = f"Telegram upload failed: HTTP {error.code}"
        if detail:
            message += f" - {detail}"
        raise RuntimeError(message) from error
    except Exception as error:
        raise RuntimeError(f"Telegram upload failed: {error}") from error
    if not result.get("ok"):
        raise RuntimeError(f"Telegram rejected the release message: {result.get('description', 'unknown error')}")
    return result["result"]["message_id"]


def main():
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    if not token:
        raise ValueError("TELEGRAM_BOT_TOKEN is missing")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip() or DEFAULT_CHAT_ID
    requested = checked_tag(os.environ.get("SOURCE_TAG", "").strip())
    release = release_info(requested)
    tag = checked_tag(release.get("tag_name", ""))
    if not tag or release.get("draft") or release.get("prerelease"):
        raise ValueError("Only a published stable Release can be sent to Telegram")

    version = tag[1:]
    apk_name = f"ListCleaner-{version}-release.apk"
    if not find_asset(release, apk_name):
        raise ValueError(f"Release asset is missing: {apk_name}")

    release_body = release.get("body", "")
    tracked_notes_path = Path("RELEASE_NOTES.md")
    if tracked_notes_path.is_file():
        tracked_notes = tracked_notes_path.read_text(encoding="utf-8").strip()
        if tracked_notes and not release_body.strip().startswith(tracked_notes):
            raise ValueError("Published GitHub Release notes are not refreshed from RELEASE_NOTES.md yet")

    body_sha256 = hashlib.sha256(release_body.encode("utf-8")).hexdigest()
    marker_asset = find_asset(release, MARKER_NAME)

    with tempfile.TemporaryDirectory(prefix="listcleaner-telegram-") as directory:
        directory = Path(directory)
        if marker_asset:
            gh("release", "download", tag, "--repo", SOURCE, "--dir", str(directory), "--pattern", MARKER_NAME)
            marker_path = directory / MARKER_NAME
            try:
                marker_data = json.loads(marker_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                marker_data = {}
            if marker_data.get("body_sha256") == body_sha256:
                print(f"Telegram already published for {tag} with the current Release notes; skipping duplicate send.")
                return

        gh("release", "download", tag, "--repo", SOURCE, "--dir", str(directory), "--pattern", apk_name)
        apk = directory / apk_name
        if not apk.is_file() or apk.stat().st_size == 0:
            raise ValueError("Downloaded APK is empty")
        caption = make_caption(version, release_body, release["html_url"])
        message_id = send_document(token, chat_id, apk, caption)
        marker = directory / MARKER_NAME
        marker.write_text(json.dumps({
            "tag": tag,
            "version": version,
            "chat_id": chat_id,
            "message_id": message_id,
            "body_sha256": body_sha256,
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        gh("release", "upload", tag, "--repo", SOURCE, str(marker), "--clobber")
        print(f"Published {tag} to Telegram {chat_id}; message_id={message_id}; body_sha256={body_sha256}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(str(error))
