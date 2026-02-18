package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SimulatedSurvivor;
import com.raimod.entity.SurvivorState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/**
 * Camping goal that models window sniping and roof camping.
 */
public final class CampingGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        SimulatedSurvivor survivor = context.survivor();
        Player target = context.server().overworld().getNearestPlayer(survivor, 56.0);
        if (target == null) {
            return 0.0;
        }

        boolean indoors = isIndoors(context.server().overworld(), target);
        return indoors ? 0.78 : 0.0;
    }

    @Override
    public void execute(SurvivorContext context) {
        SimulatedSurvivor survivor = context.survivor();
        ServerLevel level = context.server().overworld();
        Player target = level.getNearestPlayer(survivor, 56.0);
        if (target == null) {
            return;
        }

        BlockPos window = findWindowSnipeSpot(level, survivor.blockPosition(), target);
        if (window != null) {
            context.integrations().baritone().setCoverGoal(survivor, window);
            survivor.aimAt(target.getEyePosition());
            survivor.setState(survivor.state().withMode(SurvivorState.TacticalMode.BASE_DEFENSE)
                .withReactionFireTicks(Math.max(1200, survivor.state().reactionFireTicks())));
            return;
        }

        BlockPos roof = findRoofCampSpot(level, target);
        if (roof != null) {
            context.integrations().baritone().setCoverGoal(survivor, roof);
            survivor.aimAt(target.getEyePosition());
            survivor.setState(survivor.state().withMode(SurvivorState.TacticalMode.BASE_DEFENSE)
                .withReactionFireTicks(Math.max(1200, survivor.state().reactionFireTicks())));
        }
    }

    private boolean isIndoors(ServerLevel level, LivingEntity target) {
        BlockPos above = target.blockPosition().above();
        return !level.getBlockState(above).isAir();
    }

    private BlockPos findWindowSnipeSpot(ServerLevel level, BlockPos around, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -12; x <= 12; x++) {
            for (int y = -3; y <= 4; y++) {
                for (int z = -12; z <= 12; z++) {
                    BlockPos candidate = targetPos.offset(x, y, z);
                    if (!level.getBlockState(candidate).isAir() && level.getBlockState(candidate).canOcclude()) {
                        continue;
                    }
                    if (!hasLos(level, Vec3.atCenterOf(candidate), target.getEyePosition(), target)) {
                        continue;
                    }
                    double d = candidate.distSqr(around);
                    if (d < bestDist) {
                        bestDist = d;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best;
    }

    private BlockPos findRoofCampSpot(ServerLevel level, LivingEntity target) {
        BlockPos base = target.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int y = 3; y <= 4; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = base.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    if (!level.getBlockState(pos.below()).isSolid()) {
                        continue;
                    }
                    if (!hasLos(level, Vec3.atCenterOf(pos), target.getEyePosition(), target)) {
                        continue;
                    }
                    double d = pos.distSqr(base);
                    if (d < bestDist) {
                        bestDist = d;
                        best = pos.immutable();
                    }
                }
            }
        }

        return best;
    }

    private boolean hasLos(ServerLevel level, Vec3 from, Vec3 to, LivingEntity owner) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS;
    }
}
