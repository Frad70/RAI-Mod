package com.raimod.ai.memory;

import com.raimod.entity.SurvivorState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class SurvivorMemory {
    private final UUID survivorId;
    private final List<RaidTargetKnowledge> raidTargets;
    private final List<WorldKnowledgePoint> worldPoints;
    private final Deque<String> combatLog;
    private SurvivorState.TacticalMode currentMode;

    private SurvivorMemory(UUID survivorId) {
        this.survivorId = survivorId;
        this.raidTargets = new ArrayList<>();
        this.worldPoints = new ArrayList<>();
        this.combatLog = new ArrayDeque<>();
        this.currentMode = SurvivorState.TacticalMode.SCOUTING;
    }

    public static SurvivorMemory createEmpty(UUID survivorId) {
        return new SurvivorMemory(survivorId);
    }

    public UUID survivorId() {
        return survivorId;
    }

    public RaidTargetKnowledge bestRaidCandidate() {
        return raidTargets.stream()
            .max(Comparator.comparingDouble(RaidTargetKnowledge::priorityScore))
            .orElse(null);
    }

    public void rememberRaidTarget(RaidTargetKnowledge knowledge) {
        raidTargets.removeIf(existing -> existing.structureName().equalsIgnoreCase(knowledge.structureName()));
        raidTargets.add(knowledge);
    }

    public void rememberWorldPoint(WorldKnowledgePoint point) {
        worldPoints.removeIf(existing -> existing.key().equals(point.key()));
        worldPoints.add(point);
    }

    public CombatLog combatLog() {
        return new CombatLog(combatLog);
    }

    public void setCurrentMode(SurvivorState.TacticalMode currentMode) {
        this.currentMode = currentMode;
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
    }
}
