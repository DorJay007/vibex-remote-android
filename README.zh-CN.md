# VibeX Companion

<p align="center">
  <a href="./README.md">English</a> · 简体中文
</p>

<p align="center">
  <strong>连接 VibeX Host 的 Android 伴随端</strong><br />
  配对桌上的 Host，查看回合、发送跟进，并在 Agent 卡住时拍板。
</p>

<p align="center">
  <a href="https://vibex.com"><img src="https://img.shields.io/badge/Website-vibex.com-111111?style=flat-square" alt="Official website" /></a>
  <a href="https://github.com/Xircth/vibex-companion/releases/latest"><img src="https://img.shields.io/badge/Download-Latest_Release-111111?style=flat-square" alt="Download latest release" /></a>
  <img src="https://img.shields.io/badge/Platform-Android_8%2B-111111?style=flat-square" alt="Android 8 or later" />
  <img src="https://img.shields.io/badge/Protocol-Remote_v1-111111?style=flat-square" alt="Remote Protocol v1" />
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-Apache--2.0-111111?style=flat-square" alt="Apache 2.0 License" /></a>
</p>

<p align="center">
  <a href="https://vibex.com">官网</a> ·
  <a href="https://vibex.com/docs">文档</a> ·
  <a href="https://github.com/Xircth/VibeX">VibeX Host</a> ·
  <a href="https://github.com/Xircth/vibex-companion/releases/latest">下载</a>
</p>

VibeX Companion 是 [VibeX](https://github.com/Xircth/VibeX) Host 家族中的 **Mobile companion**。它是薄的原生 Android 客户端。执行面仍是 Host：Agent、Worktree、终端、插件与 Git 都留在你已经作为 Host 运行的那台机器上。

扫描本机控制台出示的配对邀请后，手机可以阅读会话、发送输入、向在途回合纠偏，并处理权限与提问。Host 不可达时只能读离线缓存。

它不是缩小版桌面应用，不是在手机上跑 ACP，也不是工作站控制台。伴随设备权限不含 Git 写、终端、插件写入、Workflow / Automation 写入，以及 Host 监听 / token / 设备管理。

> [!IMPORTANT]
> **本地运行与数据归属：** Companion 只连接你控制的 Host（VibeX 桌面应用或 `vibex-server`）。设备凭证保存在 Android Keystore。会话历史以 Host 上的事件日志为准。手机只保留只读的离线事件缓存；不在手机上运行 Agent，也不会把项目上传到 VibeX 运营的云端。
>
> **测试阶段：** Companion 仍处于测试阶段。请从 GitHub Releases 侧载 APK，保持 Host 与应用处于匹配的产品版本，并在 Host 上检查 Agent 产生的变更后再提交或合并。
>
> 移动端可以落后 Host 一个次版本。连接时协商能力。

## 能做什么

| 界面 | 作用 |
| --- | --- |
| **文件夹** | 选择这台手机要看的项目，再同步该项目的近期会话。 |
| **会话** | 列出、搜索、置顶并打开会话。用 Host 目录创建新会话。 |
| **时间线** | 折叠 Host 事件日志：用户消息、助手输出、工具、权限、提问。 |
| **状态** | 跨会话汇总等你处理的审批与提问。 |
| **设置** | 连接（置顶）、外观、消息流显示、Host 档案、能力、关于。 |

可以：

- 扫描或粘贴 `vibex-pairing:` 邀请，或手填 origin 与一次性配对码
- 同一 Host 身份再次扫描时只合并 Reachability（邀请里不会出现 `127.0.0.1`）
- 发送、排队、纠偏、取消回合
- 批准或拒绝权限，回答提问
- 使用 Host 已提供的 `/` Skill、结构化 token 与 Agent 提及
- 断网后仍可阅读已缓存的会话

不可以：

- 用管理员 token 代替配对
- 写 Git、开终端、装插件，或改 Host 监听 / FRP / token
- 在手机上运行 Agent、Worktree 或产物工具
- 离线排队写操作

## 配对

1. 在 Host：设置 → Web 服务 → 开始监听。手机不在本机回环时需要允许局域网。创建 **伴随端** 邀请。
2. 在手机：扫描二维码，或粘贴邀请字符串 / origin + 8 位配对码。
3. 确认配对。应用保存长期、可撤销的设备凭证。断开连接不会解除配对；**忘记**会在 Host 可达时请求撤销本设备。

Server Profile 由 Host 身份区分，而不是某条 URL。局域网、Tailscale、FRP 等都可以作为同一 Host 的 Reachability。

Host 侧完整步骤见[连接 Host](https://vibex.com/docs/connect-host)。

## 下载与安装

安装包从 [GitHub Releases](https://github.com/Xircth/vibex-companion/releases/latest) 获取。

| 项目 | 说明 |
| --- | --- |
| 平台 | Android 8.0（API 26）或更高 |
| 包名 | `dev.vibex.companion` |
| 产物 | `.apk` |
| 安装 | 侧载。允许此来源安装后打开 APK。 |

本版本是测试阶段的侧载构建，未上架 Google Play。

需要正在运行、提供 Remote Protocol v1 的 VibeX Host。桌面应用与 `vibex-server` 都可以。Host 默认监听 `127.0.0.1:17891`；手机必须使用自己能打开的 Reachability（局域网或已发布的隧道），不要把 Host 机器上的 loopback 地址抄过来。

## 开发

本仓库是 Gradle 工程：`:companion-core`（协议折叠、配对、Host API）与 `:app`（Jetpack Compose 界面）。`docs/protocol/v1/` 是 VibeX 协议快照；若与上游不一致，以 VibeX `master` 为准。

环境：

- JDK 17
- Android SDK 35（`ANDROID_HOME`）
- 一台正在运行的 VibeX Host，用于配对与联调

```bash
export JAVA_HOME=$HOME/.local/jdk-17
export ANDROID_HOME=$HOME/.local/android-sdk

./gradlew :companion-core:test
./gradlew :app:assembleDebug
```

调试包：`app/build/outputs/apk/debug/app-debug.apk`

```text
app/              Compose 界面、扫码、Keystore、WebSocket
companion-core/   配对、Host 命令、事件折叠、时间线布局
docs/             ADR 快照、伴随端 UI 规格、Remote Protocol v1
```

不要在本仓加入 Host 控制台能力。不要手改 `docs/protocol/v1/generated`。

见 [`CONTEXT.md`](CONTEXT.md)、[`docs/companion/android-frontend.md`](docs/companion/android-frontend.md) 与 [`docs/companion/host-events.md`](docs/companion/host-events.md)。

## 社区

本应用的缺陷与需求请到 [GitHub Issues](https://github.com/Xircth/vibex-companion/issues)。

Host、桌面与协议问题请到 [VibeX](https://github.com/Xircth/VibeX/issues)。

微信群与 QQ 群见 [VibeX README](https://github.com/Xircth/VibeX/blob/master/README.zh-CN.md#社区)。

## 致谢

Companion 消费 VibeX [Remote Protocol](https://github.com/Xircth/VibeX/tree/master/docs/protocol/v1)。Host 上的 Agent 经 [Agent Client Protocol](https://agentclientprotocol.com/) 接入。

VibeX Companion 使用 [Apache License 2.0](./LICENSE) 授权。
