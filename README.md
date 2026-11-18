# FluidCloudX (流体云 Xposed 增强版)

本项目是基于原 ShortX 脚本 **“流体云组件 3.8（云端规则版）”** 完全使用**现代 Android 技术栈（Modern Android Development）** 与 **Xposed 框架（YukiHookAPI）** 彻底重构的系统级模块。

---

## 🌟 为什么重写为 Xposed 模块？架构对比

| 维度 | 原 ShortX 脚本 (Rhino JS + Shizuku/Accessibility) | 现代 Xposed 模块 (FluidCloudX) |
| :--- | :--- | :--- |
| **执行环境** | 宿主进程内通过 Rhino / JS 动态 `eval` 解析执行，有解释器开销与类型丢失 | 纯原生 **Kotlin 2.0** 编译为 DEX，极致性能与类型安全 |
| **拦截延迟** | 依赖 Accessibility 或后台轮询，存在明显滞后或触发漏检 | **0 延迟系统级 Hook**：在 `system_server` 的 `startActivity` 和 `setPrimaryClip` 前置拦截 |
| **微信 / QQ 拦截** | 依赖 ActivityStarted 事实感知并异步处理 | 直接注入 QQ / 微信进程，在 `QQBrowserActivity` / `MMWebViewUI` 初始化时即完成抓取并拦截 |
| **小窗控制** | 依赖 ShortX 发送 Shell 命令或受限的反射 | 拥有系统特权直接调用 `ActivityOptions.setLaunchWindowingMode`（兼容 ColorOS Zoom 与原生 Freeform） |
| **界面与配置** | 原生 Android View 手写拼装，编辑繁琐 | **Jetpack Compose + Material 3** 动态取色界面，带实时规则测试沙盒与一键云端同步 |

---

## 🛠 技术架构与技术栈

- **开发语言**: Kotlin 2.0.21
- **Hook 规范**: **LSPosed / LibXposed API 102** (`io.github.libxposed:api:102.0.0`) + YukiHookAPI 兼容层
- **UI 界面**: Jetpack Compose 2024.11 + Material 3 (动态取色、Edge-to-Edge 边到边全面屏体验)
- **规则引擎**:
  - `LinkExtractor`: 智能提取各种复杂文本中的顶级域名、http/https 链接，自动回溯绑定前后文提取码/密码
  - `RuleEngine`: 完全兼容原版规则语法（`withcode`, `contains`, `All/Any/None` 策略及自定义变量 `SynthesisRule`）
- **数据与网络**:
  - `Kotlinx Serialization`: 序列化本地规则与配置
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
        ├── res/xml/xposed_scope.xml      # LSPosed 作用域 (android, QQ, 微信)
        └── kotlin/com/nyehueh/fluidcloud/
            ├── FluidApp.kt               # Application 入口
            ├── hook/                     # Xposed Hook 核心逻辑
            │   ├── HookEntry.kt          # YukiHookAPI 入口 (@InjectYukiHookWithXposed)
            │   ├── SystemServerHook.kt   # Hook 系统剪贴板与全局 Intent
            │   ├── QQHook.kt             # Hook QQ 内置浏览器外链
            │   └── WeChatHook.kt         # Hook 微信内置网页外链
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
                └── screens/SettingsScreen.kt  # Compose 设置界面与规则沙盒
```

---

## 🚀 安装与编译使用指南

1. **编译环境**:
   - Android Studio Koala / Ladybug (或更新版本)
   - JDK 17
   - Android SDK 35 (最低支持 Android 10 / API 29)
2. **构建 APK**:
   ```bash
   ./gradlew assembleDebug
   ```
3. **在 LSPosed 中激活**:
   - 安装生成的 APK。
   - 打开 **LSPosed Manager**，在模块列表中找到 **流体云 X**。
   - 勾选模块并检查推荐作用域：
     - `系统框架 (android)`：负责监听剪切板与系统级外链调度
     - `QQ (com.tencent.mobileqq)`：负责拦截 QQ 内跳外链
     - `微信 (com.tencent.mm)`：负责拦截微信内置网页链接
   - 重启手机或软重启系统框架生效。
