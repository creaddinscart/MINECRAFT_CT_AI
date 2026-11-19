package com.creaddinscart.ctai;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MINECRAFT_CT_AI implements ClientModInitializer {
    public static final String MOD_ID = "minecraft_ct_ai";
    public static final Logger LOGGER = LoggerFactory.getLogger("MINECRAFT_CT_AI");

    @Override
    public void onInitializeClient() {
        CTAIConfig.load();
        CTAICommand.register();
        LOGGER.info("MINECRAFT_CT_AI loaded for Minecraft 26.3");
    }
}
