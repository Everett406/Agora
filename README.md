
> [!NOTE]
> **本仓库已迁移至 Gitee**：<https://gitee.com/Everett5678/Agora>
> GitHub 端仅作只读归档，新代码请推送到 Gitee。

---
# Local Chat (a fork of Agora)

**A stripped-down, local-model chat-only Android client forked from
[newoether/Agora](https://github.com/newoether/Agora).**

This is **not** an official Agora build. It is an independent derivative that shares no affiliation
with, and is not endorsed by, the upstream project or its author.

---

## What this is

Upstream Agora is a BYOK *agent platform*: cloud LLM providers, MCP servers, tool calling,
scheduled automation, a memory/RAG subsystem, an embedded WebUI server, remote device control, and
a PRoot sandbox. This fork removes all of that and keeps one thing:

> **A private, local chat client** — GGUF models on-device via llama.cpp, an Ollama / any
> OpenAI-compatible endpoint on your own machine, the streaming Markdown renderer, branching
> conversations, context budgeting, attachments, and import/export.

### Removed

| Subsystem | What went |
|---|---|
| (residuals) | `TaskEntity`, `LoopEntity` and `EmbeddingEntity` remain in the Room schema so existing databases still migrate; the `MIGRATION_36_37` migration clears their contents and no code reads them. |
| Cloud providers | OpenAI, Anthropic, Gemini, DeepSeek, Qwen, Groq, OpenRouter, OpenCode Go |
| Agent / tool layer | every `ToolProvider`, the tool executor, tool rounds in the generation loop |
| MCP | client, protocol, registry, settings |
| Automation | Tasks, Loops, cron scheduler, WorkManager runners, boot receiver |
| Memory & RAG | `MemoryManager`, skills, embedding index, semantic search, cache worker |
| Tools | web search, image generation, remote shell/files, sandbox, Conch encrypted transport |
| Remote | Filo/Conch remote sessions and their screen |
| WebUI | the embedded local web server and its Preact assets |
| Network proxy | HTTP/SOCKS settings, `Authenticator` wiring, settings page |
| Sandbox | PRoot runtime, `agora_proot` native target, the `proot` submodule, `build-proot.sh` |

### Kept

Local llama.cpp chat (with vision via `mmproj`), Ollama and user-defined OpenAI-compatible
endpoints, the tree-structured conversation model, context budgets and Compact capsules,
streaming Markdown/LaTeX rendering, thinking/reasoning display, attachments (image, file, camera),
`.agora` archives and import/export, automatic backup, theming and localization.

### The agent/tool gate is gone by construction

Upstream always attached tool schemas to every request, so a GGUF whose chat template lacks
`supports_tools` failed on the first send with *"The local model chat template does not support
tool calling."* This fork never advertises tools: `GenerationApiPathBuilder` sends an empty tool
set on every request, so there is no capability check left to fail. Local models are chat models
here, full stop.

---

## Build

JDK 21, Android SDK 36, NDK 28.2.13676358, CMake ≥ 3.22. The only submodule is `thirdparty/llama.cpp`.

```bash
git clone --recurse-submodules <this-repo>
cd Agora
./gradlew :app:assembleFdroidRelease
```

If `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` repository secrets are
set, CI signs the release build; otherwise it falls back to debug signing.

### GitHub Actions

`.github/workflows/build.yml` runs unit tests, then builds the F-Droid release APK. Pushing a tag
matching `v*` publishes a GitHub Release with the APK attached:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

---

## Licence

**GNU General Public License v3.0.** See [LICENSE](LICENSE).

This fork is a derivative of Agora, which was relicensed to GPL-3.0 from v2.2.0 onward after an
unauthorized re-release incident; the tree this fork started from is GPL-3.0. Under that licence:

- the upstream copyright and licence notices are retained in [LICENSE](LICENSE) and
  [NOTICE.md](NOTICE.md);
- **this fork modifies the source** — see [NOTICE.md](NOTICE.md) for the change list;
- the complete corresponding source is this repository, published under the same licence;
- the upstream **name, logo and screenshots are not covered by the code licence**. This fork ships
  its own `applicationId` (`io.github.everett406.localchat`) and is signed with a different key, so
  it installs as a separate app alongside upstream's. Upstream's launcher icon is still in use at
  v0.1.1 (see [NOTICE.md](NOTICE.md)); neither is reused as a claim of origin, and this app must
  not be presented as official or endorsed by the upstream project.

The bundled llama.cpp submodule keeps its own licence (MIT); the Termux/PRoot components that
upstream vendored for its sandbox are gone from this fork.
