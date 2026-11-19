package com.creaddinscart.ctai;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class CTAICommand {
    private CTAICommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(
                    ClientCommands.literal("ctai")
                            .then(ClientCommands.literal("test")
                                    .executes(context -> test(context.getSource())))
                            .then(ClientCommands.literal("stop")
                                    .executes(context -> stop(context.getSource())))
                            .then(ClientCommands.literal("ask")
                                    .then(ClientCommands.argument("question", StringArgumentType.greedyString())
                                            .executes(context -> ask(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "question")))))
                            .then(ClientCommands.argument("request", StringArgumentType.greedyString())
                                    .executes(context -> route(
                                            context,
                                            StringArgumentType.getString(context, "request"))))
                            .executes(context -> {
                                context.getSource().sendError(Component.literal(
                                        "Usage: /ctai <request> | /ctai ask <question> | /ctai stop | /ctai test | /ctai_stop"));
                                return 0;
                            })
            );
            dispatcher.register(
                    ClientCommands.literal("ctai_stop")
                            .executes(context -> stop(context.getSource()))
            );
        });
    }

    private static int stop(FabricClientCommandSource source) {
        if (!CTAIAgent.isRunning()) {
            source.sendFeedback(Component.literal("[CT AI] No agent is currently running."));
            return 0;
        }
        CTAIAgent.requestStop();
        source.sendFeedback(Component.literal("[CT AI] Stopping the agent..."));
        return 1;
    }

    private static int route(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context,
                             String request) {
        CTAIConfig config = CTAIConfig.get();
        if (config.agentEnabled) {
            return agent(context, request);
        }
        return ask(context.getSource(), request);
    }

    private static int agent(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context,
                             String request) {
        FabricClientCommandSource source = context.getSource();
        CTAIConfig config = CTAIConfig.get();

        if (!config.completed || !config.isUsable()) {
            source.sendError(Component.literal(
                    "[CT AI] Setup is not complete. Open the setup screen from the title screen first."));
            return 0;
        }
        if (request == null || request.isBlank()) {
            source.sendError(Component.literal("[CT AI] Please describe what you want the agent to do."));
            return 0;
        }
        String keyIssue = CTAIConfig.keyIssue(config.apiKey,
                AiCompatibility.fromName(config.compatibilityMode));
        if (keyIssue != null) {
            source.sendError(Component.literal("[CT AI] " + keyIssue));
            return 0;
        }
        if (CTAIAgent.isRunning()) {
            source.sendError(Component.literal("[CT AI] An agent is already running. Use /ctai_stop to stop it first."));
            return 0;
        }
        if (source.getClient().player == null) {
            source.sendError(Component.literal("[CT AI] The agent needs to be in a world."));
            return 0;
        }

        boolean started = CTAIAgent.start(context, request);
        if (!started) {
            source.sendError(Component.literal("[CT AI] The agent could not start."));
            return 0;
        }
        return 1;
    }

    private static int test(FabricClientCommandSource source) {
        CTAIConfig config = CTAIConfig.get();
        if (!config.completed || !config.isUsable()) {
            source.sendError(Component.literal(
                    "[CT AI] Setup is not complete. Open the setup screen from the title screen first."));
            return 0;
        }
        AiCompatibility mode = AiCompatibility.fromName(config.compatibilityMode);
        String keyIssue = CTAIConfig.keyIssue(config.apiKey, mode);
        if (keyIssue != null) {
            source.sendError(Component.literal("[CT AI] " + keyIssue));
            return 0;
        }

        source.sendFeedback(Component.literal("[CT AI] Testing " + mode.getDisplayName()
                + " at " + config.apiHost + " with key " + CTAIConfig.maskKey(config.apiKey) + " ..."));

        Thread worker = new Thread(() -> {
            try {
                String answer = AiClient.testConnection(config);
                String normalized = answer.replaceAll("\\s+", " ");
                final String preview = normalized.length() > 80
                        ? normalized.substring(0, 80) + "..."
                        : normalized;
                source.getClient().execute(() ->
                        source.sendFeedback(Component.literal("[CT AI] Connection OK. Provider replied: " + preview)));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                source.getClient().execute(() ->
                        source.sendError(Component.literal("[CT AI] " + message)));
            }
        }, "ctai-test-worker");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    private static int ask(FabricClientCommandSource source, String question) {
        CTAIConfig config = CTAIConfig.get();

        if (!config.completed || !config.isUsable()) {
            source.sendError(Component.literal(
                    "[CT AI] Setup is not complete. Open the setup screen from the title screen first."));
            return 0;
        }
        if (question == null || question.isBlank()) {
            source.sendError(Component.literal("[CT AI] Please provide a question."));
            return 0;
        }

        source.sendFeedback(Component.literal("[CT AI] " + config.botName + " is thinking..."));

        Thread worker = new Thread(() -> {
            try {
                String answer = AiClient.ask(config, question);
                source.getClient().execute(() -> sendAnswer(source, config, answer));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                source.getClient().execute(() ->
                        source.sendError(Component.literal("[CT AI] Request failed: " + message)));
            }
        }, "ctai-api-worker");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    private static void sendAnswer(FabricClientCommandSource source, CTAIConfig config, String answer) {
        String prefix = "[CT AI] " + config.botName + ": ";
        for (String line : answer.split("\\R")) {
            if (!line.isBlank()) {
                source.sendFeedback(Component.literal(prefix + line));
            }
        }
    }
}
