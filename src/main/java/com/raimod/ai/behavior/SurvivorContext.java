package com.raimod.ai.behavior;

import com.raimod.config.RAIServerConfig;
import com.raimod.entity.SimulatedSurvivor;
import com.raimod.integration.ModIntegrationRegistry;
import net.minecraft.server.MinecraftServer;

public record SurvivorContext(
    MinecraftServer server,
    SimulatedSurvivor survivor,
    ModIntegrationRegistry integrations,
    RAIServerConfig.RuntimeValues config
) {
}
