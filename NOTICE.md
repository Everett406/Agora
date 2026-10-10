# NOTICE

This project is a derivative work of **Agora** (<https://github.com/newo-ether/Agora>),
copyright its respective authors, licensed under the **GNU General Public License v3.0**.

The upstream copyright notice and the full licence text are retained in [LICENSE](LICENSE).
Per GPL-3.0 §5(b), the modifications made here are listed below.

## Modifications

This fork removes the agent platform and keeps a local-model chat client.

**Deleted subsystems**

- Cloud provider adapters: `api/anthropic/`, `api/gemini/`, and the built-in variants in
  `api/openai/` (`OpenAiProvider`, `DeepSeekProvider`, `QwenProvider`, `GroqProvider`,
  `OpenRouterProvider`, `OpenCodeGoProvider`). `CustomOpenAiProvider` is retained so a
  user-defined OpenAI-compatible endpoint (LM Studio, llama.cpp server, vLLM, …) still works.
- The whole tool layer: `tool/`, `mcp/`, `viewmodel/GenerationToolExecutor.kt`,
  `GenerationToolBatchEffectExecutor.kt`, `GenerationToolRoundBuilder.kt`,
  `ToolRoundEffectCoordinator.kt`, `InterruptedToolRounds.kt`, and the multi-round tool loop in
  `viewmodel/GenerationManager.kt`.
- Automation: `automation/` (except `ConversationExecutionCoordinator`, which serializes ordinary
  chat generation and is retained), `ui/tasks/`, `service/TaskWorker.kt`, `LoopWorker.kt`,
  `AutomationForegroundInfo.kt`, `BootReceiver.kt`, and the alarm receiver in the manifest.
- Memory and RAG: `MemoryManager`, `SkillManager`, `EmbeddingIndexer`, `RagManager`,
  `SemanticSearchService`, the embedding cache worker, and the semantic index DAOs.
- Remote sessions: `remote/`, `ui/remote/`, and the Conch encrypted transport
  (`util/Conch*.kt`, `util/Shell*.kt`, `util/SshClient.kt`).
- WebUI: `webui/` and `app/src/main/assets/webui/`.
- Network proxy: `ProxyConfig`/`ProxyType` wiring, `ProxySettingsSynchronizer`, and the proxy
  settings page.
- Sandbox: `sandbox/`, the `fdroid`/`play` sandbox source sets, the `proot` submodule,
  `build-proot.sh`, and the `agora_proot` CMake target.

**Behavioural changes**

- `GenerationApiPathBuilder` sends an empty tool set on every request. Upstream attached tool
  schemas unconditionally, which made local GGUF models fail with "chat template does not support
  tool calling" on the first send. This fork is chat-only, so the gate cannot fire.
- The Room migration `MIGRATION_36_37` (database version 36 → 37) erases the semantic-search
  index and the automation tables. Conversations, messages, attachments, runs, settings and API
  keys are untouched.
- The Settings navigation no longer lists web search, image generation, shell, MCP, automation,
  interaction, proxy, WebUI, memory, skills, or semantic search.
- `AndroidManifest.xml` no longer declares the WebUI service, the automation/boot/shell/ask-user
  receivers, the sandbox documents provider, or the automation-only permissions.

**Trademarks**

The upstream project name, logo and screenshots are not covered by the Agora code licence. As of
v0.1.0 this fork still carries upstream's `applicationId` (`com.newoether.agora`) and its launcher
icon; the in-app display name has been changed to "Local Chat". The launcher icon is being replaced
and a fork-specific `applicationId` is planned before any wider distribution.

This build is not official and is not endorsed by the upstream project.

## Third-party components

- **llama.cpp** — MIT licence, vendored as the `thirdparty/llama.cpp` submodule. Upstream
  redistributed it under the same terms.
- AndroidX, Compose, Room, OkHttp, kotlinx.serialization, Coil, BouncyCastle and the other Gradle
  dependencies keep their own Apache-2.0 / MIT licences; see `app/src/main/assets/licenses/`.
