package com.creaddinscart.ctai;

import net.fabricmc.api.ClientModInitializer;

import java.util.logging.Logger;

public final class MINECRAFT_CT_AI implements ClientModInitializer {
    public static final String MOD_ID = "minecraft_ct_ai";
    public static final Logger LOGGER = Logger.getLogger("MINECRAFT_CT_AI");

    @Override
    public void onInitializeClient() {
        CTAIConfig.load();
        CTAICommand.register();
        LOGGER.info("MINECRAFT_CT_AI loaded for Minecraft 1.16.5");
    }
}
