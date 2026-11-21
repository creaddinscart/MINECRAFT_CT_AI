package com.creaddinscart.ctai;

public enum AiCompatibility {
    OPENAI_CHAT("OpenAI Chat Completions", "https://api.openai.com", "gpt-4o-mini", true),
    OPENAI_RESPONSES("OpenAI Responses API", "https://api.openai.com", "gpt-4o-mini", true),
    ANTHROPIC_MESSAGES("Anthropic Messages", "https://api.anthropic.com", "claude-3-5-haiku-latest", true),
    GOOGLE_GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", "gemini-1.5-flash", true),
    OLLAMA("Ollama", "http://localhost:11434", "llama3.1", false),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat", true),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api", "openai/gpt-4o-mini", true),
    GROQ("Groq", "https://api.groq.com/openai", "llama-3.3-70b-versatile", true),
    CUSTOM("Custom / OpenAI-compatible", "http://localhost:8000/v1", "local-model", false);

    private final String displayName;
    private final String defaultHost;
    private final String defaultModel;
    private final boolean requiresApiKey;

    AiCompatibility(String displayName, String defaultHost, String defaultModel, boolean requiresApiKey) {
        this.displayName = displayName;
        this.defaultHost = defaultHost;
        this.defaultModel = defaultModel;
        this.requiresApiKey = requiresApiKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultHost() {
        return defaultHost;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public boolean requiresApiKey() {
        return requiresApiKey;
    }

    public static AiCompatibility fromName(String name) {
        if (name != null) {
            for (AiCompatibility mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
        }
        return OPENAI_CHAT;
    }
}
