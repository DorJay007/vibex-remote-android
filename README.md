# VibeX Companion

<p align="center">
  English · <a href="./README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <strong>Android companion for a VibeX Host</strong><br />
  Pair to the Host on your desk, watch turns, send follow-ups, and approve what is waiting.
</p>

<p align="center">
  <a href="https://vibex.xforever.xin"><img src="https://img.shields.io/badge/Website-vibex.xforever.xin-111111?style=flat-square" alt="Official website" /></a>
  <a href="https://github.com/Xircth/vibex-companion/releases/latest"><img src="https://img.shields.io/badge/Download-Latest_Release-111111?style=flat-square" alt="Download latest release" /></a>
  <img src="https://img.shields.io/badge/Platform-Android_8%2B-111111?style=flat-square" alt="Android 8 or later" />
  <img src="https://img.shields.io/badge/Protocol-Remote_v1-111111?style=flat-square" alt="Remote Protocol v1" />
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-Apache--2.0-111111?style=flat-square" alt="Apache 2.0 License" /></a>
</p>

<p align="center">
  <a href="https://vibex.xforever.xin">Website</a> ·
  <a href="https://vibex.xforever.xin/docs">Docs</a> ·
  <a href="https://github.com/Xircth/VibeX">VibeX Host</a> ·
  <a href="https://github.com/Xircth/vibex-companion/releases/latest">Download</a>
</p>

VibeX Companion is the **Mobile companion** in the [VibeX](https://github.com/Xircth/VibeX) Host family. It is a thin native Android client. The Host remains the execution plane: agents, worktrees, terminals, plugins, and Git all stay on the machine you already run as Host.

Scan a pairing invitation on the Host console. The phone then reads conversations, sends input, steers an in-flight turn, and answers permission or question prompts. Without a reachable Host it only reads the offline cache.

This is not a copy of the desktop app, not ACP-on-phone, and not a workstation console. Companion Device permission does not include Git write, terminal, plugin writes, Workflow / Automation writes, or Host listen / token / device administration.

> [!IMPORTANT]
> **Local operation and data ownership:** Companion talks only to a Host you control (the VibeX desktop app or `vibex-server`). Device credentials stay in Android Keystore. Conversation history is authoritative on the Host. The phone keeps a read-only offline cache of event sequences; it does not run agents and does not upload your projects to a VibeX-operated cloud.
>
> **Testing-stage notice:** Companion is still in testing. Sideload the APK from GitHub Releases, keep the Host on a matching product version, and review agent-generated changes on the Host before you commit or merge them.
>
> The mobile app may be one minor version behind the Host. Capabilities are negotiated at connect time.

## What it does

| Surface | Role |
| --- | --- |
| **Folders** | Pick the project whose recent conversations you want on this phone. |
| **Sessions** | List, search, pin, and open conversations. Create a new session with the Host catalog. |
| **Timeline** | Fold the Host event log: user messages, assistant output, tools, permissions, questions. |
| **Status** | Pending approvals and questions that need you, across conversations. |
| **Settings** | Connection (first), appearance, stream display, Host profile, capabilities, about. |

You can:

- Scan or paste a `vibex-pairing:` invitation, or type origin + one-time pairing code
- Merge Reachability when the same Host identity is scanned again (`127.0.0.1` is never in an invitation)
- Send, queue, steer, and cancel turns
- Approve or deny permissions and answer questions
- Use `/` Skills, structured tokens, and agent mentions the Host already knows
- Keep reading a conversation after the network drops, from the offline cache

You cannot:

- Paste an admin token in place of pairing
- Write Git, open a terminal, install plugins, or change Host listen / FRP / tokens
- Run an agent, a worktree, or artifact tooling on the phone
- Queue writes while offline

## Pair

1. On the Host: Settings → Web service → start listening. Allow LAN if the phone is not on the same loopback. Create a **Companion** invitation.
2. On the phone: scan the QR code, or paste the invitation string / origin + 8-character code.
3. Approve the pairing. The app stores a long-lived, revocable device credential. Disconnect does not unpair; **Forget** revokes the device when the Host is reachable.

The Host identity, not a URL, is what a Server Profile represents. Several origins (LAN, Tailscale, FRP, and similar) can point at the same Host.

Full Host-side steps: [Connect a Host](https://vibex.xforever.xin/docs/connect-host).

## Download and installation

Installers come from [GitHub Releases](https://github.com/Xircth/vibex-companion/releases/latest).

| Item | Value |
| --- | --- |
| Platform | Android 8.0 (API 26) or later |
| Package | `dev.vibex.companion` |
| Artifact | `.apk` |
| Installation | Sideload. Enable install from this source, then open the APK. |

This release is a testing-stage sideload build. It is not distributed through Google Play.

A running VibeX Host with Remote Protocol v1 is required. Desktop and `vibex-server` both work. The default Host listen address is `127.0.0.1:17891`; the phone needs a Reachability origin it can actually open (LAN or a published tunnel), never loopback copied from the Host machine.

## Development

This repository is a Gradle project: `:companion-core` (protocol fold, pairing, Host API) and `:app` (Jetpack Compose UI). Wire models under `docs/protocol/v1/` are snapshots of VibeX; VibeX `master` wins if they drift.

Prerequisites:

- JDK 17
- Android SDK 35 (`ANDROID_HOME`)
- A running VibeX Host for pairing and live tests

```bash
export JAVA_HOME=$HOME/.local/jdk-17
export ANDROID_HOME=$HOME/.local/android-sdk

./gradlew :companion-core:test
./gradlew :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

```text
app/              Compose UI, pairing camera, Keystore, WebSocket
companion-core/   Pairing, Host commands, event fold, timeline layout
docs/             ADR snapshots, companion UI spec, Remote Protocol v1
```

Do not add Host console features here. Do not hand-edit `docs/protocol/v1/generated`.

See [`CONTEXT.md`](CONTEXT.md), [`docs/companion/android-frontend.md`](docs/companion/android-frontend.md), and [`docs/companion/host-events.md`](docs/companion/host-events.md).

## Community

Bug reports for this app: [GitHub Issues](https://github.com/Xircth/vibex-companion/issues).

Host, desktop, and protocol issues belong in [VibeX](https://github.com/Xircth/VibeX/issues).

WeChat and QQ groups are listed on the [VibeX README](https://github.com/Xircth/VibeX#community).

## Acknowledgements

Companion consumes the VibeX [Remote Protocol](https://github.com/Xircth/VibeX/tree/master/docs/protocol/v1). Agents on the Host enter through the [Agent Client Protocol](https://agentclientprotocol.com/).

VibeX Companion is licensed under the [Apache License 2.0](./LICENSE).
