package com.creaddinscart.ctai;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SetupScreen extends Screen {
    private static final int FIELD_HEIGHT = 20;
    private static final int LABEL_WIDTH = 118;
    private static final int GAP = 8;
    private static final int MARGIN = 8;
    private static final double[] SCALE_STEPS = {0.70, 0.85, 1.0, 1.15, 1.30, 1.50};

    private final Screen parent;

    private Layout layout;
    private EditBox botNameField;
    private EditBox apiKeyField;
    private EditBox apiHostField;
    private EditBox modelField;
    private EditBox promptField;
    private EditBox temperatureField;
    private EditBox maxTokensField;
    private EditBox timeoutField;

    private Button compatibilityButton;
    private Button agentButton;
    private Button detectButton;
    private Button scanButton;
    private Button modelButton;
    private Button testButton;
    private Button scaleButton;

    private int compatibilityIndex;
    private Component status = Component.literal("");
    private boolean statusError = false;

    public SetupScreen(Screen parent) {
        super(Component.literal("CREADDINSCART AI BOT - Setup"));
        this.parent = parent;
        CTAIConfig config = CTAIConfig.get();
        this.compatibilityIndex = AiCompatibility.fromName(config.compatibilityMode).ordinal();
    }

    @Override
    protected void init() {
        CTAIConfig config = CTAIConfig.get();
        this.layout = computeLayout();
        int rowY = this.layout.startY;

        this.botNameField = addField(rowY, config.botName, 80);
        rowY += this.layout.rowSpacing;
        this.apiKeyField = addField(rowY, config.apiKey, 400);
        rowY += this.layout.rowSpacing;
        this.apiHostField = addField(rowY, config.apiHost, 200);
        rowY += this.layout.rowSpacing;
        this.modelField = addField(rowY, config.model, 200);
        rowY += this.layout.rowSpacing;
        this.promptField = addField(rowY, config.systemPrompt, 400);
        rowY += this.layout.rowSpacing;
        this.temperatureField = addField(rowY, Double.toString(config.temperature), 16);
        rowY += this.layout.rowSpacing;
        this.maxTokensField = addField(rowY, Integer.toString(config.maxTokens), 8);
        rowY += this.layout.rowSpacing;
        this.timeoutField = addField(rowY, Integer.toString(config.timeoutSeconds), 6);
        rowY += this.layout.rowSpacing;

        List<Button> compatRow = addButtonRow(rowY, new String[]{compatibilityLabel()}, i -> {
            this.compatibilityIndex = (this.compatibilityIndex + 1) % AiCompatibility.values().length;
            updateButtonLabels();
        }, 240);
        this.compatibilityButton = compatRow.get(0);
        rowY += this.layout.rowSpacing;

        List<Button> agentRow = addButtonRow(rowY, new String[]{agentLabel()}, i -> {
            CTAIConfig.get().agentEnabled = !CTAIConfig.get().agentEnabled;
            CTAIConfig.save();
            updateButtonLabels();
        }, 240);
        this.agentButton = agentRow.get(0);
        rowY += this.layout.rowSpacing + 4;

        List<Button> tools = addButtonRow(rowY,
                new String[]{"Detect Local", "Scan Models", "Models", "Test Connection", scaleLabel()},
                i -> {
                    if (i == 0) {
                        detectLocal();
                    } else if (i == 1) {
                        scanModels();
                    } else if (i == 2) {
                        cycleModel();
                    } else if (i == 3) {
                        testConnection();
                    } else {
                        cycleScale();
                    }
                }, 132);
        this.detectButton = tools.get(0);
        this.scanButton = tools.get(1);
        this.modelButton = tools.get(2);
        this.testButton = tools.get(3);
        this.scaleButton = tools.get(4);
        rowY += 24;

        addButtonRow(rowY, new String[]{"Save & Continue", "Cancel"},
                i -> {
                    if (i == 0) {
                        save();
                    } else {
                        onClose();
                    }
                }, 160);

        updateButtonLabels();
        this.setInitialFocus(this.botNameField);
    }

    private Layout computeLayout() {
        double scale = CTAIConfig.get().uiScale;
        if (scale <= 0) {
            scale = 1.0;
        }
        int fieldWidth = clamp((int) (this.width * 0.34 * scale), 120, 300);
        int contentWidth = LABEL_WIDTH + GAP + fieldWidth;
        int left = Math.max(MARGIN, (this.width - contentWidth) / 2);
        int fieldX = left + LABEL_WIDTH + GAP;
        int startY = 26;

        int rows = 10;
        int bottomReserve = 24 + 24 + 20 + 16;
        int available = this.height - startY - bottomReserve;
        int rowSpacing = clamp((int) ((available / (double) rows) * scale), 12, 26);
        if (startY + rows * rowSpacing + bottomReserve > this.height) {
            rowSpacing = Math.max(11, (this.height - startY - bottomReserve) / rows);
        }
        return new Layout(left, fieldX, fieldWidth, rowSpacing, startY);
    }

    private EditBox addField(int y, String value, int maxLength) {
        EditBox field = new EditBox(this.font, this.layout.fieldX, y,
                this.layout.fieldWidth, FIELD_HEIGHT, Component.literal(""));
        field.setValue(value == null ? "" : value);
        field.setMaxLength(maxLength);
        this.addRenderableWidget(field);
        return field;
    }

    private List<Button> addButtonRow(int y, String[] labels, java.util.function.Consumer<Integer> action, int maxWidth) {
        int gap = 6;
        int buttonWidth = clamp((this.width - MARGIN * 2 - gap * (labels.length - 1)) / labels.length, 66, maxWidth);
        int totalWidth = buttonWidth * labels.length + gap * (labels.length - 1);
        int startX = Math.max(MARGIN, (this.width - totalWidth) / 2);
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button button = Button.builder(Component.literal(labels[i]), b -> action.accept(index))
                    .bounds(startX + i * (buttonWidth + gap), y, buttonWidth, FIELD_HEIGHT)
                    .build();
            this.addRenderableWidget(button);
            buttons.add(button);
        }
        return buttons;
    }

    private void updateButtonLabels() {
        if (this.compatibilityButton != null) {
            this.compatibilityButton.setMessage(Component.literal(compatibilityLabel()));
        }
        if (this.agentButton != null) {
            this.agentButton.setMessage(Component.literal(agentLabel()));
        }
        if (this.scaleButton != null) {
            this.scaleButton.setMessage(Component.literal(scaleLabel()));
        }
        if (this.modelButton != null) {
            List<String> models = CTAIConfig.get().availableModels;
            int count = models == null ? 0 : models.size();
            this.modelButton.setMessage(Component.literal("Models (" + count + ")"));
        }
    }

    private String compatibilityLabel() {
        AiCompatibility mode = AiCompatibility.values()[this.compatibilityIndex];
        return "Compatibility: " + mode.getDisplayName();
    }

    private String agentLabel() {
        return "Agent: " + (CTAIConfig.get().agentEnabled ? "ON" : "OFF");
    }

    private String scaleLabel() {
        return "UI " + Math.round(CTAIConfig.get().uiScale * 100) + "%";
    }

    private void cycleScale() {
        readFields();
        double current = CTAIConfig.get().uiScale;
        int index = 0;
        for (int i = 0; i < SCALE_STEPS.length; i++) {
            if (Math.abs(SCALE_STEPS[i] - current) < 0.01) {
                index = i;
                break;
            }
            if (SCALE_STEPS[i] > current) {
                index = i;
                break;
            }
            index = i;
        }
        CTAIConfig.get().uiScale = SCALE_STEPS[(index + 1) % SCALE_STEPS.length];
        CTAIConfig.save();
        this.rebuildWidgets();
    }

    private void cycleModel() {
        List<String> models = CTAIConfig.get().availableModels;
        if (models == null || models.isEmpty()) {
            setStatus("No scanned models. Click Scan Models first.", true);
            return;
        }
        String current = this.modelField.getValue().trim();
        int index = models.indexOf(current);
        index = (index + 1) % models.size();
        this.modelField.setValue(models.get(index));
        setStatus("Model " + (index + 1) + "/" + models.size() + ": " + models.get(index), false);
        updateButtonLabels();
    }

    private void readFields() {
        if (this.botNameField == null) {
            return;
        }
        CTAIConfig config = CTAIConfig.get();
        config.botName = this.botNameField.getValue().trim();
        config.apiKey = this.apiKeyField.getValue().trim();
        config.apiHost = this.apiHostField.getValue().trim();
        config.model = this.modelField.getValue().trim();
        config.systemPrompt = this.promptField.getValue().trim();
        config.compatibilityMode = AiCompatibility.values()[this.compatibilityIndex].name();
    }

    private void save() {
        readFields();
        CTAIConfig config = CTAIConfig.get();
        AiCompatibility mode = AiCompatibility.values()[this.compatibilityIndex];

        if (config.botName.isEmpty() || config.apiHost.isEmpty()) {
            setStatus("Bot name and API host are required.", true);
            return;
        }
        String keyIssue = CTAIConfig.keyIssue(config.apiKey, mode);
        if (keyIssue != null) {
            setStatus(keyIssue, true);
            return;
        }
        if (!config.apiHost.startsWith("http://") && !config.apiHost.startsWith("https://")) {
            setStatus("API host must start with http:// or https://", true);
            return;
        }
        if (config.model.isEmpty()) {
            config.model = mode.getDefaultModel();
        }

        try {
            config.temperature = Double.parseDouble(this.temperatureField.getValue().trim());
        } catch (NumberFormatException e) {
            config.temperature = 0.7;
        }
        try {
            config.maxTokens = Integer.parseInt(this.maxTokensField.getValue().trim());
        } catch (NumberFormatException e) {
            config.maxTokens = 512;
        }
        try {
            config.timeoutSeconds = Integer.parseInt(this.timeoutField.getValue().trim());
        } catch (NumberFormatException e) {
            config.timeoutSeconds = 30;
        }
        config.temperature = Math.max(0.0, Math.min(2.0, config.temperature));
        config.maxTokens = Math.max(1, config.maxTokens);
        config.timeoutSeconds = Math.max(5, config.timeoutSeconds);

        config.completed = true;
        CTAIConfig.save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    private void testConnection() {
        readFields();
        CTAIConfig config = CTAIConfig.get();
        AiCompatibility mode = AiCompatibility.values()[this.compatibilityIndex];
        String keyIssue = CTAIConfig.keyIssue(config.apiKey, mode);
        if (keyIssue != null) {
            setStatus(keyIssue, true);
            return;
        }
        if (config.model.isEmpty()) {
            config.model = mode.getDefaultModel();
        }

        setStatus("Testing " + mode.getDisplayName() + " with key " + CTAIConfig.maskKey(config.apiKey) + " ...", false);
        Thread worker = new Thread(() -> {
            try {
                String answer = AiClient.testConnection(config);
                String preview = answer.length() > 40 ? answer.substring(0, 40) + "..." : answer;
                this.minecraft.execute(() ->
                        setStatus("Connection OK - provider replied: " + preview.replaceAll("\\s+", " "), false));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                this.minecraft.execute(() -> setStatus(message, true));
            }
        }, "ctai-test-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void detectLocal() {
        readFields();
        CTAIConfig config = CTAIConfig.get();
        int timeout = config.timeoutSeconds;
        setStatus("Scanning local model servers on common ports ...", false);
        Thread worker = new Thread(() -> {
            try {
                java.util.List<AiClient.LocalServer> servers = AiClient.detectLocalServers(timeout);
                this.minecraft.execute(() -> {
                    if (servers.isEmpty()) {
                        setStatus("No local server found. Start LM Studio / Ollama / llama.cpp and retry.", true);
                        return;
                    }
                    AiClient.LocalServer chosen = servers.get(0);
                    CTAIConfig cfg = CTAIConfig.get();
                    cfg.apiHost = chosen.host;
                    cfg.compatibilityMode = chosen.mode.name();
                    cfg.availableModels = chosen.models;
                    cfg.model = chosen.models.get(0);
                    cfg.apiKey = "";
                    CTAIConfig.save();

                    this.compatibilityIndex = chosen.mode.ordinal();
                    this.apiHostField.setValue(chosen.host);
                    this.apiKeyField.setValue("");
                    this.modelField.setValue(chosen.models.get(0));
                    updateButtonLabels();

                    String msg = "Found " + servers.size() + " local server(s). Using "
                            + chosen.mode.getDisplayName() + " with " + chosen.models.size() + " models.";
                    setStatus(msg, false);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                this.minecraft.execute(() -> setStatus(message, true));
            }
        }, "ctai-local-detect-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void scanModels() {
        readFields();
        CTAIConfig config = CTAIConfig.get();
        AiCompatibility mode = AiCompatibility.values()[this.compatibilityIndex];
        String keyIssue = CTAIConfig.keyIssue(config.apiKey, mode);
        if (keyIssue != null) {
            setStatus(keyIssue, true);
            return;
        }

        setStatus("Scanning models at " + config.apiHost + " ...", false);
        Thread worker = new Thread(() -> {
            try {
                List<String> models = AiClient.listModels(config);
                CTAIConfig.get().availableModels = models;
                CTAIConfig.save();
                this.minecraft.execute(() -> {
                    if (models.isEmpty()) {
                        setStatus("Scan finished but no models were returned.", true);
                    } else {
                        setStatus("Found " + models.size() + " models. Use Models button to cycle.", false);
                        updateButtonLabels();
                    }
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                this.minecraft.execute(() -> setStatus(message, true));
            }
        }, "ctai-model-scan-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void setStatus(String message, boolean error) {
        this.statusError = error;
        String clean = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        int max = Math.max(30, this.width / 6);
        if (clean.length() > max) {
            clean = clean.substring(0, max - 3) + "...";
        }
        this.status = Component.literal(clean);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        String[] labels = {"Bot Name", "API Key", "API Host", "Model", "System Prompt",
                "Temperature", "Max Tokens", "Timeout (s)"};
        int y = this.layout.startY;
        for (String label : labels) {
            graphics.text(this.font, Component.literal(label), this.layout.labelX, y + 5, 0xFFFFFFFF);
            y += this.layout.rowSpacing;
        }
        graphics.text(this.font, Component.literal("Compatibility"), this.layout.labelX, y + 5, 0xFFFFFFFF);
        y += this.layout.rowSpacing;
        graphics.text(this.font, Component.literal("Agent"), this.layout.labelX, y + 5, 0xFFFFFFFF);

        int statusY = Math.min(this.height - 14, y + this.layout.rowSpacing + 32);
        graphics.centeredText(this.font, this.status, this.width / 2, statusY,
                this.statusError ? 0xFFFF5555 : 0xFF55FF55);
        graphics.centeredText(this.font, Component.literal("Detect Local finds local models; agent: /ctai <request>, stop: /ctai_stop"),
                this.width / 2, Math.min(this.height - 8, statusY + 10), 0xFFAAAAAA);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Layout {
        final int labelX;
        final int fieldX;
        final int fieldWidth;
        final int rowSpacing;
        final int startY;

        Layout(int labelX, int fieldX, int fieldWidth, int rowSpacing, int startY) {
            this.labelX = labelX;
            this.fieldX = fieldX;
            this.fieldWidth = fieldWidth;
            this.rowSpacing = rowSpacing;
            this.startY = startY;
        }
    }
}
