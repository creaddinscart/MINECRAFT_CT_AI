package com.creaddinscart.ctai.mixin;

import com.creaddinscart.ctai.CTAIConfig;
import com.creaddinscart.ctai.SetupScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ctai$setupGate(CallbackInfo ci) {
        if (!CTAIConfig.get().completed) {
            this.minecraft.execute(() -> this.minecraft.gui.setScreen(new SetupScreen(this)));
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ctai$addSetupButton(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(Component.literal("CT AI Setup"), button ->
                        this.minecraft.gui.setScreen(new SetupScreen(this)))
                .bounds(this.width - 96, this.height - 28, 88, 20)
                .build());
    }
}
