package com.raimod.ai.memory;

import net.minecraft.core.BlockPos;

public record RaidTargetKnowledge(
    String structureName,
    BlockPos position,
    int knownChestCount,
    int defenderCount,
    double estimatedLootValue,
    double expectedRaidCost,
    int wallHardness,
    boolean hasKnownEntryPoint
) {
}
