# Release Signing and Publishing

[简体中文](RELEASE.md) | **English**

Official releases use a fixed RSA-3072 / PKCS12 key with APK v2/v3 signing. The repository stores only the public certificate SHA-256 (`signing/release-certificate.sha256`); private keys and passwords are never committed.

## One-time GitHub setup

In repository Settings → Secrets and variables → Actions, add a Repository secret:

- Name: `ANDROID_SIGNING_JSON`
- Value: the complete contents of `ANDROID_SIGNING_JSON.txt` from the signing backup package.

The JSON contains `keystore_base64`, `store_password`, `key_alias`, and `key_password`. Do not commit the JSON, key material, or backup package to source control, Issues, Actions artifacts, or Releases. Keep the backup permanently and do not replace the key used by published releases with a newly generated one.

Official publication is **manual only**. Open Actions → Build and Publish Release → Run workflow and select `main`. Ordinary pushes, version changes, release-workflow changes, and signing-configuration changes do not automatically publish a release. Pull requests only run Release compilation validation and never create a Release.

The manual publishing workflow builds Release, verifies signing and alignment, validates package identity, then uploads the APK, `SHA256SUMS.txt`, and `signature.txt` to GitHub Releases. The signing certificate must match the certificate pinned in the repository; a Debug key is never substituted for release signing.

Increment both `versionCode` and `versionName` before publishing a new version. Existing versions are never overwritten. Rebuilding an already published version may still complete build and validation, but publication skips an existing Release.

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

`Sync LSPosed Release` runs only in two cases:

- `Complete Release Changelog` finishes successfully on `main`, then `workflow_run` continues the release chain automatically.
- It is started manually from Actions → Sync LSPosed Release → Run workflow. The selected branch must be `main`; provide a stable `tag` to repair a specific version or leave it empty to synchronize the latest stable release.

Ordinary pushes, synchronization-script changes, and `docs/lsposed/` documentation changes do not directly publish a release. The synchronizer uses only **already published** source-repository Release assets and validates package name, version, SHA-256, and the pinned Release certificate.

All assets are uploaded to a draft before publication. A failed run can be retried; already uploaded identical assets are skipped. If the same version already contains a different file, synchronization stops instead of overwriting or deleting the existing asset. A synchronization failure does not affect the source repository's already published Release and never requires regenerating the signing key.

## Telegram publishing

`Publish Telegram Release` automatically continues after `Complete Release Changelog` succeeds on `main`, and it can also be started manually from Actions. Ordinary pushes do not send Telegram releases.

For initial setup, add these values under source repository Settings → Secrets and variables → Actions:

- `TELEGRAM_BOT_TOKEN`: Bot Token created with `@BotFather`. Never place it in source, logs, or chat.
- `TELEGRAM_CHAT_ID`: optional. `@LISTCLEANER` is the default; configure this only if the channel changes later.

Add the Bot to `@LISTCLEANER` as an administrator with at least permission to publish messages. For a manual run, specify a stable tag or leave it empty to use the latest stable Release.

After a successful post, the script adds a `telegram-published.json` marker asset to the matching GitHub Release and records the sent version plus Telegram `message_id`. Later reruns of the same version detect this marker and skip duplicate publication. A temporary Telegram failure only fails this independent workflow; it does not affect the GitHub Release, APK signature result, or LSPosed synchronization.
