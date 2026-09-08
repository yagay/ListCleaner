#!/usr/bin/env python3
"""Publish a verified GitHub Release APK to the List Cleaner Telegram channel.

The bot token is read only from TELEGRAM_BOT_TOKEN. A marker asset stores the
Release-body/presentation digest and Telegram message id. Identical reruns are
skipped; when release notes or the presentation format change, the previous
Telegram post is deleted first and replaced with the refreshed APK post.
"""
import hashlib
import html
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid

SOURCE = "yagay/ListCleaner"
DEFAULT_CHAT_ID = "@LISTCLEANER"
MARKER_NAME = "telegram-published-v2.json"
PUBLISH_FORMAT_VERSION = "expandable-caption-v1"
CAPTION_VISIBLE_LIMIT = 960


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
    text = re.sub(r"\s+", " ", (text or "").replace("\r", " ").strip())
    if len(text) <= limit:
        return text
    return text[: limit - 1].rstrip(" ,，;；。.") + "…"


def chinese_feature_bullets(body):
    """Return every Chinese feature-summary bullet before English/full raw changelog."""
    text = (body or "").replace("\r", "")
    if "## 中文" in text:
        text = text.split("## 中文", 1)[1]
    for marker in ("\n---\n", "\n## English\n", "\n## 完整变更 / Full changelog\n"):
        if marker in text:
            text = text.split(marker, 1)[0]
    bullets = []
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("- "):
            bullets.append(line[2:].strip())
    return bullets


def compact_feature_notes(body, budget):
    """Keep all feature bullets represented within Telegram's one-caption limit."""
    bullets = chinese_feature_bullets(body)
    if not bullets:
        text = re.sub(r"^#.*?\n+", "", (body or "").strip(), count=1)
        return _compact(text, budget)

    # Preserve every functional item by compacting each bullet rather than dropping later ones.
    per_item = max(28, min(62, (budget - max(0, len(bullets) - 1)) // max(1, len(bullets)) - 2))
    lines = [f"• {_compact(item, per_item)}" for item in bullets]
    notes = "\n".join(lines)
    if len(notes) <= budget:
        return notes

    # Last-resort rebalance for unusually large release summaries.
    per_item = max(18, per_item - 8)
    return "\n".join(f"• {_compact(item, per_item)}" for item in bullets)


def make_caption(version, body, release_url):
    title = f"📢 <b>List Cleaner {html.escape(version)} 发布 / Release</b>"
    footer = (
        f'\n\n<a href="{html.escape(release_url, quote=True)}">GitHub Release · 完整原始变更</a>'
        "\n#ListCleaner #LSPosed"
    )

    # Telegram document captions are limited; expandable formatting changes presentation,
    # not the hard caption limit. Reserve room for title/footer and keep every feature item.
    visible_overhead = len(re.sub(r"<[^>]+>", "", title + footer)) + 28
    notes_budget = max(240, CAPTION_VISIBLE_LIMIT - visible_overhead)
    notes = compact_feature_notes(body, notes_budget)
    escaped_notes = html.escape(notes)
    caption = (
        title
        + "\n\n<b>更新日志 / Changelog</b>"
        + f"\n<blockquote expandable>{escaped_notes}</blockquote>"
        + footer
    )
    return caption


def telegram_call(token, method, fields):
    payload = urllib.parse.urlencode(fields).encode("utf-8")
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/{method}",
        data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = ""
        try:
            body = json.loads(error.read().decode("utf-8"))
            detail = body.get("description", "")
        except Exception:
            pass
        message = f"Telegram {method} failed: HTTP {error.code}"
        if detail:
            message += f" - {detail}"
        raise RuntimeError(message) from error
    if not result.get("ok"):
        raise RuntimeError(f"Telegram {method} rejected the request: {result.get('description', 'unknown error')}")
    return result.get("result")


def delete_message(token, chat_id, message_id):
    if not message_id:
        return False
    try:
        telegram_call(token, "deleteMessage", {"chat_id": chat_id, "message_id": int(message_id)})
        print(f"Deleted previous Telegram release post: chat={chat_id}; message_id={message_id}")
        return True
    except RuntimeError as error:
        text = str(error).lower()
        if "message to delete not found" in text or "message can't be deleted" in text:
            print(f"Previous Telegram post could not be deleted ({error}); continuing with replacement send.")
            return False
        raise


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
    fields = {
        "chat_id": chat_id,
        "caption": caption,
        "parse_mode": "HTML",
    }
    boundary, body = multipart(fields, "document", apk.name, apk.read_bytes())
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

    presentation_material = PUBLISH_FORMAT_VERSION + "\0" + release_body
    body_sha256 = hashlib.sha256(presentation_material.encode("utf-8")).hexdigest()
    marker_asset = find_asset(release, MARKER_NAME)

    with tempfile.TemporaryDirectory(prefix="listcleaner-telegram-") as directory:
        directory = Path(directory)
        marker_data = {}
        if marker_asset:
            gh("release", "download", tag, "--repo", SOURCE, "--dir", str(directory), "--pattern", MARKER_NAME)
            marker_path = directory / MARKER_NAME
            try:
                marker_data = json.loads(marker_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                marker_data = {}
            if marker_data.get("body_sha256") == body_sha256:
                print(f"Telegram already published for {tag} with the current Release notes and presentation; skipping duplicate send.")
                return

        gh("release", "download", tag, "--repo", SOURCE, "--dir", str(directory), "--pattern", apk_name)
        apk = directory / apk_name
        if not apk.is_file() or apk.stat().st_size == 0:
            raise ValueError("Downloaded APK is empty")

        old_message_id = marker_data.get("message_id")
        old_chat_id = marker_data.get("chat_id") or chat_id
        if old_message_id:
            delete_message(token, old_chat_id, old_message_id)

        caption = make_caption(version, release_body, release["html_url"])
        message_id = send_document(token, chat_id, apk, caption)
        marker = directory / MARKER_NAME
        marker.write_text(json.dumps({
            "tag": tag,
            "version": version,
            "chat_id": chat_id,
            "message_id": message_id,
            "body_sha256": body_sha256,
            "presentation": PUBLISH_FORMAT_VERSION,
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        gh("release", "upload", tag, "--repo", SOURCE, str(marker), "--clobber")
        print(f"Published {tag} to Telegram {chat_id}; message_id={message_id}; body_sha256={body_sha256}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(str(error))
