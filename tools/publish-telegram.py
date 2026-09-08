#!/usr/bin/env python3
"""Publish one Telegram release message with download links and expandable notes."""
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

SOURCE = "yagay/ListCleaner"
DEFAULT_CHAT_ID = "@LISTCLEANER"
MARKER_NAME = "telegram-published-v2.json"
PUBLISH_FORMAT_VERSION = "single-expandable-link-message-v3"
TELEGRAM_TEXT_LIMIT = 4096


def gh(*args):
    env = dict(os.environ, GH_TOKEN=os.environ["GITHUB_TOKEN"], GH_PROMPT_DISABLED="1")
    result = subprocess.run(["gh", *args], text=True, capture_output=True, env=env, timeout=180)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "GitHub operation failed")
    return result.stdout


def checked_tag(value):
    if value and not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", value):
        raise ValueError("Release tag must be a stable version such as v1.6.8")
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
    text = (body or "").replace("\r", "").strip()
    marker = "\n## 完整变更 / Full changelog\n"
    if marker in text:
        text = text.split(marker, 1)[0].rstrip()
    text = re.sub(r"^#.*?\n+", "", text, count=1)
    return text.strip()


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
        return
    try:
        telegram_call(token, "deleteMessage", {"chat_id": chat_id, "message_id": int(message_id)})
        print(f"Deleted previous Telegram release post: chat={chat_id}; message_id={message_id}")
    except RuntimeError as error:
        text = str(error).lower()
        if "message to delete not found" in text or "message can't be deleted" in text:
            print(f"Previous Telegram post could not be deleted ({error}); continuing.")
            return
        raise


def make_message(version, body, release_url, apk_url):
    title = f"📢 <b>List Cleaner {html.escape(version)} 发布 / Release</b>"
    links = (
        f'📦 <a href="{html.escape(apk_url, quote=True)}">下载 APK / Download APK</a>\n'
        f'🔗 <a href="{html.escape(release_url, quote=True)}">GitHub Release · 完整原始变更</a>\n'
        '🧩 <a href="https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/releases">LSPosed 官方仓库 / Official repository</a>'
    )
    footer = "\n\n#ListCleaner #LSPosed"
    summary = feature_summary(body)
    fixed_plain = len(re.sub(r"<[^>]+>", "", title + "\n\n" + links + "\n\n更新日志 / Changelog\n" + footer))
    available = max(0, TELEGRAM_TEXT_LIMIT - fixed_plain - 96)
    if len(summary) > available:
        summary = summary[:max(0, available - 28)].rstrip() + "\n\n…完整内容见 GitHub Release"
    return (
        title + "\n\n" + links +
        "\n\n<b>更新日志 / Changelog</b>\n" +
        f"<blockquote expandable>{html.escape(summary)}</blockquote>" + footer
    )


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
    apk_asset = find_asset(release, apk_name)
    if not apk_asset:
        raise ValueError(f"Release asset is missing: {apk_name}")
    apk_url = apk_asset.get("browser_download_url")
    if not apk_url:
        raise ValueError("Release APK has no browser_download_url")

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
                print(f"Telegram already published for {tag} with the current presentation; skipping duplicate send.")
                return

        old_chat_id = marker_data.get("chat_id") or chat_id
        old_ids = marker_data.get("message_ids") or [marker_data.get("message_id")]
        for old_id in reversed([item for item in old_ids if item]):
            delete_message(token, old_chat_id, old_id)

        text = make_message(version, release_body, release["html_url"], apk_url)
        result = telegram_call(token, "sendMessage", {
            "chat_id": chat_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": "true",
        })
        message_id = result["message_id"]

        marker = directory / MARKER_NAME
        marker.write_text(json.dumps({
            "tag": tag,
            "version": version,
            "chat_id": chat_id,
            "message_id": message_id,
            "message_ids": [message_id],
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
