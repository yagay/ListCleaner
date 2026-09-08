#!/usr/bin/env python3
import argparse
import os
import subprocess
from pathlib import Path


def run(*args: str) -> str:
    return subprocess.check_output(args, text=True).strip()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--certificate", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    repo = os.environ.get("GITHUB_REPOSITORY", "")
    current_tag = f"v{args.version}"

    # Prefer the latest published release tag, excluding the version being built.
    previous_tag = ""
    try:
        releases = run(
            "gh", "release", "list",
            "--limit", "100",
            "--json", "tagName,isDraft,isPrerelease,publishedAt",
            "--jq", f'.[] | select(.isDraft == false and .isPrerelease == false and .tagName != "{current_tag}") | .tagName'
        ).splitlines()
        if releases:
            previous_tag = releases[0].strip()
    except Exception:
        previous_tag = ""

    # Fallback for repositories without an earlier published release.
    if not previous_tag:
        try:
            tags = run("git", "tag", "--sort=-creatordate").splitlines()
            previous_tag = next((tag for tag in tags if tag != current_tag), "")
        except Exception:
            previous_tag = ""

    summary_path = Path("RELEASE_NOTES.md")
    summary = summary_path.read_text(encoding="utf-8").strip() if summary_path.exists() else ""
    expected_heading = f"# 列表清理 / List Cleaner {args.version}"
    if summary and summary.splitlines()[0].strip() != expected_heading:
        raise SystemExit(f"RELEASE_NOTES.md heading must be: {expected_heading}")

    if previous_tag:
        commit_lines = run(
            "git", "log", f"{previous_tag}..{args.commit}",
            "--reverse", "--no-merges",
            "--pretty=format:%H%x09%s"
        ).splitlines()
    else:
        commit_lines = run(
            "git", "log", args.commit,
            "--reverse", "--no-merges",
            "--pretty=format:%H%x09%s"
        ).splitlines()

    changelog = []
    for line in commit_lines:
        if not line.strip():
            continue
        sha, subject = line.split("\t", 1)
        short = sha[:7]
        if repo:
            changelog.append(f"- {subject} ([`{short}`](https://github.com/{repo}/commit/{sha}))")
        else:
            changelog.append(f"- {subject} (`{short}`)")

    parts = []
    if summary:
        parts.append(summary)
    else:
        parts.append(expected_heading)

    parts.append("## 完整变更 / Full changelog")
    if previous_tag:
        parts.append(f"范围 / Range: `{previous_tag}...{current_tag}`")
    else:
        parts.append("范围 / Range: repository history (no previous release found)")
    parts.append("\n".join(changelog) if changelog else "- No commits found in the release range.")

    parts.append(f"版本码 / Version code: {args.version_code}")
    parts.append(f"签名证书 SHA-256 / Signing certificate SHA-256: `{args.certificate}`")
    parts.append(f"源码提交 / Source commit: `{args.commit}`")

    Path(args.output).write_text("\n\n".join(parts) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
