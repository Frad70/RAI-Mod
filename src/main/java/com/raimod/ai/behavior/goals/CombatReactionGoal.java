package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SimulatedSurvivor;
import com.raimod.entity.SurvivorState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class CombatReactionGoal implements Goal {
    private final Map<UUID, Integer> lostSightTicks = new HashMap<>();

    @Override
    public double score(SurvivorContext context) {
        Entity attacker = context.survivor().getLastHurtByMob();
        boolean loudSoundNearby = context.integrations().hasLoudSoundNear(context.survivor(), 32);
        if (attacker != null) {
            return 1.3;
        }
        return loudSoundNearby ? 0.95 : 0.0;
    }

    @Override
    public void execute(SurvivorContext context) {
        SimulatedSurvivor survivor = context.survivor();
        if (!survivor.canPerformActions()) {
            return;
        }

        LivingEntity target = getPriorityTarget(context);
        if (target == null) {
            survivor.clearMovementInput();
            return;
        }

        LivingEntity betrayalTarget = maybePickBetrayalTarget(context, target);
        if (betrayalTarget != null) {
            target = betrayalTarget;
        }

        survivor.setCurrentCombatTarget(target.getUUID());
        UUID botId = survivor.id();

        if (!survivor.isEntityVisible(target)) {
            int lost = lostSightTicks.getOrDefault(botId, 0) + 1;
            lostSightTicks.put(botId, lost);

            survivor.memory().setLastKnownPosition(target.getUUID(), target.blockPosition());
            BlockPos lastKnown = survivor.memory().lastKnownPosition(target.getUUID());

            survivor.setState(survivor.state().withMode(SurvivorState.TacticalMode.SEARCHING));
            context.integrations().baritone().setCoverGoal(survivor, lastKnown);

            double sweep = Math.sin((survivor.tickCount + survivor.getId()) * 0.18) * 45.0;
            Vec3 toLastKnown = Vec3.atCenterOf(lastKnown).subtract(survivor.getEyePosition());
            Vec3 looked = toLastKnown.normalize().yRot((float) Math.toRadians(sweep));
            survivor.aimAt(survivor.getEyePosition().add(looked.scale(6.0)));

            if (lost > 300) {
                lostSightTicks.remove(botId);
                survivor.memory().clearLastKnownPosition(target.getUUID());
                survivor.setCurrentCombatTarget(null);
            }
            return;
        }

        lostSightTicks.put(botId, 0);
        survivor.memory().setLastKnownPosition(target.getUUID(), target.blockPosition());

        float accuracy = context.integrations().physicalStats().getAccuracySkill(survivor.id());
        Vec3 aimPos = context.integrations().tacz().calculateLeadShot(
            survivor,
            target,
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
        survivor.setMovementInput(strafe, 0.18);

        if (context.integrations().tacz().isGunEmpty(survivor.getMainHandItem())) {
            survivor.setState(survivor.state().withMode(SurvivorState.TacticalMode.RELOAD));
        }

        boolean lowHealth = survivor.getHealth() <= (survivor.getMaxHealth() * 0.6f);
        boolean suppressed = survivor.state().suppressionTicks() > 0;
        if (lowHealth || suppressed || survivor.state().tacticalMode() == SurvivorState.TacticalMode.RELOAD) {
            Vec3 attackerEye = target.getEyePosition();
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

    private LivingEntity getPriorityTarget(SurvivorContext context) {
        SimulatedSurvivor survivor = context.survivor();
        ServerLevel level = context.server().overworld();

        double baseRange = 48.0 * awarenessModifier(survivor.state().tacticalMode());
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
            survivor.getBoundingBox().inflate(baseRange),
            entity -> entity.isAlive() && entity != survivor);

        List<ScoredTarget> scored = new ArrayList<>();
        for (LivingEntity entity : candidates) {
            double effectiveRange = baseRange;
            if (isStealthed(level, entity)) {
                effectiveRange *= 0.4;
            }

            double distance = survivor.distanceTo(entity);
            if (distance > effectiveRange) {
                continue;
            }

            if (!survivor.isEntityVisible(entity)) {
                continue;
            }

            double score = (1.0 / Math.max(1.0, distance))
                * (hasGun(entity) ? 2.0 : 1.0)
                * (isLookingAtMe(entity, survivor) ? 1.5 : 1.0);

            if (entity instanceof SimulatedSurvivor otherBot && otherBot.getGameProfile().getName().startsWith("rai_")
                && survivor.trustFactor() < 0.35f) {
                score *= 2.2;
            }

            scored.add(new ScoredTarget(entity, score));
        }

        return scored.stream()
            .max(Comparator.comparingDouble(ScoredTarget::score))
            .map(ScoredTarget::entity)
            .orElse(null);
    }

    private LivingEntity maybePickBetrayalTarget(SurvivorContext context, LivingEntity currentTarget) {
        SimulatedSurvivor survivor = context.survivor();
        if (survivor.level().random.nextDouble() > 0.10) {
            return null;
        }

        List<SimulatedSurvivor> nearbyBots = context.server().overworld().getEntitiesOfClass(
            SimulatedSurvivor.class,
            survivor.getBoundingBox().inflate(32.0),
            other -> other != survivor && currentTarget.getUUID().equals(other.currentCombatTarget())
        );

        if (nearbyBots.isEmpty()) {
            return null;
        }

        return nearbyBots.get(survivor.level().random.nextInt(nearbyBots.size()));
    }

    private double awarenessModifier(SurvivorState.TacticalMode mode) {
        if (mode == SurvivorState.TacticalMode.FARMING || mode == SurvivorState.TacticalMode.SCOUTING) {
            return 0.65;
        }
        return 1.0;
    }

    private boolean hasGun(LivingEntity entity) {
        return !entity.getMainHandItem().isEmpty() && !entity.getMainHandItem().isEdible();
    }

    private boolean isLookingAtMe(LivingEntity entity, SimulatedSurvivor me) {
        Vec3 look = entity.getLookAngle().normalize();
        Vec3 toMe = me.getEyePosition().subtract(entity.getEyePosition()).normalize();
        return look.dot(toMe) > 0.75;
    }

    private boolean isStealthed(ServerLevel level, LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.isShiftKeyDown()) {
            return false;
        }

        BlockPos above = entity.blockPosition().above();
        return !level.getBlockState(above).isAir() && level.getBlockState(above).isCollisionShapeFullBlock(level, above);
    }

    private record ScoredTarget(LivingEntity entity, double score) {
    }
}
