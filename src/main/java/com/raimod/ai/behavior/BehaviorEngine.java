package com.raimod.ai.behavior;

import com.raimod.ai.behavior.goals.BaseDefenseGoal;
import com.raimod.ai.behavior.goals.Goal;
import com.raimod.ai.behavior.goals.LootRecoveryGoal;
import com.raimod.ai.behavior.goals.ProgressionGoal;
import com.raimod.ai.behavior.goals.RaidGoal;
import java.util.Comparator;
import java.util.List;

public final class BehaviorEngine {
    private final List<Goal> goals = List.of(
        new BaseDefenseGoal(),
        new LootRecoveryGoal(),
        new RaidGoal(),
        new ProgressionGoal()
    );

    public void tick(SurvivorContext context) {
        goals.stream()
            .map(goal -> new ScoredGoal(goal, goal.score(context)))
            .filter(scored -> scored.score > 0)
            .max(Comparator.comparingDouble(ScoredGoal::score))
            .ifPresent(scored -> scored.goal.execute(context));
    }

    private record ScoredGoal(Goal goal, double score) {
    }
}
