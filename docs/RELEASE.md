# Release signing and publishing

**English** | [简体中文](RELEASE.zh-CN.md)

Official releases use a fixed RSA-3072 / PKCS12 key and APK v2/v3 signing. The repository stores only the public certificate SHA-256 (`signing/release-certificate.sha256`); private keys and passwords are never committed.

## One-time GitHub setup

In repository Settings → Secrets and variables → Actions, add a Repository secret:

- Name: `ANDROID_SIGNING_JSON`
- Value: the complete contents of `ANDROID_SIGNING_JSON.txt` from the signing backup package.

The JSON contains `keystore_base64`, `store_password`, `key_alias`, and `key_password`. Do not commit the JSON, key material, or backup package to source control, Issues, Actions artifacts, or Releases. Keep the backup permanently and do not replace the key used by published releases with a newly generated one.

After setup, open Actions → Build and Publish Release → Run workflow and select `main`. You can also rerun a previous workflow that failed because the secret was missing. The workflow builds Release, verifies signing and alignment, validates package identity, then uploads the APK, `SHA256SUMS.txt`, and `signature.txt` to GitHub Releases.

The signing certificate must match the certificate pinned in the repository. If the secret is missing, the workflow only verifies that unsigned Release compilation succeeds and then stops; it never publishes an unsigned APK or substitutes a Debug key.

Changes to the version in `app/build.gradle.kts`, the release workflow, or signing configuration can also trigger the release pipeline. Increment both `versionCode` and `versionName` before publishing a new version. Existing versions are never overwritten.

When rebuilding an already published version, APK compilation, signature verification, and artifact upload still complete, while the publishing step reports that the existing Release was skipped. A duplicate Release name does not make the whole workflow fail. Download the APK built from the current source from that Actions run's `ListCleaner-release-<version>` artifact; existing GitHub Release attachments continue to represent the originally published source.

## Local builds

Place the backed-up `keystore.properties` in the project root and set `storeFile` to the absolute path of the key:

```properties
storeFile=/absolute/path/to/ListCleaner-release.p12
storePassword=your-private-password
keyAlias=listcleaner
keyPassword=your-private-password
storeType=PKCS12
```

Run `./gradlew :app:assembleRelease`. Output is written to `app/build/outputs/apk/release/app-release.apk`.

Equivalent environment variables are also supported: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, and `RELEASE_STORE_TYPE`; environment variables take precedence. A normal Release build fails when signing configuration is missing. For compile-only checks, explicitly pass `-PallowUnsignedRelease=true`; the resulting unsigned APK must not be installed or published.

## Migrating from a Debug build

The fixed Release certificate normally differs from an older Debug certificate, so the first transition may not install as an in-place update. Export a rule backup from the app first. Future releases signed with the same Release key can then update each other normally.

## Synchronizing to the official LSPosed repository

Source code and builds remain in `yagay/ListCleaner`. Module documentation and official APK releases are mirrored to `Xposed-Modules-Repo/com.yagay.ListCleaner`.

In the **source repository**, add `LSPOSED_REPO_TOKEN` under Settings → Secrets and variables → Actions. The token owner must have write access to the official module repository. External collaborators can use a classic PAT with `public_repo` when allowed by organization policy. Update the same Secret when the token expires. Never place the token in source code, logs, or chat. This credential is only for official repository synchronization and does not replace APK signing configuration.

The `Sync LSPosed Release` workflow runs when:

- `Build and Publish Release` completes successfully on `main`, including a rebuild of an already published version.
- A Release is manually published in the source repository.
- Synchronization scripts, workflows, or `docs/lsposed/` documentation change on `main`.
- It is run manually from Actions → Sync LSPosed Release → Run workflow. Select `main`; set `tag` to a stable tag such as `v1.6.3` to repair a specific version, or leave it empty to synchronize the latest stable release.

The synchronizer downloads the **already published** APK, checksum file, and signature report from the source repository. It validates the APK package name, version, SHA-256, and pinned Release certificate, then creates an official Release named with `<versionCode>-<versionName>`, for example `28-1.6.3`.

Release notes originate from the source Release and include a link back to the original publication. A rebuilt artifact for the same version is never substituted for the published asset. The official repository README, localized README, SUMMARY files, and description are maintained from `docs/lsposed/` and the synchronization script.

All assets are uploaded to a draft before publication. A failed run can be retried; already uploaded identical assets are skipped. If the same version already contains a different file, synchronization stops instead of overwriting or deleting the existing asset. A synchronization failure does not affect the source repository's already published release and never requires regenerating the signing key.

A Release created by the publishing workflow with `GITHUB_TOKEN` does not trigger a normal secondary Release-event workflow, so `workflow_run` is also used to continue synchronization. Synchronization executes only trusted `main`-branch scripts; it does not run PR source or download PR build outputs, and no cross-repository token is exposed to PRs.

## Automatic Telegram publishing

After an official Release succeeds, the `Publish Telegram Release` workflow posts the corresponding Release APK directly to the Telegram channel. The attachment caption includes the version, Release notes, GitHub Release link, and official LSPosed repository link.

For initial setup, add these values under source repository Settings → Secrets and variables → Actions:

- `TELEGRAM_BOT_TOKEN`: Bot Token created with `@BotFather`. Never place it in source, logs, or chat.
- `TELEGRAM_CHAT_ID`: optional. `@LISTCLEANER` is already the default; configure this only if the channel changes later.

Add the Bot to `@LISTCLEANER` as an administrator with at least permission to publish messages.

The workflow runs automatically after `Build and Publish Release` completes successfully on `main`, and it can also be started manually. For a manual run, specify a stable tag such as `v1.6.4`, or leave it empty to use the latest stable Release.

After a successful post, the script adds a `telegram-published.json` marker asset to the matching GitHub Release and records the sent version plus Telegram `message_id`. Later reruns of the same version detect this marker and skip duplicate publication. A temporary Telegram failure only fails this independent workflow; it does not affect the GitHub Release, APK signature result, or LSPosed synchronization.
