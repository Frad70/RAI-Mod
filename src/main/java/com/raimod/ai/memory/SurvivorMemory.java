package com.raimod.ai.memory;

import com.raimod.entity.SurvivorState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

public final class SurvivorMemory {
    private final UUID survivorId;
    private final List<RaidTargetKnowledge> raidTargets;
    private final List<WorldKnowledgePoint> worldPoints;
    private final List<BlockPos> dangerousBlocks;
    private final List<BlockPos> knownChests;
    private final Map<UUID, Integer> relationMatrix;
    private final Deque<String> combatLog;
    private SurvivorState.TacticalMode currentMode;
    private BlockPos homePosition;

    private SurvivorMemory(UUID survivorId) {
        this.survivorId = survivorId;
        this.raidTargets = new ArrayList<>();
        this.worldPoints = new ArrayList<>();
        this.dangerousBlocks = new ArrayList<>();
        this.knownChests = new ArrayList<>();
        this.relationMatrix = new HashMap<>();
        this.combatLog = new ArrayDeque<>();
        this.currentMode = SurvivorState.TacticalMode.SCOUTING;
        this.homePosition = BlockPos.ZERO;
    }

    public static SurvivorMemory createEmpty(UUID survivorId) {
        return new SurvivorMemory(survivorId);
    }

    public UUID survivorId() {
        return survivorId;
    }

    public RaidTargetKnowledge bestRaidCandidate() {
        return raidTargets.stream().max(Comparator.comparingInt(RaidTargetKnowledge::knownChestCount)).orElse(null);
    }

    public void rememberRaidTarget(RaidTargetKnowledge knowledge) {
        raidTargets.removeIf(existing -> existing.structureName().equalsIgnoreCase(knowledge.structureName()));
        raidTargets.add(knowledge);
    }

    public List<RaidTargetKnowledge> raidTargets() {
        return List.copyOf(raidTargets);
    }

    public void rememberWorldPoint(WorldKnowledgePoint point) {
        worldPoints.removeIf(existing -> existing.key().equals(point.key()));
        worldPoints.add(point);
    }

    public List<WorldKnowledgePoint> worldPoints() {
        return List.copyOf(worldPoints);
    }

    public void replaceWorldPoints(List<WorldKnowledgePoint> points) {
        worldPoints.clear();
        worldPoints.addAll(points);
    }

    public void replaceRaidTargets(List<RaidTargetKnowledge> targets) {
        raidTargets.clear();
        raidTargets.addAll(targets);
    }

    public CombatLog combatLog() {
        return new CombatLog(combatLog);
    }

    public void setCurrentMode(SurvivorState.TacticalMode currentMode) {
        this.currentMode = currentMode;
    }

    public SurvivorState.TacticalMode currentMode() {
        return currentMode;
    }

    public BlockPos homePosition() {
        return homePosition;
    }

    public void setHomePosition(BlockPos homePosition) {
        this.homePosition = homePosition.immutable();
    }

    public List<BlockPos> dangerousBlocks() {
        return List.copyOf(dangerousBlocks);
    }

    public void setDangerousBlocks(List<BlockPos> positions) {
        dangerousBlocks.clear();
        dangerousBlocks.addAll(positions);
    }

    public List<BlockPos> knownChests() {
        return List.copyOf(knownChests);
    }

    public void setKnownChests(List<BlockPos> chests) {
        knownChests.clear();
        knownChests.addAll(chests);
    }

    public Map<UUID, Integer> relationMatrix() {
        return Map.copyOf(relationMatrix);
    }

    public void setRelation(UUID targetId, int value) {
        relationMatrix.put(targetId, value);
    }

    public void replaceRelations(Map<UUID, Integer> relations) {
        relationMatrix.clear();
        relationMatrix.putAll(relations);
    }

    public void refreshStaleKnowledge(MinecraftServer server, int revalidationSeconds) {
        long now = server.overworld().getGameTime();
        long staleAt = revalidationSeconds * 20L;

        for (int i = 0; i < worldPoints.size(); i++) {
            WorldKnowledgePoint point = worldPoints.get(i);
            if (now - point.lastVerifiedTick() > staleAt) {
                worldPoints.set(i, point.withNeedsRevalidation(true));
            }
        }
    }

    public record CombatLog(Deque<String> lines) {
        public void append(String line) {
            lines.addLast(line);
            while (lines.size() > 128) {
                lines.removeFirst();
            }
        }

        public List<String> snapshot() {
            return List.copyOf(lines);
        }
    }
}
