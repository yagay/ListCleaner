import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('sync_lsposed', Path(__file__).with_name('sync-lsposed.py'))
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class ReleaseVerificationTest(unittest.TestCase):
    def test_identity_signature_and_version(self):
        badging = "package: name='com.yagay.ListCleaner' versionCode='28' versionName='1.6.3'"
        signature = 'Signer #1 certificate SHA-256 digest: ' + 'a' * 64
        self.assertEqual(('28', '1.6.3'), sync.verify_metadata('v1.6.3', badging, signature, 'a' * 64))
        for tag, badge, cert in [
            ('v1.6.4', badging, signature),
            ('v1.6.3', badging.replace('com.yagay.ListCleaner', 'com.other.app'), signature),
            ('v1.6.3', badging + '\napplication-debuggable', signature),
            ('v1.6.3', badging.replace("'28'", "'0'"), signature),
            ('v1.6.3', badging, signature.replace('a' * 64, 'b' * 64)),
            ('v1.6.3', badging, signature + '\n' + signature.replace('#1', '#2')),
        ]:
            with self.subTest(tag=tag, badge=badge, cert=cert), self.assertRaises(ValueError):
                sync.verify_metadata(tag, badge, cert, 'a' * 64)

    def test_only_stable_tags(self):
        self.assertEqual('v1.6.3', sync.checked_tag('v1.6.3'))
        self.assertEqual('', sync.checked_tag(''))
        for tag in ['../main', 'v1.6.3-beta', '--help', 'v1.6.3\n', '1.6.3']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                sync.checked_tag(tag)

    def test_checksum_required_and_exact(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'app.apk'
            apk.write_bytes(b'published apk')
            checks = Path(tmp) / 'SHA256SUMS.txt'
            correct = sync.digest(apk) + '  app.apk\n'
            checks.write_text(correct)
            sync.verify_checksum(apk, checks)
            for invalid in ['', correct.replace('app.apk', 'other.apk'), correct * 2, '0' * 64 + '  app.apk']:
                checks.write_text(invalid)
                with self.assertRaises(ValueError):
                    sync.verify_checksum(apk, checks)

    def test_retry_only_uploads_missing_assets_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'app.apk'
            apk.write_bytes(b'published apk')
            checksum = Path(tmp) / 'SHA256SUMS.txt'
            checksum.write_text('checksums')
            existing = {'name': apk.name, 'state': 'uploaded', 'digest': 'sha256:' + sync.digest(apk)}
            def no_download(_):
                self.fail('Digest is available; no download should be needed')
            self.assertEqual([checksum], sync.asset_plan([existing], [apk, checksum], no_download))
            self.assertEqual([], sync.asset_plan([existing], [apk], no_download))
            for changed in [dict(existing, digest='sha256:bad'), dict(existing, state='starter')]:
                with self.assertRaises(ValueError):
                    sync.asset_plan([changed], [apk], no_download)
            legacy = dict(existing)
            legacy.pop('digest')
            self.assertEqual([], sync.asset_plan([legacy], [apk], lambda _: apk))

    def test_bilingual_document_sections_match(self):
        chinese = '<!-- section:intro -->\n<!-- section:features -->'
        english = '<!-- section:intro -->\n<!-- section:features -->'
        self.assertEqual(sync.document_sections(chinese), sync.document_sections(english))
        self.assertNotEqual(
            sync.document_sections(chinese),
            sync.document_sections('<!-- section:intro -->\n<!-- section:usage -->')
        )

    def test_repository_bilingual_docs_are_in_sync(self):
        sync.verify_bilingual_docs()


if __name__ == '__main__':
    unittest.main()
