package com.raimod.ai;

import com.raimod.config.RAIServerConfig;
import com.raimod.entity.SimulatedSurvivor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Global squad coordinator for survivor bots.
 */
public final class GroupManager {
    private static final GroupManager INSTANCE = new GroupManager();

    private final Map<UUID, UUID> squadByMember = new HashMap<>();
    private final Map<UUID, Squad> squads = new HashMap<>();
    private final FactionRegistry factions = new FactionRegistry();
    private RAIServerConfig.RuntimeValues runtime;

    private GroupManager() {
    }

    public static GroupManager instance() {
        return INSTANCE;
    }

    public void configure(RAIServerConfig.RuntimeValues runtime) {
        this.runtime = runtime;
        this.factions.configure(runtime);
    }

    public Squad squadOf(UUID survivorId) {
        UUID squadId = squadByMember.get(survivorId);
        if (squadId == null) {
            return null;
        }
        return squads.get(squadId);
    }

    public UUID squadIdOf(UUID survivorId) {
        return squadByMember.get(survivorId);
    }

    public Squad registerSurvivor(SimulatedSurvivor survivor, int maxSize) {
        UUID id = survivor.id();
        Squad existing = squadOf(id);
        if (existing != null) {
            return existing;
        }

        int configMax = runtime != null ? runtime.squadMaxSize() : 4;
        int cappedSize = Math.max(2, Math.min(4, Math.min(maxSize, configMax)));
        Squad target = squads.values().stream()
            .filter(s -> s.members().size() < cappedSize)
            .findFirst()
            .orElseGet(() -> {
                Squad created = new Squad(UUID.randomUUID(), new ArrayList<>(), null, null, null, false);
                squads.put(created.id(), created);
                factions.assignSquad(created.id());
                return created;
            });

        target.members().add(id);
        squadByMember.put(id, target.id());
        if (target.members().size() == 1) {
            target.pointManId = id;
        }
        return target;
    }

    public void reportTargetSpotted(SimulatedSurvivor reporter, UUID targetId, BlockPos lkp) {
        Squad squad = squadOf(reporter.id());
        if (squad == null || targetId == null || lkp == null) {
            return;
        }
        squad.sharedTargetId = targetId;
        squad.sharedLastKnownPosition = lkp.immutable();
    }

    public Role roleOf(UUID memberId) {
        Squad squad = squadOf(memberId);
        if (squad == null) {
            return Role.SOLO;
        }
        return memberId.equals(squad.pointManId) ? Role.POINT_MAN : Role.SUPPORT;
    }

    public void setReloading(SimulatedSurvivor survivor, boolean reloading) {
        Squad squad = squadOf(survivor.id());
        if (squad == null) {
            return;
        }
        squad.teammateReloading = reloading;
    }

    public boolean shouldSuppressForTeammate(SimulatedSurvivor survivor) {
        Squad squad = squadOf(survivor.id());
        return squad != null && squad.teammateReloading && roleOf(survivor.id()) == Role.SUPPORT;
    }

    public boolean hasConfidenceBuff(SimulatedSurvivor survivor) {
        Squad squad = squadOf(survivor.id());
        return squad != null && squad.teammateReloading;
    }

    public List<SimulatedSurvivor> onlineSquadmates(SimulatedSurvivor survivor) {
        Squad squad = squadOf(survivor.id());
        if (squad == null) {
            return List.of();
        }

        List<SimulatedSurvivor> members = new ArrayList<>();
        for (UUID memberId : squad.members()) {
            if (memberId.equals(survivor.id())) {
                continue;
            }
            var entity = survivor.serverLevel().getEntity(memberId);
            if (entity instanceof SimulatedSurvivor teammate && teammate.isAlive()) {
                members.add(teammate);
            }
        }
        return members;
    }

    public SimulatedSurvivor findBestAmmoDonor(SimulatedSurvivor requester) {
        return onlineSquadmates(requester).stream()
            .filter(teammate -> teammate.distanceTo(requester) < 14.0f)
            .filter(teammate -> teammate.ammoRatio() > 0.45)
            .max(Comparator.comparingDouble(SimulatedSurvivor::ammoRatio))
            .orElse(null);
    }

    public SimulatedSurvivor findNearestMedic(SimulatedSurvivor injured) {
        return onlineSquadmates(injured).stream()
            .filter(teammate -> teammate.distanceTo(injured) < 16.0f)
            .filter(SimulatedSurvivor::hasMedkit)
            .min(Comparator.comparingDouble(bot -> bot.distanceTo(injured)))
            .orElse(null);
    }

    public FactionRegistry.FactionId factionOf(SimulatedSurvivor survivor) {
        Squad squad = squadOf(survivor.id());
        if (squad == null) {
            return FactionRegistry.FactionId.STAYERS;
        }
        return factions.factionOf(squad.id());
    }

    public boolean areHostile(SimulatedSurvivor a, SimulatedSurvivor b) {
        UUID squadA = squadIdOf(a.id());
        UUID squadB = squadIdOf(b.id());
        if (squadA == null || squadB == null) {
            return false;
        }
        return factions.areHostile(squadA, squadB);
    }

    public void reportTrade(SimulatedSurvivor one, SimulatedSurvivor two) {
        UUID squadA = squadIdOf(one.id());
        UUID squadB = squadIdOf(two.id());
        if (squadA == null || squadB == null || Objects.equals(squadA, squadB)) {
            return;
        }
        factions.recordTrade(squadA, squadB);
    }

    public void reportKill(SimulatedSurvivor killer, SimulatedSurvivor victim) {
        UUID squadA = squadIdOf(killer.id());
        UUID squadB = squadIdOf(victim.id());
        if (squadA == null || squadB == null || Objects.equals(squadA, squadB)) {
            return;
        }
        factions.recordKill(squadA, squadB);
    }

    public void cleanup() {
        List<UUID> emptySquads = squads.values().stream()
            .filter(squad -> squad.members().isEmpty())
            .map(Squad::id)
            .toList();
        for (UUID empty : emptySquads) {
            squads.remove(empty);
        }
    }

    public enum Role {
        SOLO,
        POINT_MAN,
        SUPPORT
    }

    public static final class Squad {
        private final UUID id;
        private final List<UUID> members;
        private UUID pointManId;
        private UUID sharedTargetId;
        private BlockPos sharedLastKnownPosition;
        private boolean teammateReloading;

        private Squad(UUID id, List<UUID> members, UUID pointManId, UUID sharedTargetId, BlockPos sharedLastKnownPosition,
                      boolean teammateReloading) {
            this.id = id;
            this.members = members;
            this.pointManId = pointManId;
            this.sharedTargetId = sharedTargetId;
            this.sharedLastKnownPosition = sharedLastKnownPosition;
            this.teammateReloading = teammateReloading;
        }

        public UUID id() {
            return id;
        }

        public List<UUID> members() {
            return members;
        }

        public UUID pointManId() {
            return pointManId;
        }

        public UUID sharedTargetId() {
            return sharedTargetId;
        }

        public BlockPos sharedLastKnownPosition() {
            return sharedLastKnownPosition;
        }

        public void rebalancePointMan(Map<UUID, SimulatedSurvivor> online) {
            pointManId = members.stream()
                .map(online::get)
                .filter(Objects::nonNull)
                .max(Comparator.comparingDouble(bot -> bot.trustFactor() + bot.getHealth() / 20.0f))
                .map(SimulatedSurvivor::id)
                .orElse(pointManId);
        }
    }
}
