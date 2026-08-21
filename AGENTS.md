# Agents

Read `CONTEXT.md` before changing product behavior.

- This repo is the **Companion Device** Android app only.
- Do not add Host console features (listen, FRP, tokens, MCP, Git write).
- Do not hand-edit `docs/protocol/v1/generated`.
- Event fold must follow `docs/companion/host-events.md`. Unknown kinds stay.
- UI must follow `docs/companion/android-frontend.md`. Bottom destinations stay at three.
- Credentials go in Keystore. No secrets in logs, URLs, or backups.
