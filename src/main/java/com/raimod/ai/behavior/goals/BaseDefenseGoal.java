package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SurvivorState;
import net.minecraft.world.entity.Entity;

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

        Entity target = context.server().overworld().getNearestPlayer(context.survivor(), 28.0);
        if (target != null) {
            float accuracy = context.integrations().physicalStats().getAccuracySkill(context.survivor().id());
            context.integrations().tacz().calculateLeadShot(
                context.survivor(),
                target,
                context.survivor().getMainHandItem(),
                context.config().baseAimScatterDegrees(),
                accuracy
            );
        }

        context.survivor().setState(context.survivor().state().withMode(SurvivorState.TacticalMode.BASE_DEFENSE));
    }
}
