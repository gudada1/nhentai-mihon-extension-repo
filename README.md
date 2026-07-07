# Gudada Mihon Extension Repo

这是一个单独的 Mihon 扩展仓库，包含常用漫画源的中文筛选定制版。

## 包含扩展

- Akuma CN
- AsmHentai CN
- E-Hentai CN
- HDoujin CN
- HentaiFox CN
- Hitomi CN
- MangaDex CN
- MyComic CN
- NHentai CN
- Pixiv CN

## 定制内容

- 常用源的筛选项尽量中文化，并保留原站查询参数
- 多语言源保留原有多站点/多语言入口
- 成人向源保留 NSFW 标记
- `NHentai 中文/收藏` 首页保留中文热门和中文最近更新
- `NHentai 中文/收藏` 收藏合并到同一个源的筛选项：`只显示我的收藏`
- `NHentai 中文/收藏` 支持收藏起始页筛选，可从指定页开始浏览
- `NHentai 中文/收藏` 会记住上次填写的收藏起始页和预设
- 收藏接口支持 API key 或 Mihon WebView 本地登录 Cookie
- 找不到 API key 时，可在源设置中手动填写自己的 `access_token` Cookie 作为备用
- API key 设置会从旧版多个源自动迁移到共享设置
- API key 会自动清理 `Key ` / `Authorization: Key ` 前缀和多余空格

## 登录说明

账号密码不会写入仓库、源码、APK 或索引。查看收藏时请在 Mihon 中使用以下任一方式：

- 推荐：在普通浏览器登录 nHentai，到 `https://nhentai.net/user/settings#apikeys` 创建 API key，然后填入源设置
- 备用：打开源的 WebView 并登录 nHentai
- 备用：如果 API key 页面找不到且 WebView CAPTCHA 失败，可从你自己已登录的浏览器里复制 `access_token` Cookie，填入源设置的 `Access token（备用）`

如果 WebView 登录提示 `Failed to load CAPTCHA script`，通常是系统 WebView、代理、DNS 或去广告工具拦截了 hCaptcha。请优先使用 API key；也可以尝试更新 Android System WebView/Chrome，并确保 `hcaptcha.com`、`js.hcaptcha.com` 没有被拦截。

## 发布

在 Mihon 添加仓库地址：

```text
https://raw.githubusercontent.com/gudada1/nhentai-mihon-extension-repo/main/v5/index.min.json
```

旧地址保留备用：

```text
https://raw.githubusercontent.com/gudada1/nhentai-mihon-extension-repo/main/index.min.json
```

`index.min.json` 是 Mihon 常用的传统仓库索引格式。如果你的 Mihon 版本支持新版仓库元数据，可以添加：

```text
https://raw.githubusercontent.com/gudada1/nhentai-mihon-extension-repo/main/v5/repo.json
```

新版元数据旧地址保留备用：

```text
https://raw.githubusercontent.com/gudada1/nhentai-mihon-extension-repo/main/repo.json
```

如果以后重新构建 APK，可以运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-index.ps1
```
