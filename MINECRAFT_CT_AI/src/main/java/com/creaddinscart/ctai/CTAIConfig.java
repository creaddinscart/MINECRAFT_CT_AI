package com.creaddinscart.ctai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CTAIConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("minecraft_ct_ai.json");
    private static CTAIConfig instance = new CTAIConfig();

    public String botName = "CREADDINSCART AI BOT";
    public String apiKey = "";
    public String apiHost = "https://api.openai.com";
    public String compatibilityMode = "OPENAI_CHAT";
    public String model = "gpt-4o-mini";
    public String systemPrompt = "You are a helpful assistant inside Minecraft.";
    public double temperature = 0.7;
    public int maxTokens = 512;
    public int timeoutSeconds = 30;
    public boolean completed = false;

    public double uiScale = 1.0;
    public java.util.List<String> availableModels = new java.util.ArrayList<>();

    public boolean agentEnabled = true;
    public int agentMaxSteps = 12;
    public int agentStepDelayMs = 1200;
    public String agentAllowedCommands =
            "time,weather,locate,seed,list,say,me,tell,msg,help,gamerule,title,playsound,particle";

    public static CTAIConfig get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                CTAIConfig loaded = GSON.fromJson(json, CTAIConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } else {
                save();
            }
        } catch (IOException | RuntimeException e) {
            MINECRAFT_CT_AI.LOGGER.error("Failed to load CT AI config: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            Path parent = FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MINECRAFT_CT_AI.LOGGER.error("Failed to save CT AI config: {}", e.getMessage());
        }
    }

    public boolean isUsable() {
        if (botName == null || botName.isBlank() || apiHost == null || apiHost.isBlank()) {
            return false;
        }
        AiCompatibility mode = AiCompatibility.fromName(compatibilityMode);
        return !mode.requiresApiKey() || (apiKey != null && !apiKey.isBlank());
    }

    public static String keyIssue(String key, AiCompatibility mode) {
        if (!mode.requiresApiKey()) {
            return null;
        }
        if (key == null || key.isBlank()) {
            return "An API key is required for this compatibility mode.";
        }
        String value = key.trim();
        if (value.contains("*")) {
            return "The API key looks masked. Paste the full key, not the asterisks shown by the dashboard.";
        }
        if (value.matches(".*\\s+.*")) {
            return "The API key contains spaces or line breaks. Paste it as a single line.";
        }
        if (value.length() < 16) {
            return "The API key is too short. Check that you copied the whole key.";
        }
        return null;
    }

    public static String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return "(none)";
        }
        String value = key.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
