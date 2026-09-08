#!/usr/bin/env python3
"""Publish a verified GitHub Release APK to the List Cleaner Telegram channel.

The APK post always keeps the canonical GitHub/LSPosed links visible. The
feature changelog is sent as a second expandable HTML message so document
caption limits cannot remove links or truncate the release summary. A marker
asset records both message ids; replacement runs delete the previous group.
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
PUBLISH_FORMAT_VERSION = "document-plus-expandable-changelog-v2"
TELEGRAM_TEXT_LIMIT = 4096


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


def feature_summary(body):
    """Keep the curated release summary and exclude the raw per-commit appendix."""
    text = (body or "").replace("\r", "").strip()
    marker = "\n## 完整变更 / Full changelog\n"
    if marker in text:
        text = text.split(marker, 1)[0].rstrip()
    text = re.sub(r"^#.*?\n+", "", text, count=1)
    return text.strip()


def html_changelog(version, body, release_url):
    summary = feature_summary(body)
    escaped = html.escape(summary)
    header = f"<b>List Cleaner {html.escape(version)} · 更新日志 / Changelog</b>\n"
    footer = (
        f'\n\n<a href="{html.escape(release_url, quote=True)}">GitHub Release · 完整原始变更</a>'
        "\n<a href=\"https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/releases\">LSPosed 官方仓库 / Official repository</a>"
    )
    available = TELEGRAM_TEXT_LIMIT - len(re.sub(r"<[^>]+>", "", header + footer)) - 64
    if len(summary) > available:
        # Preserve links and explicit truncation marker rather than silently cutting the tail.
        clipped = summary[:max(0, available - 24)].rstrip()
        escaped = html.escape(clipped + "\n\n…其余内容见 GitHub Release")
    return header + f"<blockquote expandable>{escaped}</blockquote>" + footer


def apk_caption(version, release_url):
    return (
        f"📢 <b>List Cleaner {html.escape(version)} 发布 / Release</b>\n\n"
        f'<a href="{html.escape(release_url, quote=True)}">GitHub Release</a>\n'
        '<a href="https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/releases">LSPosed 官方仓库 / Official repository</a>\n\n'
        "完整更新日志见下一条折叠消息。\n"
        "Full changelog is in the next expandable message.\n\n"
        "#ListCleaner #LSPosed"
    )


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
    fields = {"chat_id": chat_id, "caption": caption, "parse_mode": "HTML"}
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
    if not result.get("ok"):
        raise RuntimeError(f"Telegram rejected the release message: {result.get('description', 'unknown error')}")
    return result["result"]["message_id"]


def send_text(token, chat_id, text):
    result = telegram_call(token, "sendMessage", {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": "true",
    })
    return result["message_id"]


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

        old_chat_id = marker_data.get("chat_id") or chat_id
        old_ids = marker_data.get("message_ids") or [marker_data.get("message_id")]
        for old_id in reversed([item for item in old_ids if item]):
            delete_message(token, old_chat_id, old_id)

        document_id = send_document(token, chat_id, apk, apk_caption(version, release["html_url"]))
        changelog_id = send_text(token, chat_id, html_changelog(version, release_body, release["html_url"]))
        message_ids = [document_id, changelog_id]

        marker = directory / MARKER_NAME
        marker.write_text(json.dumps({
            "tag": tag,
            "version": version,
            "chat_id": chat_id,
            "message_id": document_id,
            "message_ids": message_ids,
            "body_sha256": body_sha256,
            "presentation": PUBLISH_FORMAT_VERSION,
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        gh("release", "upload", tag, "--repo", SOURCE, str(marker), "--clobber")
        print(f"Published {tag} to Telegram {chat_id}; message_ids={message_ids}; body_sha256={body_sha256}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(str(error))
