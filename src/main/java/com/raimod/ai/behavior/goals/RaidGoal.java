package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.ai.memory.RaidTargetKnowledge;
import com.raimod.entity.SurvivorState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

public final class RaidGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        RaidTargetKnowledge target = context.survivor().memory().bestRaidCandidate();
        if (target == null) {
            return 0.0;
        }

        double distanceToHome = context.survivor().memory().homePosition().distSqr(target.position());
        distanceToHome = Math.sqrt(distanceToHome);
        return (target.knownChestCount() * 10.0) - (distanceToHome * 0.1) - (target.defenderCount() * 50.0);
    }

    @Override
    public void execute(SurvivorContext context) {
        RaidTargetKnowledge target = context.survivor().memory().bestRaidCandidate();
        if (target == null) {
            return;
        }

        double raidScore = score(context);
        SurvivorState state = context.survivor().state();
        if (raidScore <= context.config().raidThreshold() && state.tacticalMode() != SurvivorState.TacticalMode.RAID_BREACH
            && state.tacticalMode() != SurvivorState.TacticalMode.RAID_LOOT
            && state.tacticalMode() != SurvivorState.TacticalMode.RAID_RETREAT) {
            return;
        }

        if (state.tacticalMode() == SurvivorState.TacticalMode.SCOUTING || state.tacticalMode() == SurvivorState.TacticalMode.FARMING) {
            context.integrations().tacz().prepareRaidLoadout(context.survivor().id(), target.defenderCount());
            context.integrations().baritone().setRaidHouseGoal(context.survivor(), target.position());
            context.survivor().setState(state.withMode(SurvivorState.TacticalMode.RAID_BREACH));
            return;
        }

        if (state.tacticalMode() == SurvivorState.TacticalMode.RAID_BREACH) {
            BlockPos wall = findRaidWall(context.server().overworld(), target.position());
            if (wall != null) {
                context.integrations().placeExplosive(context.survivor(), wall);
            }
            if (isTargetAccessible(context.server().overworld(), target.position())) {
                context.survivor().setState(state.withMode(SurvivorState.TacticalMode.RAID_LOOT));
            }
            return;
        }

        if (state.tacticalMode() == SurvivorState.TacticalMode.RAID_LOOT) {
            context.integrations().baritone().setLootChestGoal(context.survivor(), target.position());
            context.survivor().setState(state.withMode(SurvivorState.TacticalMode.RAID_RETREAT));
            return;
        }

        if (state.tacticalMode() == SurvivorState.TacticalMode.RAID_RETREAT) {
            context.integrations().baritone().setRetreatGoal(context.survivor(), context.survivor().memory().homePosition());
            context.survivor().setState(state.withMode(SurvivorState.TacticalMode.EXTRACTION));
        }
    }

    private boolean isTargetAccessible(ServerLevel level, BlockPos targetPos) {
        for (Direction3D dir : Direction3D.values()) {
            BlockPos adjacent = targetPos.offset(dir.dx, dir.dy, dir.dz);
            if (level.getBlockState(adjacent).isAir()) {
                return true;
            }
        }
        return false;
    }

    private BlockPos findRaidWall(ServerLevel level, BlockPos target) {
        int radius = 5;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = target.offset(x, y, z);
                    if (level.getBlockState(pos).is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private enum Direction3D {
        UP(0, 1, 0), DOWN(0, -1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), EAST(1, 0, 0), WEST(-1, 0, 0);
        final int dx;
        final int dy;
        final int dz;

        Direction3D(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
