package com.raimod.ai;

import com.raimod.entity.SimulatedSurvivor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Global squad coordinator for survivor bots.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>forms squads of 2-4 survivors;</li>
 *   <li>assigns one POINT_MAN role and SUPPORT role for all other members;</li>
 *   <li>shares target UUID + last known position to all members;</li>
 *   <li>enables suppression (support boosts) while one teammate is reloading.</li>
 * </ul>
 */
public final class GroupManager {
    private static final GroupManager INSTANCE = new GroupManager();

    private final Map<UUID, UUID> squadByMember = new HashMap<>();
    private final Map<UUID, Squad> squads = new HashMap<>();

    private GroupManager() {
    }

    public static GroupManager instance() {
        return INSTANCE;
    }

    public Squad squadOf(UUID survivorId) {
        UUID squadId = squadByMember.get(survivorId);
        if (squadId == null) {
            return null;
        }
        return squads.get(squadId);
    }

    public Squad registerSurvivor(SimulatedSurvivor survivor, int maxSize) {
        UUID id = survivor.id();
        Squad existing = squadOf(id);
        if (existing != null) {
            return existing;
        }

        int cappedSize = Math.max(2, Math.min(4, maxSize));
        Squad target = squads.values().stream()
            .filter(s -> s.members().size() < cappedSize)
            .findFirst()
            .orElseGet(() -> {
                Squad created = new Squad(UUID.randomUUID(), new ArrayList<>(), null, null, null, false);
                squads.put(created.id(), created);
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
                .filter(java.util.Objects::nonNull)
                .max(Comparator.comparingDouble(bot -> bot.trustFactor() + bot.getHealth() / 20.0f))
                .map(SimulatedSurvivor::id)
                .orElse(pointManId);
        }
    }
}
