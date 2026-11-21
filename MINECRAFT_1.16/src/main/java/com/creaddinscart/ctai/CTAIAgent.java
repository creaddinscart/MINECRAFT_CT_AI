package com.creaddinscart.ctai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.TextComponent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class CTAIAgent {
    private static volatile boolean running = false;
    private static volatile boolean stopRequested = false;
    private static volatile Thread worker = null;

    private CTAIAgent() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void requestStop() {
        stopRequested = true;
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public static boolean start(CommandContext<FabricClientCommandSource> context, String request) {
        if (running) {
            return false;
        }
        CTAIConfig base = CTAIConfig.get();
        if (!base.agentEnabled) {
            return false;
        }
        FabricClientCommandSource source = context.getSource();
        final Minecraft client = source.getClient();
        running = true;
        stopRequested = false;

        Consumer<String> say = message -> client.execute(() ->
                source.sendFeedback(new TextComponent("[CT AI] " + message)));
        Consumer<String> warn = message -> client.execute(() ->
                source.sendError(new TextComponent("[CT AI] " + message)));

        Thread thread = new Thread(() -> run(client, base, request, say, warn), "ctai-agent-worker");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
        return true;
    }

    private static void run(Minecraft client, CTAIConfig base, String request,
                            Consumer<String> say, Consumer<String> warn) {
        int maxSteps = Math.max(1, Math.min(base.agentMaxSteps, 40));
        long delay = Math.max(0L, base.agentStepDelayMs);
        Set<String> allowed = parseAllowed(base.agentAllowedCommands);
        String lastResult = "";

        try {
            say.accept("Agent started for: " + request);
            for (int step = 1; step <= maxSteps; step++) {
                if (stopRequested || Thread.currentThread().isInterrupted()) {
                    break;
                }

                String contextText = captureContext(client);
                CTAIConfig stepConfig = copyForAgent(base, request, contextText, lastResult, allowed);

                String raw;
                try {
                    raw = AiClient.ask(stepConfig, "Execute the next step now.");
                } catch (Exception e) {
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    warn.accept("Agent step " + step + " failed: " + message);
                    break;
                }

                AgentReply reply = parseReply(raw);
                if (reply == null) {
                    warn.accept("Agent step " + step + " returned an unreadable reply.");
                    lastResult = "Your previous reply was not valid JSON. Return only the JSON object.";
                    intWait(delay);
                    continue;
                }

                if (reply.message != null && !reply.message.trim().isEmpty()) {
                    say.accept(reply.message);
                }

                StringBuilder result = new StringBuilder();
                if (!reply.commands.isEmpty()) {
                    executeCommands(client, reply.commands, allowed, say, warn, result);
                } else {
                    result.append("No commands issued.");
                }
                lastResult = result.toString();

                if (reply.done) {
                    say.accept("Agent finished after " + step + " step(s).");
                    break;
                }
                if (step == maxSteps) {
                    warn.accept("Agent reached the step limit (" + maxSteps + "). Use /ctai_stop to stop it if needed.");
                }
                intWait(delay);
            }
        } finally {
            String end = stopRequested ? "Agent stopped." : "Agent ended.";
            say.accept(end);
            running = false;
            worker = null;
            stopRequested = false;
        }
    }

    private static void executeCommands(Minecraft client, List<String> commands, Set<String> allowed,
                                        Consumer<String> say, Consumer<String> warn, StringBuilder result) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                LocalPlayer player = client.player;
                if (player == null) {
                    warn.accept("Agent cannot run commands: no player in world.");
                    result.append("No player in world.");
                    future.complete(null);
                    return;
                }
                for (String entry : commands) {
                    if (stopRequested) {
                        break;
                    }
                    String command = entry == null ? "" : entry.trim();
                    while (command.startsWith("/")) {
                        command = command.substring(1);
                    }
                    if (command.trim().isEmpty()) {
                        continue;
                    }
                    String token = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                    if (!allowed.contains(token)) {
                        warn.accept("Blocked command not on whitelist: " + token);
                        result.append("Command ").append(token).append(" was blocked by the whitelist. ");
                        continue;
                    }
                    player.chat("/" + command);
                    say.accept("Ran: /" + command);
                    result.append("Ran /").append(command).append(". ");
                }
            } catch (Exception e) {
                warn.accept("Failed to run an agent command: " + e.getMessage());
                result.append("A command failed. ");
            } finally {
                future.complete(null);
            }
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static String captureContext(Minecraft client) {
        CompletableFuture<String> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                LocalPlayer player = client.player;
                if (player == null) {
                    future.complete("No player is currently in a world.");
                    return;
                }
                StringBuilder builder = new StringBuilder();
                builder.append("Position: ")
                        .append(String.format(Locale.ROOT, "%.1f, %.1f, %.1f",
                                player.getX(), player.getY(), player.getZ())).append(". ");
                builder.append("Dimension: ")
                        .append(player.level.dimension().location().toString()).append(". ");
                builder.append("Health: ")
                        .append(String.format(Locale.ROOT, "%.1f / %.1f",
                                player.getHealth(), player.getMaxHealth())).append(". ");
                builder.append("Raining: ")
                        .append(player.level.isRaining()).append(".");
                future.complete(builder.toString());
            } catch (Exception e) {
                future.complete("Game context is temporarily unavailable.");
            }
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "Game context is temporarily unavailable.";
        }
    }

    private static CTAIConfig copyForAgent(CTAIConfig base, String request, String contextText,
                                           String lastResult, Set<String> allowed) {
        CTAIConfig config = new CTAIConfig();
        config.botName = base.botName;
        config.apiKey = base.apiKey;
        config.apiHost = base.apiHost;
        config.compatibilityMode = base.compatibilityMode;
        config.model = base.model;
        config.temperature = 0.2;
        config.maxTokens = Math.max(base.maxTokens, 600);
        config.timeoutSeconds = base.timeoutSeconds;
        config.completed = true;
        config.systemPrompt = buildAgentSystemPrompt(request, contextText, lastResult, allowed);
        return config;
    }

    private static String buildAgentSystemPrompt(String request, String contextText,
                                                 String lastResult, Set<String> allowed) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are CREADDINSCART AI BOT in AGENT MODE inside Minecraft. ");
        builder.append("You can control the game only by issuing whitelisted Minecraft commands. ");
        builder.append("Game context: ").append(contextText).append("\n");
        builder.append("Player request: ").append(request).append("\n");
        builder.append("Allowed command names (first word only): ").append(join(allowed)).append("\n");
        if (lastResult != null && !lastResult.trim().isEmpty()) {
            builder.append("Result of your previous step: ").append(lastResult).append("\n");
        }
        builder.append("Respond with ONLY one JSON object, no markdown and no extra text, in this format:\n");
        builder.append("{\"message\":\"short text shown to the player\",\"commands\":[\"cmd one\",\"cmd two\"],\"done\":false}\n");
        builder.append("Rules: commands are strings without a leading slash; use only allowed command names; ");
        builder.append("use an empty array when no command is needed; set done to true only when the request is fully satisfied.");
        return builder.toString();
    }

    private static String join(Set<String> set) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String value : set) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(value);
            first = false;
        }
        return builder.toString();
    }

    private static AgentReply parseReply(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonElement element = new JsonParser().parse(text.substring(start, end + 1));
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            AgentReply reply = new AgentReply();
            JsonElement message = object.get("message");
            if (message != null && message.isJsonPrimitive()) {
                reply.message = message.getAsString();
            }
            JsonElement done = object.get("done");
            reply.done = done != null && done.isJsonPrimitive() && done.getAsBoolean();
            JsonElement commands = object.get("commands");
            if (commands != null && commands.isJsonArray()) {
                JsonArray array = commands.getAsJsonArray();
                for (JsonElement item : array) {
                    if (item != null && item.isJsonPrimitive()) {
                        reply.commands.add(item.getAsString());
                    }
                }
            }
            return reply;
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<String> parseAllowed(String value) {
        Set<String> set = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return set;
        }
        for (String part : value.split(",")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty()) {
                set.add(token);
            }
        }
        return set;
    }

    private static void intWait(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class AgentReply {
        String message = "";
        boolean done = false;
        final List<String> commands = new ArrayList<>();
    }
}
