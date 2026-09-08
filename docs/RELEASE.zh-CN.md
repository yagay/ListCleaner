# Release 签名与发布

[English](RELEASE.md) | **简体中文**

正式版本使用固定的 RSA-3072 / PKCS12 密钥，并启用 APK v2/v3 签名。
仓库只保存公开证书的 SHA-256（`signing/release-certificate.sha256`），私钥和密码不提交。

## GitHub 一次性配置

在仓库 Settings → Secrets and variables → Actions 新增 Repository secret：

- 名称：`ANDROID_SIGNING_JSON`
- 值：签名备份包中 `ANDROID_SIGNING_JSON.txt` 的全部内容。

该 JSON 包含 `keystore_base64`、`store_password`、`key_alias`、`key_password`。
不要把 JSON、密钥或备份包提交到源码、Issue、Actions artifacts 或 Releases。
请长期保管备份；不要重新生成密钥来替代已发布版本使用的密钥。

配置完成后，进入 Actions → Build and Publish Release → Run workflow，选择 `main`。
也可以重跑此前因缺少 Secret 而失败的运行。工作流会执行 Release 编译、签名校验、
对齐校验和包身份检查，再上传 APK、SHA256SUMS.txt、signature.txt 到 GitHub Releases。
签名证书必须与仓库记录匹配。缺少 Secret 时只验证未签名的 Release 编译，随后停止；
不会发布未签名包或使用 Debug 密钥代替。

修改 `app/build.gradle.kts` 中的版本号、发布工作流或签名配置后也会触发发布流程。
发布新版本前同时递增 `versionCode` 和 `versionName`。已存在的版本不会被覆盖。
重复编译已发布版本时，APK 编译、签名校验和 Artifact 上传仍会完成，发布步骤会提示跳过，
不会因同名 Release 导致整个工作流失败。到该次 Actions 运行的 `ListCleaner-release-版本号`
Artifact 下载本次源码编译的 APK；已有 Release 附件仍对应原发布的源码。

## 本地构建

将备份中的 `keystore.properties` 放在项目根目录，把 `storeFile` 改为密钥的绝对路径：

```properties
storeFile=/absolute/path/to/ListCleaner-release.p12
storePassword=your-private-password
keyAlias=listcleaner
keyPassword=your-private-password
storeType=PKCS12
```

执行 `./gradlew :app:assembleRelease`，输出为 `app/build/outputs/apk/release/app-release.apk`。
也支持同名用途的 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、
`RELEASE_KEY_PASSWORD`、`RELEASE_STORE_TYPE` 环境变量，环境变量优先。
没有签名配置时正式构建报错。仅编译检查可显式传入 `-PallowUnsignedRelease=true`；
得到的未签名包不能用于安装或发布。

## 从 Debug 版迁移

新固定 Release 证书通常与旧 Debug 证书不同，因此首次切换可能无法直接覆盖安装。
先在 App 内导出规则备份。后续始终使用同一 Release 签名即可正常覆盖升级。

## 同步到 LSPosed 官方仓库

源码和构建继续保留在 `yagay/ListCleaner`，模块介绍与正式 APK 发布到
`Xposed-Modules-Repo/com.yagay.ListCleaner`。

在**源码仓库** Settings → Secrets and variables → Actions 中添加 `LSPOSED_REPO_TOKEN`。
令牌所属账号需要有官方模块仓库的写入权限。外部协作者可使用带 `public_repo` 权限的
classic PAT；组织策略若禁止该令牌，需由组织管理员处理。令牌到期后更新同名 Secret，
不要把令牌写入源码、日志或聊天。该凭据仅用于官方仓库同步，不替代 APK 签名配置。

`Sync LSPosed Release` 工作流在以下情况运行：

- `Build and Publish Release` 在 main 上成功结束后（包括重复编译已发布版本）。
- 手动在源码仓库发布 Release 后。
- main 上修改同步脚本、工作流或 `docs/lsposed/` 介绍资料后。
- 手动 Actions → Sync LSPosed Release → Run workflow，选择 main；`tag` 填 `v1.6.3`
  可补发指定版本，留空同步最新正式版。

同步下载源码仓库**已发布的** APK、校验文件及签名报告，核对 APK 实际包名、版本、
SHA-256 和固定签名证书后，以 `版本码-版本名` 创建官方 Release，例如 `28-1.6.3`。
更新说明来自原 Release，并附原始发布链接。不会把同版本重新编译的 Artifact 替换进去。
官方仓库的 README、SUMMARY 和简介由 `docs/lsposed/` 及脚本统一维护。

附件全部上传到草稿后才发布；失败后可重跑，已上传且相同的附件会跳过。
相同版本若已有不同文件则停止并提示，不覆盖或删除旧附件。
同步失败不影响自己仓库已发布的版本，也不需要重新生成签名密钥。

发布工作流使用 `GITHUB_TOKEN` 创建 Release 不会触发另一个普通 Release 事件工作流，
因此这里同时使用 `workflow_run` 接续同步。同步只运行可信 main 分支的脚本，不运行 PR
源码或下载 PR 构建产物，不向 PR 提供跨仓库令牌。

## 自动发布到 Telegram 频道

正式 Release 成功后，`Publish Telegram Release` 工作流会把对应的 Release APK 作为文件
直接发布到 Telegram 频道，并在附件说明中包含版本号、Release 更新说明、GitHub Release
链接和 LSPosed 官方仓库链接。

首次使用时，在源码仓库 Settings → Secrets and variables → Actions 添加：

- `TELEGRAM_BOT_TOKEN`：由 `@BotFather` 创建的 Bot Token。不要写入源码、日志或聊天。
- `TELEGRAM_CHAT_ID`：可选。默认已经使用 `@LISTCLEANER`；只有以后更换频道时才需要配置。

需要把 Bot 加入 `@LISTCLEANER` 频道并设为管理员，至少授予发布消息权限。

工作流在 `Build and Publish Release` 于 main 成功结束后自动运行，也支持手动运行。
手动运行时可以填写 `v1.6.4` 这类稳定版本 Tag；留空则使用最新正式 Release。

每次成功发送后，脚本会在对应 GitHub Release 中加入 `telegram-published.json` 标记附件，
记录已发送的版本与 Telegram `message_id`。之后即使重新运行相同版本，也会检测该标记并
跳过发送，避免重复发布。Telegram 临时故障只会让该独立工作流失败，不影响 GitHub Release、
APK 签名结果或 LSPosed 官方仓库同步。
