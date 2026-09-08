#!/usr/bin/env python3
"""Mirror verified, already-published releases; never rebuild or expose signing keys."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

SOURCE = 'yagay/ListCleaner'
TARGET = 'Xposed-Modules-Repo/com.yagay.ListCleaner'
ROOT = Path(__file__).resolve().parents[1]


def gh(token, *args, payload=None, missing_ok=False):
    env = dict(os.environ, GH_TOKEN=token, GH_PROMPT_DISABLED='1')
    command = ['gh', *args]
    if payload is not None:
        command += ['--input', '-']
    result = subprocess.run(command, input=json.dumps(payload) if payload is not None else None,
                            text=True, capture_output=True, env=env, timeout=180)
    if result.returncode:
        if missing_ok and '(HTTP 404)' in result.stderr:
            return None
        if '(HTTP 403)' in result.stderr:
            raise RuntimeError('GitHub denied access (403). Check token scope, expiry and organization policy.')
        raise RuntimeError(f'GitHub operation failed: {result.stderr.strip()}')
    return result.stdout


def api(token, repo, path='', method='GET', data=None, missing_ok=False):
    result = gh(token, 'api', f'repos/{repo}{path}', '--method', method,
                payload=data, missing_ok=missing_ok)
    return None if result is None else (json.loads(result) if result.strip() else {})


def checked_tag(value):
    if value and not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', value):
        raise ValueError('Source tag must be a stable version such as v1.6.3')
    return value


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_metadata(tag, badging, signature, expected):
    package = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
    certs = re.findall(r'Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signature)
    if not package or package[1] != 'com.yagay.ListCleaner' or 'v' + package[3] != tag:
        raise ValueError('Published APK package/version does not match the source release')
    if int(package[2]) <= 0 or 'application-debuggable' in badging:
        raise ValueError('Refusing an invalid version code or debuggable APK')
    if len(certs) != 1 or certs[0].lower() != expected:
        raise ValueError('Published APK does not use the pinned Release certificate')
    return package[2], package[3]


def verify_checksum(apk, checksum_file):
    expected = []
    for line in checksum_file.read_text().splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].lstrip('*') == apk.name:
            expected.append(parts[0].lower())
    if expected != [digest(apk)]:
        raise ValueError('Published APK checksum is missing, duplicated or incorrect')


def document_sections(text):
    return re.findall(r'<!--\s*section:([a-z0-9_-]+)\s*-->', text)


def verify_bilingual_docs():
    chinese = (ROOT / 'docs/lsposed/README.md').read_text()
    english = (ROOT / 'docs/lsposed/README.en.md').read_text()
    zh_sections = document_sections(chinese)
    en_sections = document_sections(english)
    if not zh_sections or zh_sections != en_sections:
        raise ValueError(
            'LSPosed Chinese and English documentation sections are out of sync. '
            'Update README.md and README.en.md together with matching section markers.'
        )


def sync_document(token, name):
    path = '/' + 'contents/' + name
    current = api(token, TARGET, path, missing_ok=True)
    content = (ROOT / 'docs/lsposed' / name).read_bytes()
    if current and base64.b64decode(current['content']) == content:
        return
    data = {'message': f'docs: update {name} from ListCleaner source repository',
            'content': base64.b64encode(content).decode()}
    if current:
        data['sha'] = current['sha']
    api(token, TARGET, path, 'PUT', data)


def find_release(token, tag):
    page = 1
    while True:
        releases = api(token, TARGET, f'/releases?per_page=100&page={page}')
        for release in releases:
            if release['tag_name'] == tag:
                return release
        if len(releases) < 100:
            return None
        page += 1


def asset_plan(assets, files, download):
    missing = []
    for path in files:
        matches = [a for a in assets if a['name'] == path.name]
        if not matches:
            missing.append(path)
            continue
        if len(matches) != 1 or matches[0].get('state') != 'uploaded':
            raise ValueError(f'Incomplete or duplicate official asset: {path.name}')
        asset = matches[0]
        expected = 'sha256:' + digest(path)
        actual = asset.get('digest') or ('sha256:' + digest(download(path.name)))
        if actual != expected:
            raise ValueError(f'Official asset differs: {path.name}; refusing to overwrite this version')
    return missing


def main():
    verify_bilingual_docs()
    source_token = os.environ['SOURCE_TOKEN']
    target_token = os.environ['LSPOSED_REPO_TOKEN']
    if not target_token:
        raise ValueError('LSPOSED_REPO_TOKEN is missing')
    requested = checked_tag(os.environ.get('SOURCE_TAG', '').strip())
    source = api(source_token, SOURCE, '/releases/' + ('tags/' + requested if requested else 'latest'))
    tag = checked_tag(source['tag_name'])
    if not tag or source['draft'] or source['prerelease']:
        raise ValueError('Only published stable releases can be synchronized')
    apk_name = f'ListCleaner-{tag[1:]}-release.apk'
    names = [apk_name, 'SHA256SUMS.txt', 'signature.txt']
    for name in names:
        if sum(a['name'] == name and a['state'] == 'uploaded' for a in source['assets']) != 1:
            raise ValueError(f'Source release is missing a complete asset: {name}')

    with tempfile.TemporaryDirectory(prefix='lsposed-sync-') as directory:
        directory = Path(directory)
        gh(source_token, 'release', 'download', tag, '--repo', SOURCE, '--dir', str(directory),
           '--pattern', apk_name, '--pattern', 'SHA256SUMS.txt', '--pattern', 'signature.txt')
        apk = directory / apk_name
        verify_checksum(apk, directory / 'SHA256SUMS.txt')
        build_tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
        signature = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose',
                                             '--print-certs', str(apk)], text=True, timeout=60)
        badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)],
                                           text=True, timeout=60)
        code, version = verify_metadata(tag, badging, signature,
            (ROOT / 'signing/release-certificate.sha256').read_text().strip().lower())
        official_tag = f'{code}-{version}'
        files = [directory / name for name in names]
        release = find_release(target_token, official_tag)

        def download(name):
            destination = directory / 'existing'
            destination.mkdir(exist_ok=True)
            gh(target_token, 'release', 'download', official_tag, '--repo', TARGET,
               '--pattern', name, '--dir', str(destination))
            return destination / name

        missing = asset_plan(release['assets'] if release else [], files, download)
        for document in ('README.md', 'README.en.md', 'SUMMARY', 'SUMMARY.en'):
            sync_document(target_token, document)
        metadata = api(target_token, TARGET)
        description = '列表清理 · List Cleaner'
        if metadata.get('description') != description or metadata.get('homepage') != f'https://github.com/{SOURCE}':
            api(target_token, TARGET, method='PATCH', data={
                'description': description, 'homepage': f'https://github.com/{SOURCE}'})
        body = (
            (source.get('body') or '')
            + f'\n\n原始发布 / Original release：{source["html_url"]}\n'
            + '\n使用要求 / Requirements：modern libxposed API 102；Android 12 及以上 / Android 12 or newer.\n'
        )
        if release is None:
            release = api(target_token, TARGET, '/releases', 'POST', {
                'tag_name': official_tag, 'target_commitish': metadata['default_branch'],
                'name': version, 'body': body, 'draft': True, 'prerelease': False})
        if missing:
            gh(target_token, 'release', 'upload', official_tag, '--repo', TARGET,
               *[str(path) for path in missing])
        if release['draft'] or missing or release.get('body') != body or release.get('name') != version:
            release = api(target_token, TARGET, f'/releases/{release["id"]}', 'PATCH', {
                'name': version, 'body': body, 'draft': False, 'prerelease': False,
                'make_latest': 'legacy'})
        url = f'https://github.com/{TARGET}/releases/tag/{official_tag}'
        print(f'Synchronized verified release: {url}')
        if os.environ.get('GITHUB_STEP_SUMMARY'):
            with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
                summary.write(f'Synchronized [{official_tag}]({url}); APK matches the published source release.\n')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, subprocess.SubprocessError, OSError) as error:
        raise SystemExit(str(error))
