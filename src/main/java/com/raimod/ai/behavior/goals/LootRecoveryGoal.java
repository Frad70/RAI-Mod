package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SurvivorState;

public final class LootRecoveryGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        boolean hasUnrecoveredCorpse = context.integrations().corpse().hasUnrecoveredCorpse(context.survivor().id());
        return hasUnrecoveredCorpse ? 0.85 : 0.0;
    }

    @Override
    public void execute(SurvivorContext context) {
        context.survivor().memory().combatLog().append("Navigating to corpse and recovering loadout incrementally");
        context.integrations().corpse().recoverGradually(context.survivor().id(), 2);
        context.survivor().memory().setCurrentMode(SurvivorState.TacticalMode.LOOT_RECOVERY);
    }
}
