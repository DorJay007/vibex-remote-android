# VibeX Companion (Android)

Android 上的 VibeX **Mobile companion**：连接在线 VibeX Host，观察并控制远端
会话。它不运行 Agent、Git worktree 或 Artifact 工具。

协议、Host 身份、Reachability 与能力面的权威在 VibeX 仓库。本仓只实现
Companion 客户端。

## Requirements

1. 用户扫描本机控制台出示的配对邀请即可配对；无相机时可手填 origin 与一次性 pairing token。禁止粘贴管理员 token。
2. 一个 Server Profile 对应一个 Host 身份，可有多条 Reachability。已配对后再扫邀请只合并地址。
3. 连接态必须持续可见：connecting / online / recovering / offline / auth_required / incompatible。非 online 禁止写。
4. 时间线只折叠 Host 事件日志。未知 kind 保留。不得把缺失用量填成零，不得自动重发中断 Turn。
5. 用户能发送、排队、纠偏、取消、批准或拒绝权限、回答提问。不能写 Git、开终端、改插件或 Host 监听。
6. 没有 Host 时只能读离线缓存。P1 不接 FCM。持续监控须用户显式打开。
7. UI 按 `docs/companion/android-frontend.md`：三栏「会话 / 待办 / Host」，信号条是主状态。
8. Device credential 进 Android Keystore。备份、日志、崩溃报告不含凭证。
9. Wire 模型来自 VibeX `docs/protocol/v1`；本仓不手写第二份协议权威。
10. 技术：Kotlin、Jetpack Compose。不引入跨平台 UI。iOS 不在本仓。

## Language

### Host and pairing

**VibeX Host**:
The running owner of one VibeX data directory. The phone never becomes a Host.
_Avoid_: server (alone), backend, Codeg server

**Host identity**:
The stable id of that data directory. Profiles merge on this id, not on URL.
_Avoid_: baseUrl, server address as identity

**Host console**:
The admin surface on the machine running Host. This app is not the Host console.
_Avoid_: settings (when meaning listen / token / FRP)

**Reachability**:
One origin that can reach the same Host. A profile may have many.
_Avoid_: connection, tunnel (as the profile itself)

**Pairing invitation**:
A short-lived offer: Host identity, preset, all current Reachability, and a one-time secret for unpaired devices.
_Avoid_: QR (the invitation is the artifact; QR is one presentation), CODEG_TOKEN

**Device pairing**:
Redeeming an invitation into a long-lived, revocable device credential.
_Avoid_: login, API key, admin token

**Paired device**:
This installation's identity on one Host. Disconnect does not unpair.
_Avoid_: user account

**Companion Device**:
The permission preset of this app: session read/write, approve, steer, cancel, read-only artifact, offline cache.
_Avoid_: workstation, full remote desktop

**Server profile**:
This app's local record of one Host: identity, name, Reachability list, last successful origin. Secrets stay in Keystore.
_Avoid_: server (the form fields)

**Forget server**:
Delete the local profile and credential; revoke this device when the Host is reachable.
_Avoid_: logout, disconnect (those keep the pairing)

### Session and events

**Conversation**:
The durable dialogue with one agent, whose history is the event log.
_Avoid_: chat (as the domain name), session (when meaning Conversation)

**Turn**:
One user-start to agent-terminal cycle. At most one in flight per Conversation.

**Event log**:
The append-only authority for a Conversation. The phone folds it; it does not own it.
_Avoid_: websocket messages (as authority)

**Timeline row**:
The smallest independently updated line on screen, with a stable id and revision.

**Durable attach**:
ready → snapshot/replay → high-water → live, keyed by sequence.

**Queued conversation input**:
A persisted user intent not yet bound to a Turn.

**Turn steering**:
Guidance appended to a specific in-flight Turn. It is not a new Turn and must not silently become a queued input.

**Pending request**:
A permission, question, or blocked turn waiting on this user. It is what 待办 lists.

**Offline conversation cache**:
Read-only events through a confirmed sequence. Writes cannot queue while offline.

**Terminal notification summary**:
Ids, outcome, time, operation id. No prompt, output, or path.

