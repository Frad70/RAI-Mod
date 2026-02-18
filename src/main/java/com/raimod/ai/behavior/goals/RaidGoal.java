package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SurvivorState;

public final class RaidGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        var candidate = context.survivor().memory().bestRaidCandidate();
        if (candidate == null) {
            return 0.0;
        }

        double expectedProfit = candidate.estimatedLootValue() - candidate.expectedRaidCost();
        double threatPenalty = Math.max(0.05, 1.0 - candidate.expectedDefenderStrength());
        return Math.max(0.0, (expectedProfit / 500.0) * threatPenalty);
    }

    @Override
    public void execute(SurvivorContext context) {
        var candidate = context.survivor().memory().bestRaidCandidate();
        if (candidate == null) {
            return;
        }
        context.integrations().tacz().prepareRaidLoadout(context.survivor().id(), candidate.expectedDefenderStrength());
        context.survivor().memory().combatLog().append("Starting RAID against " + candidate.structureName());
        context.survivor().memory().setCurrentMode(SurvivorState.TacticalMode.RAID);
    }
}
