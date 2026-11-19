# MINECRAFT_CT_AI

Bring your own AI into **Minecraft Java Edition 26.3 (Fabric)**.

* **Mod ID**: `minecraft_ct_ai` | **Version**: `1.0.2` | **Environment**: Client-only | **License**: MIT
* **Core Command**: `/ctai <request>` | **Stop Agent**: `/ctai_stop`

---

## 1. Overview

`MINECRAFT_CT_AI` is a client-side Fabric mod that connects your own AI API directly to Minecraft chat. No server installation is needed, and your API keys stay strictly on your local machine.

* **9 Compatibility Modes**: Supports OpenAI, Anthropic, Gemini, Ollama, DeepSeek, OpenRouter, Groq, and OpenAI-compatible local endpoints.
* **First-Run Setup**: Prompts you to configure your bot name, API key, host, and model on first launch (also accessible anytime via the **CT AI Setup** button on the title screen). The screen auto-fits any window size and offers a manual **UI Scale** button (70-150%).
* **Model Scanning**: A **Scan Models** button discovers all available models from your API host; **Models (n)** cycles through them.
* **Agent Mode**: The AI can operate the game in real time using whitelisted Minecraft commands based on your plain-language request. Stop it any time with `/ctai_stop`.

---

## 2. Quick Start

### Installation
1. Ensure **Java 25** and **Fabric Loader 0.19.5+** (for Minecraft 26.3) are installed.
2. Place **Fabric API** and `MINECRAFT_CT_AI-1.0.2.jar` into your `mods` folder:
   * **Windows**: `%appdata%\.minecraft\mods`
   * **macOS**: `~/Library/Application Support/minecraft/mods`
   * **Linux**: `~/.minecraft/mods`

### Setup & Usage
1. Launch Minecraft and complete the **CREADDINSCART AI BOT** setup screen (Bot Name, API Key, Host, Model, System Prompt, Temperature, Max Tokens, Timeout, Compatibility Mode). Use **Scan Models** to auto-fill available models and **Agent ON/OFF** to control agent mode.
2. In-game, type `/ctai <request>` in chat. With the agent ON (default), the AI will act on your request using safe commands. Use `/ctai ask <question>` for a plain answer with no game actions.
3. Type `/ctai_stop` any time to stop a running agent instantly.

---

## 3. Supported Providers

| Mode | Default Host | Endpoint |
| :--- | :--- | :--- |
| **OpenAI Chat Completions** | `https://api.openai.com` | `/chat/completions` |
| **OpenAI Responses API** | `https://api.openai.com` | `/responses` |
| **Anthropic Messages** | `https://api.anthropic.com` | `/v1/messages` |
| **Google Gemini** | `https://generativelanguage.googleapis.com` | `/v1beta/models/...` |
| **Ollama** (Local, no key) | `http://localhost:11434` | `/api/chat` |
| **DeepSeek** | `https://api.deepseek.com` | `/chat/completions` |
| **OpenRouter** | `https://openrouter.ai/api` | `/chat/completions` |
| **Groq** | `https://api.groq.com/openai` | `/chat/completions` |
| **Custom / OpenAI-compatible** | `http://localhost:8000/v1` | `/chat/completions` |

---

## 4. Configuration & Troubleshooting

Settings are saved locally at `<minecraft config dir>/minecraft_ct_ai.json`. Delete this file to reset to defaults. *(Security note: API keys are stored in plain text—do not share or commit this file.)*

* **HTTP 401**: Invalid or expired API key.
* **HTTP 404**: Incorrect API host URL or model name.
* **HTTP 429**: Provider rate limit exceeded.
* **Connection refused**: Ensure your local server (e.g., Ollama, LM Studio) is running on the right port.

---

## 5. Developer Info

* **Toolchain**: Minecraft 26.3 (Mojang mappings), Java 25, Gradle 9.6.0, Fabric Loom 1.17.21, Fabric API `0.160.7+26.3`.
* **Build**: Run `./gradlew build` (outputs to `build/libs/MINECRAFT_CT_AI-1.0.2.jar`) or `./gradlew runClient` to test.
* **Architecture**: `CTAIConfig` handles JSON persistence; `TitleScreenMixin` gates the initial setup; `CTAICommand` dispatches async `java.net.http` calls via `AiClient` and posts replies back to the render thread via `Minecraft.execute()`. `CTAIAgent` runs the game-control loop with game-context capture, a JSON decision protocol, and a command whitelist. `SetupScreen` uses a responsive layout with manual UI scaling.

---

## 6. Roadmap & Credits

* **Shipped in 1.0.2**: Responsive/scalable setup screen, automatic model discovery, and agent mode with `/ctai_stop`.
* **Planned Features**: Streaming SSE replies, screenshot/vision input, richer agent context (biome/inventory/entities), chat history panel, encrypted key storage, and Mod Menu integration.
* **Credits**: Designed and developed by **CREADDINSCART** under the **MIT License**.
