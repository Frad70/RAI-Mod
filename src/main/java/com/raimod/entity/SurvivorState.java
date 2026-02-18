package com.raimod.entity;

import com.raimod.config.RAIServerConfig;
import net.minecraft.server.MinecraftServer;

public record SurvivorState(
    int loadedChunks,
    int minChunks,
    int maxChunks,
    TacticalMode tacticalMode,
    long lastDeathGameTime,
    boolean carryingHighValueLoot,
    int interactionCooldownTicks,
    int suppressionTicks,
    int reactionFireTicks
) {
    public static SurvivorState freshSpawn(int minChunks, int maxChunks) {
        return new SurvivorState(minChunks, minChunks, maxChunks, TacticalMode.SCOUTING, -1, false, 0, 0, 0);
    }

    public SurvivorState withDynamicChunkBudget(MinecraftServer server, RAIServerConfig.RuntimeValues config) {
        int targetChunks = minChunks;
        if (config.dynamicChunkBudget()) {
            float avgTickMs = server.getAverageTickTime();
            if (avgTickMs > 45.0f) {
                targetChunks = 1;
            } else {
                float normalizedLoad = Math.max(0.0f, Math.min(1.0f, avgTickMs / 50.0f));
                float freeBudget = 1.0f - normalizedLoad;
                targetChunks = (int) Math.round(config.minActiveChunks()
                    + (config.maxActiveChunks() - config.minActiveChunks()) * freeBudget);
            }
        }

        int clamped = Math.max(config.minActiveChunks(), Math.min(config.maxActiveChunks(), targetChunks));
        return new SurvivorState(clamped, config.minActiveChunks(), config.maxActiveChunks(), tacticalMode, lastDeathGameTime,
            carryingHighValueLoot, Math.max(0, interactionCooldownTicks - 1), Math.max(0, suppressionTicks - 1),
            Math.max(0, reactionFireTicks - 1));
    }

    public SurvivorState withResetBudgets(int minChunks, int maxChunks) {
        return new SurvivorState(minChunks, minChunks, maxChunks, tacticalMode, lastDeathGameTime, carryingHighValueLoot,
            interactionCooldownTicks, suppressionTicks, reactionFireTicks);
    }

    public SurvivorState withMode(TacticalMode mode) {
        return new SurvivorState(loadedChunks, minChunks, maxChunks, mode, lastDeathGameTime, carryingHighValueLoot,
            interactionCooldownTicks, suppressionTicks, reactionFireTicks);
    }

    public SurvivorState withInteractionCooldown(int ticks) {
        return new SurvivorState(loadedChunks, minChunks, maxChunks, tacticalMode, lastDeathGameTime, carryingHighValueLoot,
            ticks, suppressionTicks, reactionFireTicks);
    }

    public SurvivorState withSuppressionTicks(int ticks) {
        return new SurvivorState(loadedChunks, minChunks, maxChunks, tacticalMode, lastDeathGameTime, carryingHighValueLoot,
            interactionCooldownTicks, ticks, reactionFireTicks);
    }

    public SurvivorState withReactionFireTicks(int ticks) {
        return new SurvivorState(loadedChunks, minChunks, maxChunks, tacticalMode, lastDeathGameTime, carryingHighValueLoot,
            interactionCooldownTicks, suppressionTicks, ticks);
    }

    public enum TacticalMode {
        SCOUTING,
        FARMING,
        BASE_BUILDING,
        BASE_DEFENSE,
        COMBAT_REACTION,
        SEARCHING,
        RELOAD,
        HEALING,
        RAID,
        RAID_BREACH,
        RAID_LOOT,
        RAID_RETREAT,
        EXTRACTION,
        LOOT_RECOVERY,
        TEAM_SUPPORT
    }
}
