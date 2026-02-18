package com.raimod.ai.memory;

import net.minecraft.core.BlockPos;

public record WorldKnowledgePoint(
    String key,
    BlockPos pos,
    String category,
    float danger,
    long lastVerifiedTick,
    boolean needsRevalidation
) {
    public WorldKnowledgePoint withNeedsRevalidation(boolean flag) {
        return new WorldKnowledgePoint(key, pos, category, danger, lastVerifiedTick, flag);
    }
}
