# FluidCloudX (流体云 Xposed 增强版)

本项目是基于原 ShortX 脚本 **“流体云组件 3.8（云端规则版）”** 完全使用**现代 Android 技术栈（Modern Android Development）** 与 **现代 Xposed 框架规范（LibXposed API 102）** 彻底重构的系统级模块。

内置 **27 套社区精选规则** 开箱即用，无需繁琐导入或手动配置，安装即可享受丝滑的流体云直达体验！

---

## 🌟 为什么重写为 Xposed 模块？架构对比

| 维度 | 原 ShortX 脚本 (Rhino JS + Shizuku/Accessibility) | 现代 Xposed 模块 (FluidCloudX) |
| :--- | :--- | :--- |
| **执行环境** | 宿主进程内通过 Rhino / JS 动态 `eval` 解析执行，有解释器开销与类型丢失 | 纯原生 **Kotlin 2.0** 编译为 DEX，极致性能与类型安全 |
| **拦截延迟** | 依赖 Accessibility 无障碍感知或后台轮询，存在明显滞后或触发漏检 | **0 延迟系统级 Hook**：在 `system_server` 的 `obtainStarter` / `startActivity` 和 `setPrimaryClip` 前置拦截 |
| **微信 / QQ 拦截** | 依赖 ActivityStarted 事实感知并异步处理，常常先打开内置浏览器才跳转 | 直接注入 QQ / 微信进程，在 `QQBrowserActivity` / `MMWebViewUI` 初始化时即完成抓取并拦截 |
| **小窗控制** | 依赖 ShortX 发送 Shell 命令或受限的反射 | 拥有系统特权直接调用 `ActivityOptions.setLaunchWindowingMode`（兼容 ColorOS Zoom 与原生 Freeform） |
| **内置规则** | 需联网手动拉取或单独导入多份 JSON 规则 | **内置 27 条全量精选规则 + 2 条无链接规则**，并支持云端一键增量同步与重置 |
| **界面与配置** | 原生 Android View 手写拼装，编辑繁琐 | **Jetpack Compose + Material 3** 动态取色界面，内置作用域勾选指南、规则调试沙盒与一键复制 |

---

## 📱 LSPosed 作用域（极简・零封号安全设计）

本模块遵循**纯系统框架（Pure System Framework Hook）**架构，在 LSPosed 管理器中**仅需勾选 1 项**：

| 勾选项 | 包名 (Package Name) | 作用说明 / 为什么仅需勾选系统框架 |
| :--- | :--- | :--- |
| 👑 **系统框架 (system_server)** | `android` | **核心总调度**：在 Android 系统底层直接拦截全系统的全局外链跳转 (`startActivity`) 与剪贴板变动 (`setPrimaryClip`)，统管所有应用分发 |

> 🛡️ **安全与防封保障**：
> - **绝不注入任何第三方应用进程**（特别是微信、QQ、支付宝、银行等），**从物理层面彻底杜绝任何账号被风控、封号或检测的风险**。
> - 系统框架掌控全局咽喉要道，即使不 Hook 第三方应用，所有跳转到外部的链接或复制的链接均能在 0 延迟下精准拦截并呼起流体云胶囊。

---

## 📦 内置 27 套社区精选规则一览

模块打包内置了全部 27 款主流常用规则，开箱即用，支持提取码自动提取与回填：

1. **百度网盘** (`pan.baidu.com` / `yun.baidu.com`) -> 自动识别并附带 4 位提取码，直接呼起百度网盘
2. **123网盘** (`123pan.com`) -> 自动提取提取码并跳转 123 网盘客户端
3. **115网盘** (`115.com`) -> 自动提取 115 提取码并跳转 115 客户端
4. **阿里云盘** (`alipan.com` / `aliyundrive.com`) -> 自动转入阿里云盘 App
5. **夸克网盘** (`pan.quark.cn`) -> 识别夸克网盘链接并调用夸克
6. **哔哩哔哩** (`bilibili.com` / `b23.tv`) -> 自动解析 B 站短链并直达客户端对应视频/动态
7. **PiliPlus** (`bilibili.com`) -> 支持第三方开源客户端 PiliPlus 播放
8. **YouTube** (`youtube.com` / `youtu.be`) -> 直达 YouTube / Revanced 播放页面
9. **X (Twitter)** (`twitter.com` / `x.com`) -> 转换为原生推文链接呼起 X 客户端
10. **Telegram** (`t.me` / `telegram.me`) -> 直接呼起 TG 客户端频道或群组
11. **TVBox** (`tvbox://`) -> 电视直播与点播接口导入
12. **新浪微博** (`weibo.com` / `weibo.cn`) -> 直达微博客户端对应博文
13. **知乎** (`zhihu.com` / `zhuanlan.zhihu.com`) -> 自动唤起知乎 App 打开回答或专栏
14. **小红书** (`xiaohongshu.com` / `xhslink.com`) -> 直达小红书笔记页面
15. **抖音** (`douyin.com` / `v.douyin.com`) -> 自动解析抖音短链接呼起抖音
16. **快手** (`kuaishou.com` / `v.kuaishou.com`) -> 呼起快手客户端
17. **酷安** (`coolapk.com`) -> 呼起酷安打开应用或酷安图文
18. **淘宝** (`taobao.com`) -> 自动识别淘口令并跳转淘宝商品页
19. **京东** (`jd.com` / `item.jd.com`) -> 直达京东客户端商品详情
20. **拼多多** (`yangkeduo.com` / `pinduoduo.com`) -> 唤起拼多多
21. **1688** (`1688.com`) -> 唤起 1688 批发采购客户端
22. **闲鱼** (`2.taobao.com` / `goofish.com`) -> 唤起闲鱼商品详情
23. **美团** (`meituan.com`) -> 直达美团本地生活
24. **美团外卖** (`waimai.meituan.com`) -> 直达美团外卖页面
25. **百度贴吧** (`tieba.baidu.com`) -> 直达贴吧主题帖
26. **米游社** (`miyoushe.com`) -> 唤起米哈游米游社
27. **库街区** (`kurobbs.com`) -> 唤起鸣潮/战双库街区

