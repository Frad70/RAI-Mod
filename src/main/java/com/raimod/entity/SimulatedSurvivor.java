package com.raimod.entity;

import com.raimod.ai.behavior.BehaviorEngine;
import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.ai.memory.SurvivorMemory;
import com.raimod.config.RAIServerConfig;
import com.raimod.integration.ModIntegrationRegistry;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class SimulatedSurvivor {
    private final UUID id;
    private final SurvivorMemory memory;
    private final BehaviorEngine behaviorEngine;
    private SurvivorState state;

    private SimulatedSurvivor(UUID id, SurvivorMemory memory, BehaviorEngine behaviorEngine, SurvivorState state) {
        this.id = id;
        this.memory = memory;
        this.behaviorEngine = behaviorEngine;
        this.state = state;
    }

    public static SimulatedSurvivor bootstrap(UUID id, RAIServerConfig.RuntimeValues config) {
        SurvivorMemory memory = SurvivorMemory.createEmpty(id);
        SurvivorState state = SurvivorState.freshSpawn(config.minActiveChunks(), config.maxActiveChunks());
        return new SimulatedSurvivor(id, memory, new BehaviorEngine(), state);
    }

    public UUID id() {
        return id;
    }

    public SurvivorMemory memory() {
        return memory;
    }

    public SurvivorState state() {
        return state;
    }

    public void tick(MinecraftServer server, ModIntegrationRegistry integrations, RAIServerConfig.RuntimeValues config) {
        state = state.withDynamicChunkBudget(config, server);

        SurvivorContext context = new SurvivorContext(server, this, integrations, config);
        behaviorEngine.tick(context);
    }

    public void resetRuntime(RAIServerConfig.RuntimeValues config) {
        this.state = state.withResetBudgets(config.minActiveChunks(), config.maxActiveChunks());
    }
}
