# BiliSearch

一个基于 Kotlin + Jetpack Compose 的第三方哔哩哔哩客户端。只保留「搜索 + 播放」，没有推荐流，也不做内容分发。

## 声明

本项目为个人出于兴趣开发，**仅用于学习和测试**。所用 API 均收集自官方网站，**不提供、不存储、不破解任何内容**。

> 感谢使用。

## 功能特性

- 首页只保留搜索框，没有任何推荐流
- 首页左上角账号头像入口（点击进入「我的」），右上角夜间模式开关与设置齿轮
- 搜索：视频 / 番剧 / UP主 三个分类，支持分页加载
- 登录：B 站 App 扫码登录 / 内置网页登录（账号密码），两者可互相切换，登录态持久化
- 我的页面：头像、昵称（大会员粉色昵称）、大会员标识、等级经验条、硬币数、UID、个人签名、关注/粉丝
- 编辑资料：点头像进入，可修改名称、性别（男/女/保密）、自我介绍
- 搜索到的用户可在 App 内查看个人主页（资料、签名、投稿、公开收藏、合集，关注/粉丝在头像下方）
- 用户主页点头像放大查看，长按可选择保存到本地相册
- 合集：调用 `seasons_series_list` / `seasons_archives_list` 拉取合集列表与合集内视频
- 设置页：黑名单管理（支持解除拉黑）、退出登录、下载错误日志 / 清空日志（导出到 `下载/BiliSearch/`）
- 观看记录、我的投稿、我的收藏（含收藏夹内视频）、稍后再看、关注、粉丝、我的追番 / TA 的追番
- 主题：跟随系统 / 白天 / 夜间，可在搜索界面右上角切换
- 用户主页右上角菜单：分享主页、加入 / 移出黑名单
- 视频播放器（App 内播放，点视频封面直接进入）：
  - DASH 播放（视频流 + 音频流分别拉取后合并），清晰度菜单按**当前视频/剧集实际可选的档位**生成，支持倍速（0.5x ~ 2.0x）
  - 底部 2dp 细进度条（可拖动 / 点击跳转），与 B 站一致
  - 双击左/右 2/7 区域快退/快进 10 秒，双击中间暂停，长按 2 倍速
  - 点按视频呼出控制栏：左下暂停、右下全屏、倍速、右上更多设置
  - 抽屉：定时关闭、清晰度、弹幕设置（屏蔽类型 / 不透明度 / 字号 / 时长）、播放顺序（默认播完停止）、镜像翻转
  - 概要区：标题、播放/弹幕/日期、UP 主（UID / 粉丝数 / 投稿数 + 关注）、点赞 / 投币 / 收藏 / 稍后再看 / 分享
  - 进入时自动读取已点赞 / 已投币 / 已收藏 / 已关注 / 是否在稍后再看，按钮状态同步
  - 收藏弹窗会列出全部收藏夹（已收藏的会勾选并变粉），可多选；投币可选 1 / 2 枚
  - 视频所属合集（有则显示）：一集一个小方框，左右滑动选集，进入时自动定位到当前集
  - 番剧 / 影视：追番列表、原生播放、选集、共 N 集提示
  - 评论区：最新 / 最热排序、分页加载、**评论点赞**、**发表评论**、**点击某条评论进行回复**
  - 底部常驻评论条，点击后在原位置展开输入框并跟随输入法上移

## 尚未实现

- 上传头像（编辑资料目前只支持名称 / 性别 / 签名）
- 我的订阅（原接口返回异常，已移除入口）
- 搜索历史、搜索建议
- 收藏夹 / 历史记录内的管理操作（批量删除、取消收藏等）
- 弹幕发送
- 楼中楼（二级评论）列表展示
- 界面视觉设计打磨

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
