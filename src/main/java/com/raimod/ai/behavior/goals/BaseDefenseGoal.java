package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SurvivorState;

public final class BaseDefenseGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        boolean underAttack = context.integrations().securityCraft().hasRecentBreakAttempt(context.survivor().id());
        return underAttack ? 1.0 : 0.0;
    }

    @Override
    public void execute(SurvivorContext context) {
        context.survivor().memory().combatLog().append("Switching to BASE_DEFENSE near home territory");
        context.integrations().voiceChat().broadcastSquadPing(context.survivor().id(), "home_under_attack");
        context.survivor().memory().setCurrentMode(SurvivorState.TacticalMode.BASE_DEFENSE);
    }
}