## Sources

Copied snapshots live under `docs/`. If they disagree with VibeX `main`, VibeX wins; refresh the snapshot.

- Pairing and Reachability: `docs/adr/0059-host-identity-and-pairing-invitation.md`, `docs/plans/2026-08-18-host-reachability-and-paired-clients.md`
- Events: `docs/companion/host-events.md`
- UI: `docs/companion/android-frontend.md`
- Scopes: `docs/adr/0054-host-family-distribution-and-client-surfaces.md`
- Protocol: `docs/protocol/v1/`

## Environment

Local toolchain (not committed):

- JDK 17: `~/.local/jdk-17` (`export JAVA_HOME=$HOME/.local/jdk-17`)
- Android SDK 35: `~/.local/android-sdk` (`export ANDROID_HOME=$HOME/.local/android-sdk`)
- `local.properties` points at that SDK. Do not commit it.

```bash
export JAVA_HOME=$HOME/.local/jdk-17
export ANDROID_HOME=$HOME/.local/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew :companion-core:test
./gradlew :app:assembleDebug
```

## Agent Skills

Use the smallest set that covers the task. Product rules in this file always win over a skill's generic Android advice (no Git write, no Host console, no second protocol).

### Always / already installed for VibeX work

| Skill | When to use it here |
|---|---|
| `maiden-skill` | Every change. Complete pairing → timeline → approve loops; no leftover demo paths. |
| `frontend-design` | Changing visual identity, type, or the signal bar / sequence rail. Follow `docs/companion/android-frontend.md`. |
| `impeccable` | Auditing hierarchy, empty/error states, copy. Do not add Git/MCP surfaces. |
| `tdd` | New fold/pairing/origin behavior: fail a `companion-core` test first. |
| `diagnosing-bugs` | Pairing, WebSocket attach, or reconnect failures. Start at Host origin and sequence, not UI. |
| `domain-modeling` | New terms (Reachability, invitation). Update this file; do not invent "connection" as Host identity. |
| `code-review` | Before calling a slice done. Check scopes, Keystore, unknown event kinds. |
| `grilling` | Changing pairing, presets, or client topology. |
| `find-skills` | Looking for more Android/Kotlin/emulator skills. |

### Ecosystem (install when that work starts)

Verified via `npx skills find` on 2026-08-18. Prefer official / high-install sources.

| Skill | Installs | When to use it here | Install |
|---|---|---|---|
| [mobile-android-design](https://skills.sh/wshobson/agents/mobile-android-design) | 20.8K | Touch targets, TalkBack, system bars, Material motion vs this app's two animations. | `npx skills add wshobson/agents@mobile-android-design -g -y` |
| [argent-android-emulator-setup](https://skills.sh/software-mansion/argent/argent-android-emulator-setup) | 12.2K | Creating an AVD to exercise scan / LAN pair / approve. | `npx skills add software-mansion/argent@argent-android-emulator-setup -g -y` |
| [android-cli](https://skills.sh/android/skills/android-cli) | 1.4K | `sdkmanager`, `adb install`, logcat. Official Android. | `npx skills add android/skills@android-cli -g -y` |
| [testing-setup](https://skills.sh/android/skills/testing-setup) | 1K | Adding instrumented tests for Compose screens. Official Android. | `npx skills add android/skills@testing-setup -g -y` |
| [kotlin-coroutines-structured-concurrency](https://skills.sh/chrisbanes/skills/kotlin-coroutines-structured-concurrency) | 1.1K | `CompanionModel` connect / attach / ping jobs leaking or overlapping. | `npx skills add chrisbanes/skills@kotlin-coroutines-structured-concurrency -g -y` |
| [android-intent-security](https://skills.sh/android/skills/android-intent-security) | 527 | Adding `vibex-pairing:` deep links. Official Android. Lower installs; still official. | `npx skills add android/skills@android-intent-security -g -y` |

Do not use Expo / Clerk / ads / pentest skills on this repo. They fight Companion scope or pull in the wrong stack.

Do not use Kotlin Multiplatform UI skills to share screens with iOS. Share Remote Protocol and fixtures only (ADR-0041).
