# NHentai CN Mihon Extension Repo

这是一个单独的 Mihon 扩展仓库，包含定制版 nHentai 源。

## 功能

- 独立包名：`eu.kanade.tachiyomi.extension.all.nhentaicn`
- NSFW 标记已开启
- 只显示一个源：`NHentai 中文/收藏`
- 首页保留中文热门和中文最近更新
- 收藏合并到同一个源的筛选项：`只显示我的收藏`
- 筛选、源设置、详情描述已中文化
- 收藏接口支持 API key 或 Mihon WebView 本地登录 Cookie
- API key 设置会从旧版多个源自动迁移到共享设置

## 登录说明

账号密码不会写入仓库、源码、APK 或索引。查看收藏时请在 Mihon 中使用以下任一方式：

- 推荐：在普通浏览器登录 nHentai，到 Profile > Settings > API Keys 创建 API key，然后填入源设置
- 备用：打开源的 WebView 并登录 nHentai

如果 WebView 登录提示 `Failed to load CAPTCHA script`，通常是系统 WebView、代理、DNS 或去广告工具拦截了 hCaptcha。请优先使用 API key；也可以尝试更新 Android System WebView/Chrome，并确保 `hcaptcha.com`、`js.hcaptcha.com` 没有被拦截。

## 发布

把本目录推送到 GitHub 后，在 Mihon 添加仓库地址：

```text
https://raw.githubusercontent.com/gudada1/nhentai-mihon-extension-repo/main/index.min.json
```

如果你的 Mihon 版本支持新版仓库元数据，也可以添加：

```text
https://raw.githubusercontent.com/gudada1/nhentai-mihon-extension-repo/main/repo.json
```

如果以后重新构建 APK，可以运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-index.ps1
```
