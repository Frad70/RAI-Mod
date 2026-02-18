package com.raimod;

import com.raimod.ai.AIDirectorService;
import com.raimod.config.RAIServerConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(RAIMod.MOD_ID)
public final class RAIMod {
    public static final String MOD_ID = "raimod";

    private final AIDirectorService aiDirectorService;

    public RAIMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, RAIServerConfig.SPEC);

        this.aiDirectorService = new AIDirectorService();
        modEventBus.addListener(aiDirectorService::onConfigReloaded);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        aiDirectorService.tick(event.getServer());
    }
}
