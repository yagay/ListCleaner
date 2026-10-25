# Release 签名与发布

**简体中文** | [English](RELEASE.en.md)

正式版本使用固定的 RSA-3072 / PKCS12 密钥，并启用 APK v2/v3 签名。仓库只保存公开证书 SHA-256（`signing/release-certificate.sha256`），私钥和密码绝不提交。

## GitHub 一次性配置

在仓库 Settings → Secrets and variables → Actions 新增 Repository secret：

- 名称：`ANDROID_SIGNING_JSON`
- 值：签名备份包中 `ANDROID_SIGNING_JSON.txt` 的全部内容。

该 JSON 包含 `keystore_base64`、`store_password`、`key_alias`、`key_password`。不要把 JSON、密钥材料或备份包提交到源码、Issue、Actions artifacts 或 Releases。请长期保管备份，不要重新生成密钥来替代已发布版本使用的密钥。

正式发布**只允许手动启动**：进入 Actions → Build and Publish Release → Run workflow，并选择 `main`。普通 push、版本号修改、Release 工作流修改或签名配置修改都不会自动发布正式版。Pull Request 只运行 Release 编译验证，不创建 Release。

手动发布工作流会执行 Release 编译、签名与对齐校验、包身份检查，然后上传 APK、`SHA256SUMS.txt` 和 `signature.txt` 到 GitHub Releases。签名证书必须与仓库固定记录一致；不会使用 Debug 密钥代替正式签名。

发布新版本前必须同时递增 `versionCode` 和 `versionName`。已存在的版本绝不覆盖。重复构建已发布版本时，构建和校验仍可完成，但发布步骤会跳过已有 Release。

## 本地构建

将备份中的 `keystore.properties` 放在项目根目录，并将 `storeFile` 设置为密钥绝对路径：

```properties
storeFile=/absolute/path/to/ListCleaner-release.p12
storePassword=your-private-password
keyAlias=listcleaner
keyPassword=your-private-password
storeType=PKCS12
```

执行 `./gradlew :app:assembleRelease`。输出位于 `app/build/outputs/apk/release/app-release.apk`。

也支持等价环境变量：`RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`、`RELEASE_STORE_TYPE`；环境变量优先。正常 Release 构建缺少签名配置时会失败。仅做编译检查时可以显式传入 `-PallowUnsignedRelease=true`；生成的未签名 APK 不能安装或发布。

## 从 Debug 版迁移

固定 Release 证书通常与旧 Debug 证书不同，因此首次切换可能无法直接覆盖安装。请先在 App 内导出规则备份。之后持续使用同一 Release 签名即可正常覆盖升级。

## 同步到 LSPosed 官方仓库

源码和构建继续保留在 `yagay/ListCleaner`。模块说明和正式 APK Release 会同步到 `Xposed-Modules-Repo/com.yagay.ListCleaner`。

在**源码仓库** Settings → Secrets and variables → Actions 中添加 `LSPOSED_REPO_TOKEN`。令牌所属账号必须拥有官方模块仓库写权限。外部协作者可在组织策略允许时使用带 `public_repo` 权限的 classic PAT。令牌到期后更新同名 Secret。不要把令牌写入源码、日志或聊天。该凭据仅用于官方仓库同步，不替代 APK 签名配置。

`Sync LSPosed Release` 只在两种情况下运行：

- `Complete Release Changelog` 在 `main` 成功完成后，通过 `workflow_run` 自动接续。
- 手动从 Actions → Sync LSPosed Release → Run workflow 启动；必须选择 `main`，`tag` 可填写稳定版本补同步，留空则同步最新稳定版。

它不会因为普通 push、同步脚本修改或 `docs/lsposed/` 文档修改而直接发布。同步器只使用源码仓库**已经发布**的 Release 资产，并核对 APK 包名、版本、SHA-256 和固定 Release 证书。

所有附件先上传到草稿再发布。失败后可重跑；已经上传且完全相同的附件会跳过。如果相同版本已经存在不同文件，同步会停止，而不是覆盖或删除。同步失败不会影响源码仓库已经发布的 Release，也不需要重新生成签名密钥。

## Telegram 发布

`Publish Telegram Release` 在 `Complete Release Changelog` 于 `main` 成功完成后自动接续，也支持从 Actions 手动启动。它不会因为普通 push 自动发送。

首次配置时，在源码仓库 Settings → Secrets and variables → Actions 添加：

- `TELEGRAM_BOT_TOKEN`：通过 `@BotFather` 创建的 Bot Token。不要写入源码、日志或聊天。
- `TELEGRAM_CHAT_ID`：可选。默认使用 `@LISTCLEANER`；仅以后更换频道时需要配置。

把 Bot 加入 `@LISTCLEANER` 并设为管理员，至少授予发布消息权限。手动运行时可以填写稳定 tag，留空则使用最新稳定 Release。

发送成功后，脚本会在对应 GitHub Release 添加 `telegram-published.json` 标记附件，记录已发送版本和 Telegram `message_id`。之后重新运行相同版本时会检测该标记并跳过重复发送。Telegram 临时失败只会让独立发布工作流失败，不影响 GitHub Release、APK 签名结果或 LSPosed 同步。
