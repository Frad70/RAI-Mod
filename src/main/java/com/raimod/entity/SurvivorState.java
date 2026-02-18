package com.raimod.entity;

import com.raimod.config.RAIServerConfig;
import net.minecraft.server.MinecraftServer;

public record SurvivorState(
    int loadedChunks,
    int minChunks,
    int maxChunks,
    TacticalMode tacticalMode,
    long lastDeathGameTime,
    boolean carryingHighValueLoot
) {
    public static SurvivorState freshSpawn(int minChunks, int maxChunks) {
        return new SurvivorState(minChunks, minChunks, maxChunks, TacticalMode.SCOUTING, -1, false);
    }

    public SurvivorState withDynamicChunkBudget(RAIServerConfig.RuntimeValues config, MinecraftServer server) {
        int targetChunks = minChunks;
        if (config.dynamicChunkBudget()) {
            float tpsLoadFactor = (float) Math.max(0.1, Math.min(1.0, server.getAverageTickTimeNanos() / 50_000_000.0));
            float freeBudget = 1.0f - tpsLoadFactor;
            targetChunks = (int) Math.round(config.minActiveChunks() + (config.maxActiveChunks() - config.minActiveChunks()) * freeBudget);
        }

        int clamped = Math.max(config.minActiveChunks(), Math.min(config.maxActiveChunks(), targetChunks));
        return new SurvivorState(clamped, config.minActiveChunks(), config.maxActiveChunks(), tacticalMode, lastDeathGameTime, carryingHighValueLoot);
    }

    public SurvivorState withResetBudgets(int minChunks, int maxChunks) {
        return new SurvivorState(minChunks, minChunks, maxChunks, tacticalMode, lastDeathGameTime, carryingHighValueLoot);
    }

    public enum TacticalMode {
        SCOUTING,
        FARMING,
        BASE_BUILDING,
        BASE_DEFENSE,
        RAID,
        EXTRACTION,
        LOOT_RECOVERY,
        TEAM_SUPPORT
    }
}
