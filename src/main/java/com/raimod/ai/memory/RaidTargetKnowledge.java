package com.raimod.ai.memory;

public record RaidTargetKnowledge(
    String structureName,
    double estimatedLootValue,
    double expectedRaidCost,
    double expectedDefenderStrength,
    int wallHardness,
    boolean hasKnownEntryPoint
) {
    public double priorityScore() {
        double entryBonus = hasKnownEntryPoint ? 0.2 : 0.0;
        double hardnessPenalty = Math.min(0.4, wallHardness / 100.0);
        return ((estimatedLootValue - expectedRaidCost) / 100.0) + entryBonus - hardnessPenalty - expectedDefenderStrength;
    }
}
