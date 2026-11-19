package com.creaddinscart.ctai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class AiClient {
    private AiClient() {
    }

    public static String ask(CTAIConfig config, String question) throws Exception {
        return send(config, question);
    }

    public static String testConnection(CTAIConfig config) throws Exception {
        return send(config, "Reply with exactly: OK");
    }

    public static java.util.List<String> listModels(CTAIConfig config) throws Exception {
        AiCompatibility mode = AiCompatibility.fromName(config.compatibilityMode);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String host = stripTrailingSlash(config.apiHost);
        String url;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(config.timeoutSeconds))
                .header("Accept", "application/json")
                .GET();

        switch (mode) {
            case OLLAMA -> url = host + "/api/tags";
            case GOOGLE_GEMINI -> url = host + "/v1beta/models?key=" + encode(config.apiKey);
            case ANTHROPIC_MESSAGES -> {
                url = withVersion(host, "/v1") + "/models";
                builder.header("x-api-key", config.apiKey);
                builder.header("anthropic-version", "2023-06-01");
            }
            default -> {
                url = withVersion(host, "/v1") + "/models";
                if (config.apiKey != null && !config.apiKey.isBlank()) {
                    builder.header("Authorization", "Bearer " + config.apiKey);
                }
                if (mode == AiCompatibility.OPENROUTER) {
                    builder.header("HTTP-Referer", "https://github.com/CREADDINSCART/MINECRAFT_CT_AI");
                    builder.header("X-Title", "MINECRAFT_CT_AI");
                }
            }
        }

        builder.uri(URI.create(url));
        HttpResponse<String> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(httpError(response.statusCode(), response.body(), mode, config));
        }

        JsonElement root;
        try {
            root = JsonParser.parseString(response.body());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid model list response from provider");
        }
        if (root == null || !root.isJsonObject()) {
            throw new IllegalStateException("Unexpected model list shape from provider");
        }
        JsonObject object = root.getAsJsonObject();

        java.util.LinkedHashSet<String> models = new java.util.LinkedHashSet<>();
        if (mode == AiCompatibility.OLLAMA) {
            collectIds(models, object.get("models"), "name");
        } else if (mode == AiCompatibility.GOOGLE_GEMINI) {
            collectIds(models, object.get("models"), "name");
        } else {
            collectIds(models, object.get("data"), "id");
        }

        java.util.List<String> result = new java.util.ArrayList<>(models);
        java.util.Collections.sort(result);
        return result;
    }

    private static void collectIds(java.util.Set<String> target, JsonElement array, String field) {
        if (array == null || !array.isJsonArray()) {
            return;
        }
        for (JsonElement element : array.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonElement value = element.getAsJsonObject().get(field);
            if (value != null && value.isJsonPrimitive()) {
                String id = value.getAsString();
                if (id != null && !id.isBlank()) {
                    if (id.startsWith("models/")) {
                        id = id.substring("models/".length());
                    }
                    target.add(id);
                }
            }
        }
    }

    private static String send(CTAIConfig config, String question) throws Exception {
        AiCompatibility mode = AiCompatibility.fromName(config.compatibilityMode);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = buildRequest(mode, config, question);
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(httpError(response.statusCode(), response.body(), mode, config));
        }
        return extractText(mode, response.body(), config);
    }

    private static HttpRequest buildRequest(AiCompatibility mode, CTAIConfig config, String question) {
        String host = stripTrailingSlash(config.apiHost);
        String url;
        JsonObject body = new JsonObject();

        switch (mode) {
            case OPENAI_CHAT:
            case OPENROUTER:
            case GROQ:
            case DEEPSEEK:
            case CUSTOM: {
                url = withVersion(host, "/v1") + "/chat/completions";
                body.addProperty("model", config.model);
                JsonArray messages = new JsonArray();
                messages.add(message("system", config.systemPrompt));
                messages.add(message("user", question));
                body.add("messages", messages);
                body.addProperty("temperature", config.temperature);
                body.addProperty("max_tokens", config.maxTokens);
                break;
            }
            case OPENAI_RESPONSES: {
                url = withVersion(host, "/v1") + "/responses";
                body.addProperty("model", config.model);
                body.addProperty("instructions", config.systemPrompt);
                body.addProperty("input", question);
                body.addProperty("max_output_tokens", config.maxTokens);
                break;
            }
            case ANTHROPIC_MESSAGES: {
                url = withVersion(host, "/v1") + "/messages";
                body.addProperty("model", config.model);
                body.addProperty("system", config.systemPrompt);
                JsonArray messages = new JsonArray();
                messages.add(message("user", question));
                body.add("messages", messages);
                body.addProperty("max_tokens", config.maxTokens);
                body.addProperty("temperature", config.temperature);
                break;
            }
            case GOOGLE_GEMINI: {
                url = host + "/v1beta/models/" + encode(config.model) + ":generateContent?key=" + encode(config.apiKey);
                JsonObject system = new JsonObject();
                JsonArray systemParts = new JsonArray();
                JsonObject systemText = new JsonObject();
                systemText.addProperty("text", config.systemPrompt);
                systemParts.add(systemText);
                system.add("parts", systemParts);
                body.add("system_instruction", system);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", question);
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
                body.add("contents", contents);
                break;
            }
            case OLLAMA: {
                url = host + "/api/chat";
                body.addProperty("model", config.model);
                JsonArray messages = new JsonArray();
                messages.add(message("system", config.systemPrompt));
                messages.add(message("user", question));
                body.add("messages", messages);
                body.addProperty("stream", false);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported compatibility mode: " + mode);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));

        switch (mode) {
            case ANTHROPIC_MESSAGES:
                builder.header("x-api-key", config.apiKey);
                builder.header("anthropic-version", "2023-06-01");
                break;
            case OLLAMA:
            case GOOGLE_GEMINI:
                break;
            default:
                builder.header("Authorization", "Bearer " + config.apiKey);
                if (mode == AiCompatibility.OPENROUTER) {
                    builder.header("HTTP-Referer", "https://github.com/CREADDINSCART/MINECRAFT_CT_AI");
                    builder.header("X-Title", "MINECRAFT_CT_AI");
                }
                break;
        }
        return builder.build();
    }

    private static JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    private static String extractText(AiCompatibility mode, String json, CTAIConfig config) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid response from provider");
        }
        if (!root.isJsonObject()) {
            throw new IllegalStateException("Unexpected response shape from provider");
        }
        JsonObject object = root.getAsJsonObject();

        if (object.has("error")) {
            JsonElement error = object.get("error");
            String message = error.isJsonPrimitive() ? error.getAsString()
                    : errorMessage(error);
            throw new IllegalStateException("Provider error: " + redact(message, config));
        }

        String text = switch (mode) {
            case OPENAI_CHAT, OPENROUTER, GROQ, DEEPSEEK, CUSTOM -> firstString(
                    path(object, "choices", 0, "message", "content"),
                    path(object, "choices", 0, "text"));
            case OPENAI_RESPONSES -> firstString(
                    path(object, "output", 0, "content", 0, "text"),
                    path(object, "output_text"));
            case ANTHROPIC_MESSAGES -> firstString(path(object, "content", 0, "text"));
            case GOOGLE_GEMINI -> firstString(
                    path(object, "candidates", 0, "content", "parts", 0, "text"));
            case OLLAMA -> stringValue(object, "message", "content");
        };

        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Provider returned an empty answer");
        }
        return text;
    }

    private static String errorMessage(JsonElement error) {
        if (error != null && error.isJsonObject()) {
            JsonElement message = error.getAsJsonObject().get("message");
            if (message != null && message.isJsonPrimitive()) {
                return message.getAsString();
            }
        }
        return "unknown error";
    }

    private static String httpError(int status, String body, AiCompatibility mode, CTAIConfig config) {
        String hint = switch (status) {
            case 401 -> "Authentication failed. The API key was rejected. Check that the key is complete (not masked), belongs to " + mode.getDisplayName() + ", is not expired, and the account has credit.";
            case 403 -> "Access denied. Check the key permissions and model access.";
            case 404 -> "Endpoint or model not found. Check the API host and model name.";
            case 429 -> "Rate limited or quota exhausted. Wait and retry, or check your provider quota.";
            default -> status >= 500 ? "Provider server error. Try again later." : "Request rejected by the provider.";
        };
        String detail = redact(providerMessage(body), config);
        return "HTTP " + status + " - " + hint
                + (detail.isEmpty() ? "" : " Provider said: " + trim(detail, 160));
    }

    private static String providerMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return "";
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement detail = object.get("error");
            if (detail != null && detail.isJsonObject()) {
                return errorMessage(detail);
            }
            if (detail != null && detail.isJsonPrimitive()) {
                return detail.getAsString();
            }
            JsonElement message = object.get("message");
            if (message != null && message.isJsonPrimitive()) {
                return message.getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    private static String redact(String text, CTAIConfig config) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String result = text;
        if (config.apiKey != null && !config.apiKey.isBlank()) {
            result = result.replace(config.apiKey, "[REDACTED]");
        }
        result = result.replaceAll("(?i)(api[_-]?key[\"':=\\s]*)[A-Za-z0-9_\\-*]{6,}", "$1[REDACTED]");
        result = result.replaceAll("\\*{2,}[A-Za-z0-9_-]+", "[REDACTED]");
        result = result.replaceAll("\\bsk-[A-Za-z0-9_-]{6,}", "[REDACTED]");
        result = result.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "[REDACTED]");
        return result.replaceAll("\\s+", " ").trim();
    }

    private static JsonElement path(JsonObject root, Object... keys) {
        JsonElement current = root;
        for (Object key : keys) {
            if (current == null || !current.isJsonObject() && !current.isJsonArray()) {
                return null;
            }
            if (key instanceof Integer index) {
                if (!current.isJsonArray() || index >= current.getAsJsonArray().size()) {
                    return null;
                }
                current = current.getAsJsonArray().get(index);
            } else if (key instanceof String name) {
                if (!current.isJsonObject() || !current.getAsJsonObject().has(name)) {
                    return null;
                }
                current = current.getAsJsonObject().get(name);
            }
        }
        return current;
    }

    private static String stringValue(JsonObject root, String... keys) {
        JsonElement element = path(root, (Object[]) keys);
        return element != null && element.isJsonPrimitive()
                ? element.getAsJsonPrimitive().getAsString()
                : null;
    }

    private static String firstString(JsonElement... candidates) {
        for (JsonElement element : candidates) {
            if (element != null && element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isString()) {
                    return primitive.getAsString();
                }
            }
        }
        return null;
    }

    private static String withVersion(String host, String version) {
        String result = stripTrailingSlash(host);
        return result.endsWith(version) ? result : result + version;
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}
