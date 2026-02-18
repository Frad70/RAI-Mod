package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;

public interface Goal {
    double score(SurvivorContext context);

    void execute(SurvivorContext context);
}
