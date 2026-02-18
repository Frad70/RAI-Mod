package com.raimod.ai.memory;

import com.raimod.entity.SimulatedSurvivor;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class SurvivorMemory {
    private final UUID survivorId;
    private final List<RaidTargetKnowledge> raidTargets;
    private final List<WorldKnowledgePoint> worldPoints;
    private final List<BlockPos> dangerousBlocks;
    private final List<BlockPos> knownChests;
    private final List<String> priorityLoot;
    private final Map<UUID, Float> relations;
    private final Map<UUID, BlockPos> lastKnownPositions;
    private final Deque<String> combatLog;
    private SurvivorState.TacticalMode currentMode;
    private BlockPos homePosition;

    private SurvivorMemory(UUID survivorId) {
        this.survivorId = survivorId;
        this.raidTargets = new ArrayList<>();
        this.worldPoints = new ArrayList<>();
        this.dangerousBlocks = new ArrayList<>();
        this.knownChests = new ArrayList<>();
        this.priorityLoot = new ArrayList<>(List.of("tacz", "ammo", "medkit"));
        this.relations = new HashMap<>();
        this.lastKnownPositions = new HashMap<>();
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

    public List<String> priorityLoot() {
        return List.copyOf(priorityLoot);
    }

    public void addPriorityLootToken(String token) {
        String normalized = token.toLowerCase();
        if (!priorityLoot.contains(normalized)) {
            priorityLoot.add(normalized);
        }
    }

    public boolean isPriorityLoot(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        for (String token : priorityLoot) {
            if (id.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldOverrideGoalForLoot(ItemStack seen, ItemStack currentMainHand) {
        if (!isPriorityLoot(seen)) {
            return false;
        }

        int seenScore = lootScore(seen);
        int currentScore = lootScore(currentMainHand);
        return seenScore > currentScore;
    }

    public void setLastKnownPosition(UUID targetId, BlockPos pos) {
        lastKnownPositions.put(targetId, pos.immutable());
    }

    public BlockPos lastKnownPosition(UUID targetId) {
        return lastKnownPositions.getOrDefault(targetId, BlockPos.ZERO);
    }

    public void clearLastKnownPosition(UUID targetId) {
        lastKnownPositions.remove(targetId);
    }

    public boolean cleanInventory(SimulatedSurvivor survivor) {
        boolean full = true;
        for (int i = 0; i < survivor.getInventory().getContainerSize(); i++) {
            if (survivor.getInventory().getItem(i).isEmpty()) {
                full = false;
                break;
            }
        }
        if (!full) {
            return false;
        }

        List<ItemEntity> nearbyDrops = survivor.level().getEntitiesOfClass(
            ItemEntity.class,
            survivor.getBoundingBox().inflate(6.0),
            drop -> !drop.getItem().isEmpty() && isPriorityLoot(drop.getItem())
        );

        if (nearbyDrops.isEmpty()) {
            return false;
        }

        int weakestSlot = -1;
        int weakestScore = Integer.MAX_VALUE;
        for (int i = 0; i < survivor.getInventory().getContainerSize(); i++) {
            ItemStack stack = survivor.getInventory().getItem(i);
            int score = lootScore(stack);
            if (score < weakestScore) {
                weakestScore = score;
                weakestSlot = i;
            }
        }

        if (weakestSlot < 0 || !survivor.canPerformActions()) {
            return false;
        }

        return survivor.dropSlotWithLatency(weakestSlot);
    }

    private int lootScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();

        int score = 5;
        if (id.contains("dirt")) {
            score -= 4;
        }
        if (id.contains("cobblestone")) {
            score -= 2;
        }
        if (id.contains("tacz")) {
            score += 50;
        }
        if (id.contains("ammo")) {
            score += 35;
        }
        if (id.contains("medkit")) {
            score += 45;
        }
        if (id.contains("legendary") || id.contains("epic")) {
            score += 25;
        }
        return score;
    }

    public Map<UUID, Float> relations() {
        return Map.copyOf(relations);
    }

    public float relationOf(UUID targetId) {
        return relations.getOrDefault(targetId, 0.0f);
    }

    public void setRelation(UUID targetId, float value) {
        relations.put(targetId, Mth.clamp(value, -1.0f, 1.0f));
    }

    public void adjustRelation(UUID targetId, float delta) {
        float next = relationOf(targetId) + delta;
        relations.put(targetId, Mth.clamp(next, -1.0f, 1.0f));
    }

    public void onAllySavedMe(UUID allyId) {
        adjustRelation(allyId, 0.2f);
    }

    public void onSuppressedBy(UUID aggressorId) {
        adjustRelation(aggressorId, -0.1f);
    }

    public void replaceRelations(Map<UUID, Float> relationValues) {
        relations.clear();
        relationValues.forEach(this::setRelation);
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
