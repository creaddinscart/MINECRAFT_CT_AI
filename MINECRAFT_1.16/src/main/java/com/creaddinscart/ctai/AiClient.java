package com.creaddinscart.ctai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AiClient {
    private AiClient() {
    }

    public static String ask(CTAIConfig config, String question) throws Exception {
        return send(config, question);
    }

    public static String testConnection(CTAIConfig config) throws Exception {
        return send(config, "Reply with exactly: OK");
    }

    private static Response http(String url, String method, String bodyJson, CTAIConfig config,
                                 AiCompatibility mode) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(config.timeoutSeconds * 1000);
            connection.setReadTimeout(config.timeoutSeconds * 1000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");

            if (bodyJson != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
            }

            switch (mode) {
                case ANTHROPIC_MESSAGES:
                    connection.setRequestProperty("x-api-key", config.apiKey);
                    connection.setRequestProperty("anthropic-version", "2023-06-01");
                    break;
                case OLLAMA:
                case GOOGLE_GEMINI:
                    break;
                default:
                    if (config.apiKey != null && !config.apiKey.isEmpty()) {
                        connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
                    }
                    if (mode == AiCompatibility.OPENROUTER) {
                        connection.setRequestProperty("HTTP-Referer", "https://github.com/CREADDINSCART/MINECRAFT_CT_AI");
                        connection.setRequestProperty("X-Title", "MINECRAFT_CT_AI");
                    }
                    break;
            }

            if (bodyJson != null) {
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(bodyJson.getBytes(StandardCharsets.UTF_8));
                } finally {
                    out.close();
                }
            }

            int status = connection.getResponseCode();
            String responseBody = readBody(connection, status);
            return new Response(status, responseBody);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBody(HttpURLConnection connection, int status) throws Exception {
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
                if (builder.length() > 2_000_000) {
                    break;
                }
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }

    private static String send(CTAIConfig config, String question) throws Exception {
        AiCompatibility mode = AiCompatibility.fromName(config.compatibilityMode);
        String url = buildUrl(mode, config);
        String body = buildBody(mode, config, question);

        Response response = http(url, "POST", body, config, mode);
        if (response.status < 200 || response.status >= 300) {
            throw new IllegalStateException(httpError(response.status, response.body, mode, config));
        }
        return extractText(mode, response.body, config);
    }

    private static String buildUrl(AiCompatibility mode, CTAIConfig config) {
        String host = stripTrailingSlash(config.apiHost);
        switch (mode) {
            case OPENAI_CHAT:
            case OPENROUTER:
            case GROQ:
            case DEEPSEEK:
            case CUSTOM:
                return withVersion(host, "/v1") + "/chat/completions";
            case OPENAI_RESPONSES:
                return withVersion(host, "/v1") + "/responses";
            case ANTHROPIC_MESSAGES:
                return withVersion(host, "/v1") + "/messages";
            case GOOGLE_GEMINI:
                return host + "/v1beta/models/" + encode(config.model) + ":generateContent?key=" + encode(config.apiKey);
            case OLLAMA:
                return host + "/api/chat";
            default:
                throw new IllegalArgumentException("Unsupported compatibility mode: " + mode);
        }
    }

    private static String buildBody(AiCompatibility mode, CTAIConfig config, String question) {
        JsonObject body = new JsonObject();
        switch (mode) {
            case OPENAI_CHAT:
            case OPENROUTER:
            case GROQ:
            case DEEPSEEK:
            case CUSTOM: {
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
                body.addProperty("model", config.model);
                body.addProperty("instructions", config.systemPrompt);
                body.addProperty("input", question);
                body.addProperty("max_output_tokens", config.maxTokens);
                break;
            }
            case ANTHROPIC_MESSAGES: {
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
        return body.toString();
    }

    public static List<String> listModels(CTAIConfig config) throws Exception {
        AiCompatibility mode = AiCompatibility.fromName(config.compatibilityMode);
        String host = stripTrailingSlash(config.apiHost);
        String url;
        switch (mode) {
            case OLLAMA:
                url = host + "/api/tags";
                break;
            case GOOGLE_GEMINI:
                url = host + "/v1beta/models?key=" + encode(config.apiKey);
                break;
            case ANTHROPIC_MESSAGES:
                url = withVersion(host, "/v1") + "/models";
                break;
            default:
                url = withVersion(host, "/v1") + "/models";
                break;
        }

        Response response = http(url, "GET", null, config, mode);
        if (response.status < 200 || response.status >= 300) {
            throw new IllegalStateException(httpError(response.status, response.body, mode, config));
        }

        JsonElement root;
        try {
            root = new JsonParser().parse(response.body);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid model list response from provider");
        }
        if (root == null || !root.isJsonObject()) {
            throw new IllegalStateException("Unexpected model list shape from provider");
        }
        JsonObject object = root.getAsJsonObject();

        Set<String> models = new LinkedHashSet<>();
        if (mode == AiCompatibility.OLLAMA || mode == AiCompatibility.GOOGLE_GEMINI) {
            collectIds(models, object.get("models"), "name");
        } else {
            collectIds(models, object.get("data"), "id");
        }
        List<String> result = new ArrayList<>(models);
        Collections.sort(result);
        return result;
    }

    private static void collectIds(Set<String> target, JsonElement array, String field) {
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
                if (id != null && !id.isEmpty()) {
                    if (id.startsWith("models/")) {
                        id = id.substring("models/".length());
                    }
                    target.add(id);
                }
            }
        }
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
            root = new JsonParser().parse(json);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid response from provider");
        }
        if (!root.isJsonObject()) {
            throw new IllegalStateException("Unexpected response shape from provider");
        }
        JsonObject object = root.getAsJsonObject();

        if (object.has("error")) {
            JsonElement error = object.get("error");
            String message = error.isJsonPrimitive() ? error.getAsString() : errorMessage(error);
            throw new IllegalStateException("Provider error: " + redact(message, config));
        }

        String text;
        switch (mode) {
            case OPENAI_CHAT:
            case OPENROUTER:
            case GROQ:
            case DEEPSEEK:
            case CUSTOM:
                text = firstString(path(object, "choices", 0, "message", "content"),
                        path(object, "choices", 0, "text"));
                break;
            case OPENAI_RESPONSES:
                text = firstString(path(object, "output", 0, "content", 0, "text"),
                        path(object, "output_text"));
                break;
            case ANTHROPIC_MESSAGES:
                text = firstString(path(object, "content", 0, "text"));
                break;
            case GOOGLE_GEMINI:
                text = firstString(path(object, "candidates", 0, "content", "parts", 0, "text"));
                break;
            case OLLAMA:
                text = stringValue(object, "message", "content");
                break;
            default:
                text = null;
                break;
        }

        if (text == null || text.trim().isEmpty()) {
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
        String hint;
        switch (status) {
            case 401:
                hint = "Authentication failed. The API key was rejected. Check that the key is complete (not masked), belongs to " + mode.getDisplayName() + ", is not expired, and the account has credit.";
                break;
            case 403:
                hint = "Access denied. Check the key permissions and model access.";
                break;
            case 404:
                hint = "Endpoint or model not found. Check the API host and model name.";
                break;
            case 429:
                hint = "Rate limited or quota exhausted. Wait and retry, or check your provider quota.";
                break;
            default:
                hint = status >= 500 ? "Provider server error. Try again later." : "Request rejected by the provider.";
                break;
        }
        String detail = redact(providerMessage(body), config);
        return "HTTP " + status + " - " + hint + (detail.isEmpty() ? "" : " Provider said: " + trim(detail, 160));
    }

    private static String providerMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            JsonElement root = new JsonParser().parse(body);
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
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String result = text;
        if (config.apiKey != null && !config.apiKey.isEmpty()) {
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
            if (current == null || (!current.isJsonObject() && !current.isJsonArray())) {
                return null;
            }
            if (key instanceof Integer) {
                int index = (Integer) key;
                if (!current.isJsonArray() || index >= current.getAsJsonArray().size()) {
                    return null;
                }
                current = current.getAsJsonArray().get(index);
            } else if (key instanceof String) {
                if (!current.isJsonObject() || !current.getAsJsonObject().has((String) key)) {
                    return null;
                }
                current = current.getAsJsonObject().get((String) key);
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
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value == null ? "" : value;
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
