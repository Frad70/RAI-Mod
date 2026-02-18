package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SurvivorState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CombatReactionGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        Entity attacker = context.survivor().getLastHurtByMob();
        boolean loudSoundNearby = context.integrations().hasLoudSoundNear(context.survivor(), 32);
        if (attacker != null) {
            return 1.2;
        }
        return loudSoundNearby ? 0.9 : 0.0;
    }

    @Override
    public void execute(SurvivorContext context) {
        var survivor = context.survivor();
        Entity attacker = survivor.getLastHurtByMob();

        if (attacker == null && context.integrations().hasLoudSoundNear(survivor, 32)) {
            attacker = context.server().overworld().getNearestPlayer(survivor, 32.0);
        }
        if (attacker == null) {
            return;
        }

        Vec3 attackerEye = attacker.getEyePosition();
        survivor.aimAt(attackerEye);

        float accuracy = context.integrations().physicalStats().getAccuracySkill(survivor.id());
        Vec3 aimPos = context.integrations().tacz().calculateLeadShot(
            survivor,
            attacker,
            survivor.getMainHandItem(),
            context.config().baseAimScatterDegrees(),
            accuracy
        );
        survivor.aimAt(aimPos);

        int suppressTicks = Math.max(survivor.state().reactionFireTicks(), 40 + survivor.level().random.nextInt(21));
        survivor.setState(survivor.state().withMode(SurvivorState.TacticalMode.COMBAT_REACTION)
            .withReactionFireTicks(suppressTicks)
            .withSuppressionTicks(20));

        double strafe = survivor.tickCount % 20 < 10 ? -1.0 : 1.0;
        survivor.setMovementInput(strafe, 0.15);

        if (context.integrations().tacz().isGunEmpty(survivor.getMainHandItem())) {
            survivor.setState(survivor.state().withMode(SurvivorState.TacticalMode.RELOAD));
        }

        boolean lowHealth = survivor.getHealth() <= (survivor.getMaxHealth() * 0.6f);
        boolean suppressed = survivor.state().suppressionTicks() > 0;
        if (lowHealth || suppressed || survivor.state().tacticalMode() == SurvivorState.TacticalMode.RELOAD) {
            BlockPos cover = context.integrations().findCoverPosition((ServerLevel) survivor.level(), attackerEye, survivor);
            if (cover != null) {
                context.integrations().baritone().setCoverGoal(survivor, cover);
                survivor.setState(survivor.state().withMode(SurvivorState.TacticalMode.RELOAD));

                if (lowHealth && context.integrations().useHealingItem(survivor)) {
                    survivor.setState(survivor.state().withMode(SurvivorState.TacticalMode.HEALING));
                }
            }
        }

        if (survivor.state().reactionFireTicks() <= 0) {
            survivor.clearMovementInput();
        }
    }
}
