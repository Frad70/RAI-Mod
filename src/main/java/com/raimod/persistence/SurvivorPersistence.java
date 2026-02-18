package com.raimod.persistence;

import com.raimod.ai.memory.RaidTargetKnowledge;
import com.raimod.ai.memory.SurvivorMemory;
import com.raimod.ai.memory.WorldKnowledgePoint;
import com.raimod.config.RAIServerConfig;
import com.raimod.entity.SimulatedSurvivor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

public final class SurvivorPersistence {
    private final Map<UUID, SimulatedSurvivor> loaded = new HashMap<>();

    public void store(ServerLevel level, SimulatedSurvivor survivor) {
        loaded.put(survivor.id(), survivor);
        saveToDisk(level, survivor.id());
    }

    public List<SimulatedSurvivor> activeSurvivors(ServerLevel level) {
        return new ArrayList<>(loaded.values());
    }

    public void saveAll(ServerLevel level) {
        for (UUID id : loaded.keySet()) {
            saveToDisk(level, id);
        }
    }

    public void saveToDisk(ServerLevel level, UUID id) {
        SimulatedSurvivor survivor = loaded.get(id);
        if (survivor == null) {
            return;
        }

        CompoundTag root = new CompoundTag();
        root.putUUID("Id", id);

        SurvivorMemory memory = survivor.memory();
        root.putLong("HomePos", memory.homePosition().asLong());

        ListTag chests = new ListTag();
        for (BlockPos chest : memory.knownChests()) {
            chests.add(StringTag.valueOf(Long.toString(chest.asLong())));
        }
        root.put("KnownChests", chests);

        ListTag relations = new ListTag();
        for (Map.Entry<UUID, Float> entry : memory.relations().entrySet()) {
            CompoundTag relation = new CompoundTag();
            relation.putUUID("Player", entry.getKey());
            relation.putFloat("Value", entry.getValue());
            relations.add(relation);
        }
        root.put("Relations", relations);

        ListTag raids = new ListTag();
        for (RaidTargetKnowledge raid : memory.raidTargets()) {
            CompoundTag raidTag = new CompoundTag();
            raidTag.putString("Name", raid.structureName());
            raidTag.putLong("Pos", raid.position().asLong());
            raidTag.putInt("KnownChestCount", raid.knownChestCount());
            raidTag.putInt("DefenderCount", raid.defenderCount());
            raidTag.putDouble("EstimatedLootValue", raid.estimatedLootValue());
            raidTag.putDouble("ExpectedRaidCost", raid.expectedRaidCost());
            raidTag.putInt("WallHardness", raid.wallHardness());
            raidTag.putBoolean("KnownEntry", raid.hasKnownEntryPoint());
            raids.add(raidTag);
        }
        root.put("RaidTargets", raids);

        ListTag points = new ListTag();
        for (WorldKnowledgePoint point : memory.worldPoints()) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putString("Key", point.key());
            pointTag.putLong("Pos", point.pos().asLong());
            pointTag.putString("Category", point.category());
            pointTag.putFloat("Danger", point.danger());
            pointTag.putLong("LastVerified", point.lastVerifiedTick());
            pointTag.putBoolean("NeedsRevalidation", point.needsRevalidation());
            points.add(pointTag);
        }
        root.put("WorldPoints", points);

        try {
            Path path = storageDir(level).resolve(id + ".dat");
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(root, path);
        } catch (IOException ignored) {
        }
    }

    public List<SimulatedSurvivor> restore(ServerLevel level, RAIServerConfig.RuntimeValues config) {
        loaded.clear();
        Path dir = storageDir(level);
        if (!Files.exists(dir)) {
            return List.of();
        }

        try (var paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".dat")).forEach(path -> {
                try {
                    CompoundTag root = NbtIo.readCompressed(path);
                    UUID id = root.getUUID("Id");
                    SimulatedSurvivor survivor = SimulatedSurvivor.bootstrap(id, config, level);
                    SurvivorMemory memory = survivor.memory();

                    memory.setHomePosition(BlockPos.of(root.getLong("HomePos")));

                    ListTag chests = root.getList("KnownChests", Tag.TAG_STRING);
                    List<BlockPos> knownChests = new ArrayList<>();
                    for (int i = 0; i < chests.size(); i++) {
                        knownChests.add(BlockPos.of(Long.parseLong(chests.getString(i))));
                    }
                    memory.setKnownChests(knownChests);

                    ListTag relations = root.getList("Relations", Tag.TAG_COMPOUND);
                    Map<UUID, Float> relationMap = new HashMap<>();
                    for (int i = 0; i < relations.size(); i++) {
                        CompoundTag relation = relations.getCompound(i);
                        relationMap.put(relation.getUUID("Player"), relation.getFloat("Value"));
                    }
                    memory.replaceRelations(relationMap);

                    ListTag raidTags = root.getList("RaidTargets", Tag.TAG_COMPOUND);
                    List<RaidTargetKnowledge> raids = new ArrayList<>();
                    for (int i = 0; i < raidTags.size(); i++) {
                        CompoundTag raidTag = raidTags.getCompound(i);
                        raids.add(new RaidTargetKnowledge(
                            raidTag.getString("Name"),
                            BlockPos.of(raidTag.getLong("Pos")),
                            raidTag.getInt("KnownChestCount"),
                            raidTag.getInt("DefenderCount"),
                            raidTag.getDouble("EstimatedLootValue"),
                            raidTag.getDouble("ExpectedRaidCost"),
                            raidTag.getInt("WallHardness"),
                            raidTag.getBoolean("KnownEntry")
                        ));
                    }
                    memory.replaceRaidTargets(raids);

                    ListTag worldPointTags = root.getList("WorldPoints", Tag.TAG_COMPOUND);
                    List<WorldKnowledgePoint> points = new ArrayList<>();
                    for (int i = 0; i < worldPointTags.size(); i++) {
                        CompoundTag pointTag = worldPointTags.getCompound(i);
                        points.add(new WorldKnowledgePoint(
                            pointTag.getString("Key"),
                            BlockPos.of(pointTag.getLong("Pos")),
                            pointTag.getString("Category"),
                            pointTag.getFloat("Danger"),
                            pointTag.getLong("LastVerified"),
                            pointTag.getBoolean("NeedsRevalidation")
                        ));
                    }
                    memory.replaceWorldPoints(points);
                    loaded.put(id, survivor);
                } catch (Exception ignored) {
                }
            });
        } catch (IOException ignored) {
        }

        return new ArrayList<>(loaded.values());
    }

    private Path storageDir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("rai_players");
    }
}
