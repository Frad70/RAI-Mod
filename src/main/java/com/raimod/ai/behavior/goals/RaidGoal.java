package com.raimod.ai.behavior.goals;

import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.ai.memory.RaidTargetKnowledge;
import com.raimod.entity.SurvivorState;
import net.minecraft.core.BlockPos;

public final class RaidGoal implements Goal {
    @Override
    public double score(SurvivorContext context) {
        RaidTargetKnowledge candidate = context.survivor().memory().bestRaidCandidate();
        if (candidate == null) {
            return 0.0;
        }

        double distanceToHome = context.survivor().fakePlayer().blockPosition().distSqr(candidate.position());
        distanceToHome = Math.sqrt(distanceToHome);

        return (candidate.knownChestCount() * 10.0)
            - (distanceToHome * 0.1)
            - (candidate.defenderCount() * 50.0);
    }

    @Override
    public void execute(SurvivorContext context) {
        RaidTargetKnowledge candidate = context.survivor().memory().bestRaidCandidate();
        if (candidate == null) {
            return;
        }

        double raidScore = score(context);
        if (raidScore <= context.config().raidThreshold() && context.survivor().state().tacticalMode() != SurvivorState.TacticalMode.RAID_BREACH
            && context.survivor().state().tacticalMode() != SurvivorState.TacticalMode.RAID_LOOT
            && context.survivor().state().tacticalMode() != SurvivorState.TacticalMode.RAID_RETREAT) {
            return;
        }

        SurvivorState.TacticalMode mode = context.survivor().state().tacticalMode();
        if (mode != SurvivorState.TacticalMode.RAID_BREACH
            && mode != SurvivorState.TacticalMode.RAID_LOOT
            && mode != SurvivorState.TacticalMode.RAID_RETREAT) {
            context.integrations().tacz().prepareRaidLoadout(context.survivor().id(), candidate.defenderCount());
            context.survivor().memory().combatLog().append("Raid step: Craft Explosives");
            context.survivor().setState(context.survivor().state().withMode(SurvivorState.TacticalMode.RAID_BREACH));
            context.integrations().baritone().setRaidHouseGoal(candidate.position());
            return;
        }

        if (mode == SurvivorState.TacticalMode.RAID_BREACH) {
            context.survivor().memory().combatLog().append("Raid step: Pathfind to Target + Breach Wall");
            context.integrations().baritone().setRaidHouseGoal(candidate.position());
            context.survivor().setState(context.survivor().state().withMode(SurvivorState.TacticalMode.RAID_LOOT));
            return;
        }

        if (mode == SurvivorState.TacticalMode.RAID_LOOT) {
            context.survivor().memory().combatLog().append("Raid step: Loot");
            context.integrations().baritone().setLootChestGoal(candidate.position());
            context.survivor().setState(context.survivor().state().withMode(SurvivorState.TacticalMode.RAID_RETREAT));
            return;
        }

        BlockPos retreat = context.survivor().fakePlayer().blockPosition();
        context.survivor().memory().combatLog().append("Raid step: Retreat");
        context.integrations().baritone().setRetreatGoal(retreat);
        context.survivor().setState(context.survivor().state().withMode(SurvivorState.TacticalMode.EXTRACTION));
    }
}
