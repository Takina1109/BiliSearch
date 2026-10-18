# BiliSearch

一个基于 Kotlin + Jetpack Compose 的第三方哔哩哔哩客户端。只保留「搜索 + 播放」，没有推荐流，也不做内容分发。

## 声明

本项目为个人出于兴趣开发，**仅用于学习和测试**。所用 API 均收集自官方网站，**不提供、不存储、不破解任何内容**。

> 感谢使用。

## 功能特性

-首页与搜索
-登录与个人中心
-用户主页
-视频与番剧播放
-评论区
-合集功能
-设置与系统功能

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- Navigation Compose
- Media3 / ExoPlayer（DASH 播放）
- OkHttp + Gson（B 站 API，含 WBI 签名）
- Coil（图片加载）
- ZXing（登录二维码）
- 架构：`data`（net / model / repo / local）+ `ui`（screens / components / navigation / theme）

## 工程结构

```
app/src/main/java/com/takina/bilisearch/
├── BiliApp.kt                  Application，初始化依赖容器
├── MainActivity.kt             入口，主题切换
├── data/
│   ├── AppGraph.kt             极简依赖容器
│   ├── local/Prefs.kt          Cookie、主题等本地存储
│   ├── model/BiliModels.kt     B 站接口数据模型
│   ├── net/
│   │   ├── BiliHttp.kt         OkHttp 客户端与请求封装
│   │   ├── BiliCookieJar.kt    Cookie 持久化
│   │   ├── WbiSigner.kt        WBI 签名（搜索/用户相关接口必需）
│   │   ├── BiliApi.kt          接口常量、buvid 初始化
│   │   └── BiliException.kt
│   └── repo/
│       ├── BiliRepository.kt   所有接口调用
│       └── Entries.kt          UI 条目模型与映射
└── ui/
    ├── navigation/             路由与 NavHost
    ├── components/             通用组件、列表行、主题切换
    ├── screens/
    │   ├── search/             搜索
    │   ├── login/              扫码登录
    │   ├── me/                 我的
    │   ├── player/             视频播放器（手势 / 控制栏 / 弹幕 / 评论）
    │   ├── profile/            编辑资料
    │   ├── user/               其他用户主页
    │   ├── settings/           设置与黑名单管理
    │   └── list/               历史/收藏/合集/关注/粉丝等列表
    ├── theme/                  主题配色
    └── util/                   二维码渲染、图片保存
```

## 构建

需要 JDK 17+ 与 Android SDK 36。

```
gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

如果需要指定 JDK（Android Studio 自带的即可）：

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
```

### 正式签名（可选）

默认使用 debug 签名，clone 下来可开箱即用。如果要正式分发：

```
keytool -genkeypair -v -keystore bilisearch-release.jks -alias bilisearch ^
  -keyalg RSA -keysize 2048 -validity 10000
```

然后在仓库根目录把 `keystore.properties.example` 复制为 `keystore.properties` 并填入信息：

```
gradlew.bat assembleRelease
```

`keystore.properties` 与 `*.jks` 已在 `.gitignore` 中，不会上传。

> ⚠️ keystore 一旦丢失，后续版本就无法覆盖安装，请务必备份。

## 已知限制

- 全部接口为 B 站 Web 端非公开接口，随时可能失效，本项目不保证可用性。
- 播放地址由服务端按 CDN 策略分配，个别网络环境下某些节点不可用；App 会自动重试并切换地址，仍失败时会提示。
- 部分版权内容需要对应账号权限（如大会员），本项目**不提供任何绕过手段**。
- 仅在 Android 8.0+ 手机上测试过，未适配平板 / 折叠屏 / 横屏平板布局。

## 安全提示

- 登录态 Cookie 只保存在应用私有目录，**不上传、不外发**，仅用于请求 B 站接口。
- 请勿在 Issue / 讨论区粘贴包含 `SESSDATA` 的日志或截图——那等同于公开你的账号。
- 导出错误日志前请自行确认是否包含敏感信息。

## 反馈

提 Issue 时请附上：设备型号、Android 版本、复现步骤，以及「设置 → 诊断 → 下载错误日志」导出的日志（**务必先脱敏**）。

## 致谢

- [bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) — B 站接口文档，本项目大量接口参数参考自此
- [PiliPala](https://github.com/guozhigq/pilipala) — 播放链路与 CDN 兜底思路的参考
- [AndroidX Media3](https://github.com/androidx/media) / [Coil](https://github.com/coil-kt/coil) / [ZXing](https://github.com/zxing/zxing) — 开源库

## License

[MIT](LICENSE) © 2026

仅供学习交流，**禁止商业用途**。如有侵权，请联系删除。
