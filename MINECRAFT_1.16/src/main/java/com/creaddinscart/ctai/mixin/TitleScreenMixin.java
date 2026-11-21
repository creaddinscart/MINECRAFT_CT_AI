package com.creaddinscart.ctai.mixin;

import com.creaddinscart.ctai.CTAIConfig;
import com.creaddinscart.ctai.SetupScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.TextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(TextComponent title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ctai$setupGate(CallbackInfo ci) {
        if (!CTAIConfig.get().completed) {
            this.minecraft.execute(() -> this.minecraft.setScreen(new SetupScreen(this)));
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ctai$addSetupButton(CallbackInfo ci) {
        this.addButton(new Button(this.width - 96, this.height - 28, 88, 20,
                new TextComponent("CT AI Setup"), button -> this.minecraft.setScreen(new SetupScreen(this))));
    }
}
