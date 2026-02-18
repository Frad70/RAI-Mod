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
        boolean moved = context.integrations().corpse().recoverGradually(context.survivor(), 1, 2);
        if (!moved) {
            return;
        }

        context.survivor().memory().combatLog().append("Recovering 1 item from corpse/container this interaction window");
        context.survivor().setState(context.survivor().state().withMode(SurvivorState.TacticalMode.LOOT_RECOVERY));
    }
}
