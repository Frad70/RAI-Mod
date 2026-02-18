package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SurvivorState;

public final class ProgressionGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        return 0.25;
    }

    @Override
    public void execute(SurvivorContext context) {
        context.integrations().physicalStats().train(context.survivor().id(), "mining");
        context.survivor().memory().refreshStaleKnowledge(context.server(), context.config().memoryRevalidationSeconds());
        context.survivor().memory().setCurrentMode(SurvivorState.TacticalMode.FARMING);
    }
}