此外还内置了 **2 套无链接 (NoLink) 规则**：
- **ShortX 快捷唤起**: 识别文本指令打开 ShortX 主页面
- **点转义**: 智能将口令中的中文“点”转换为 `.` 进行链接拼装

---

## 🛠 技术架构与技术栈

- **开发语言**: Kotlin 2.0.21
- **Hook 规范**: **LSPosed / LibXposed API 102** (`io.github.libxposed:api:102.0.0`)
  - 采用现代 OkHttp 式 **Interceptor Chain (拦截器链)** 模型：`hook(method).intercept { chain -> ... }`
  - 声明文件：`META-INF/xposed/java_init.list` 与 `scope.list`
- **UI 界面**: Jetpack Compose 2024.11 + Material 3 (动态取色、Edge-to-Edge 边到边全面屏体验)
- **规则引擎**:
  - `LinkExtractor`: 智能提取各种复杂文本中的顶级域名、http/https 链接，自动回溯绑定前后文提取码/密码
  - `RuleEngine`: 完全兼容原版规则语法（`withcode`, `withoutcode`, `contains`, `notcontains`, `All/Any/None` 策略及自定义变量 `Custom_variable` 提取与正则替换）
- **数据与网络**:
  - `Kotlinx Serialization`: 极速 JSON 解析与本地持久化
  - `OkHttp 4.12` + Coroutines: 异步非阻塞拉取 GitHub 云端规则并做本地自定义规则合并

---

## 📂 项目结构

```
FluidCloudX/
├── gradle/libs.versions.toml             # Version Catalog 统一依赖管理
├── settings.gradle.kts
├── build.gradle.kts
└── app/
    ├── build.gradle.kts                  # App 构建配置 (KSP + Compose)
    └── src/main/
        ├── AndroidManifest.xml           # Xposed 元数据与权限
        ├── assets/                       # 内置预置规则
        │   ├── default_rules.json        # 27 条全量社区规则
        │   └── default_nolinkrules.json  # 2 条无链接规则
        ├── res/xml/xposed_scope.xml      # LSPosed 作用域声明
        ├── resources/META-INF/xposed/    # LibXposed API 102 入口与作用域配置
        │   ├── java_init.list            # 模块主入口类声明
        │   └── scope.list                # API 102 推荐 Hook 包名列表
        └── kotlin/com/nyehueh/fluidcloud/
            ├── FluidApp.kt               # Application 入口
            ├── hook/                     # Xposed Hook 核心逻辑
            │   └── ModernFluidCloudModule.kt # 纯正 LibXposed API 102 模块入口与全局拦截器
            ├── core/                     # 核心业务引擎
            │   ├── model/RuleModels.kt   # 规则/配置/条件数据模型
            │   ├── extractor/LinkExtractor.kt # 链接与提取码提取器
            │   ├── engine/RuleEngine.kt  # 规则匹配与变量替换引擎
            │   └── launcher/AppLauncher.kt    # 全屏/小窗精准拉起器
            ├── overlay/
            │   └── FluidOverlayService.kt     # 流体云胶囊悬浮窗与手势处理
            ├── data/
            │   └── RuleRepository.kt     # 规则持久化与 GitHub 云端增量同步
            └── ui/
                ├── MainActivity.kt       # 设置主界面
                ├── theme/Theme.kt        # M3 主题
                └── screens/SettingsScreen.kt  # Compose 设置界面、推荐作用域列表与规则调试沙盒
```

---

## 🚀 安装与编译指南

1. **直接下载安装**:
   - 在 GitHub Releases 页面下载最新发布的 `FluidCloudX-v3.8.1-release.apk`。
2. **在 LSPosed 中激活**:
   - 安装 APK 后打开 **LSPosed 管理器**。
   - 勾选 **流体云 X** 模块，并在作用域中勾选推荐的应用（至少勾选 `系统框架 (android)`）。
   - 重启手机或软重启系统框架生效。
3. **打开流体云主界面**:
   - 授权“显示在其他应用上层”（悬浮窗权限）。
   - 在主界面内可随时测试链接识别，或点击“恢复预置规则” / “云端同步”。
