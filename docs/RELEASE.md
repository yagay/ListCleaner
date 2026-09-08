# Release 签名与发布

**简体中文** | [English](RELEASE.en.md)

正式版本使用固定的 RSA-3072 / PKCS12 密钥，并启用 APK v2/v3 签名。仓库只保存公开证书 SHA-256（`signing/release-certificate.sha256`），私钥和密码绝不提交。

## GitHub 一次性配置

在仓库 Settings → Secrets and variables → Actions 新增 Repository secret：

- 名称：`ANDROID_SIGNING_JSON`
- 值：签名备份包中 `ANDROID_SIGNING_JSON.txt` 的全部内容。

该 JSON 包含 `keystore_base64`、`store_password`、`key_alias`、`key_password`。不要把 JSON、密钥材料或备份包提交到源码、Issue、Actions artifacts 或 Releases。请长期保管备份，不要重新生成密钥来替代已发布版本使用的密钥。

配置完成后，进入 Actions → Build and Publish Release → Run workflow，选择 `main`。也可以重跑此前因缺少 Secret 而失败的运行。工作流会执行 Release 编译、签名与对齐校验、包身份检查，然后上传 APK、`SHA256SUMS.txt` 和 `signature.txt` 到 GitHub Releases。

签名证书必须与仓库固定记录一致。缺少 Secret 时只验证未签名 Release 是否能成功编译，然后停止；不会发布未签名 APK，也不会用 Debug 密钥代替。

修改 `app/build.gradle.kts` 中的版本、Release 工作流或签名配置后也可能触发发布流程。发布新版本前必须同时递增 `versionCode` 和 `versionName`。已存在的版本绝不覆盖。

重复构建已发布版本时，APK 编译、签名校验和 Artifact 上传仍会完成，发布步骤会提示跳过，不会因为同名 Release 让整个工作流失败。可以从该次 Actions 运行的 `ListCleaner-release-<version>` Artifact 下载当前源码构建的 APK；已有 GitHub Release 附件继续代表最初发布时的源码。

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

`Sync LSPosed Release` 工作流在以下情况运行：

- `Build and Publish Release` 在 `main` 成功完成后，包括重新构建已发布版本。
- 在源码仓库手动发布 Release 后。
- `main` 上的同步脚本、工作流或 `docs/lsposed/` 文档变化后。
- 手动从 Actions → Sync LSPosed Release → Run workflow 启动。选择 `main`；`tag` 可填 `v1.6.3` 这类稳定版本补同步指定版本，留空则同步最新稳定版。

同步器下载源码仓库**已经发布**的 APK、校验文件和签名报告，核对 APK 包名、版本、SHA-256 和固定 Release 证书，然后以 `<versionCode>-<versionName>` 创建官方 Release，例如 `28-1.6.3`。

更新说明来自源码仓库 Release，并附原始发布链接。同版本重新构建的 Artifact 不会替换已发布附件。官方仓库 README、本地化 README、SUMMARY 和仓库简介由 `docs/lsposed/` 与同步脚本统一维护。

所有附件先上传到草稿再发布。失败后可重跑；已经上传且完全相同的附件会跳过。如果相同版本已经存在不同文件，同步会停止，而不是覆盖或删除。同步失败不会影响源码仓库已经发布的 Release，也不需要重新生成签名密钥。

发布工作流用 `GITHUB_TOKEN` 创建 Release 时，不会触发普通的二次 Release 事件工作流，因此同时使用 `workflow_run` 接续同步。同步只运行可信 `main` 分支脚本，不运行 PR 源码、不下载 PR 构建产物，也不会把跨仓库 Token 暴露给 PR。

## 自动发布到 Telegram

正式 Release 成功后，`Publish Telegram Release` 工作流会把对应 Release APK 直接发布到 Telegram 频道。附件说明包含版本、Release Notes、GitHub Release 链接和 LSPosed 官方仓库链接。

首次配置时，在源码仓库 Settings → Secrets and variables → Actions 添加：

- `TELEGRAM_BOT_TOKEN`：通过 `@BotFather` 创建的 Bot Token。不要写入源码、日志或聊天。
- `TELEGRAM_CHAT_ID`：可选。默认已经使用 `@LISTCLEANER`；仅以后更换频道时需要配置。

把 Bot 加入 `@LISTCLEANER` 并设为管理员，至少授予发布消息权限。

工作流会在 `Build and Publish Release` 于 `main` 成功完成后自动运行，也支持手动启动。手动运行时可以填写 `v1.6.4` 这类稳定 tag，留空则使用最新稳定 Release。

发送成功后，脚本会在对应 GitHub Release 添加 `telegram-published.json` 标记附件，记录已发送版本和 Telegram `message_id`。之后重新运行相同版本时会检测该标记并跳过重复发送。Telegram 临时失败只会让独立发布工作流失败，不影响 GitHub Release、APK 签名结果或 LSPosed 同步。
