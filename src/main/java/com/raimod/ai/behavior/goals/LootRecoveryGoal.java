package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.entity.SurvivorState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

public final class LootRecoveryGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        boolean hasUnrecoveredCorpse = context.integrations().corpse().hasUnrecoveredCorpse(context.survivor().id());
        return hasUnrecoveredCorpse ? 0.85 : 0.0;
    }

    @Override
    public void execute(SurvivorContext context) {
        Container source = findNearbyContainer(context.server().overworld(), context.survivor().blockPosition());
        if (source == null) {
            return;
        }

        boolean moved = context.integrations().corpse().transferOneItem(context.survivor(), source, 1, 2);
        if (!moved) {
            return;
        }

        context.survivor().memory().combatLog().append("Recovered exactly one item with 1-2 tick throttle");
        context.survivor().setState(context.survivor().state().withMode(SurvivorState.TacticalMode.LOOT_RECOVERY));
    }

    private Container findNearbyContainer(ServerLevel level, BlockPos center) {
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockEntity be = level.getBlockEntity(center.offset(x, y, z));
                    if (be instanceof RandomizableContainerBlockEntity container) {
                        return container;
                    }
                }
            }
        }
        return null;
    }
}
