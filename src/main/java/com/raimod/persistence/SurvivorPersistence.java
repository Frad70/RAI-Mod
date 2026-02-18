package com.raimod.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.raimod.ai.memory.RaidTargetKnowledge;
import com.raimod.ai.memory.SurvivorMemory;
import com.raimod.ai.memory.WorldKnowledgePoint;
import com.raimod.config.RAIServerConfig;
import com.raimod.entity.SimulatedSurvivor;
import com.raimod.entity.SurvivorState;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class SurvivorPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, SimulatedSurvivor> byId = new HashMap<>();

    public void store(ServerLevel level, SimulatedSurvivor survivor) {
        byId.put(survivor.id(), survivor);
        saveOne(level, survivor);
    }

    public void saveAll(ServerLevel level) {
        byId.values().forEach(survivor -> saveOne(level, survivor));
    }

    public List<SimulatedSurvivor> activeSurvivors(ServerLevel level) {
        return new ArrayList<>(byId.values());
    }

    public List<SimulatedSurvivor> restore(ServerLevel level, RAIServerConfig.RuntimeValues config) {
        byId.clear();
        Path dir = storageDir(level);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(path -> loadOne(level, config, path));
        } catch (IOException ignored) {
        }

        return new ArrayList<>(byId.values());
    }

    private void loadOne(ServerLevel level, RAIServerConfig.RuntimeValues config, Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            UUID id = UUID.fromString(root.get("id").getAsString());
            SimulatedSurvivor survivor = SimulatedSurvivor.bootstrap(id, config, level);

            JsonObject memoryJson = root.getAsJsonObject("memory");
            JsonArray pointsJson = memoryJson.getAsJsonArray("worldPoints");
            List<WorldKnowledgePoint> points = new ArrayList<>();
            for (int i = 0; i < pointsJson.size(); i++) {
                JsonObject p = pointsJson.get(i).getAsJsonObject();
                points.add(new WorldKnowledgePoint(
                    p.get("key").getAsString(),
                    new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt()),
                    p.get("category").getAsString(),
                    p.get("danger").getAsFloat(),
                    p.get("lastVerifiedTick").getAsLong(),
                    p.get("needsRevalidation").getAsBoolean()
                ));
            }

            JsonArray raidsJson = memoryJson.getAsJsonArray("raidTargets");
            List<RaidTargetKnowledge> raids = new ArrayList<>();
            for (int i = 0; i < raidsJson.size(); i++) {
                JsonObject r = raidsJson.get(i).getAsJsonObject();
                raids.add(new RaidTargetKnowledge(
                    r.get("structureName").getAsString(),
                    new BlockPos(r.get("x").getAsInt(), r.get("y").getAsInt(), r.get("z").getAsInt()),
                    r.get("knownChestCount").getAsInt(),
                    r.get("defenderCount").getAsInt(),
                    r.get("estimatedLootValue").getAsDouble(),
                    r.get("expectedRaidCost").getAsDouble(),
                    r.get("wallHardness").getAsInt(),
                    r.get("hasKnownEntryPoint").getAsBoolean()
                ));
            }

            SurvivorMemory memory = survivor.memory();
            memory.replaceWorldPoints(points);
            memory.replaceRaidTargets(raids);
            byId.put(id, survivor);
        } catch (Exception ignored) {
        }
    }

    private void saveOne(ServerLevel level, SimulatedSurvivor survivor) {
        Path dir = storageDir(level);
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(survivor.id() + ".json");
            JsonObject root = new JsonObject();
            root.addProperty("id", survivor.id().toString());
            JsonObject memoryJson = new JsonObject();

            JsonArray pointsJson = new JsonArray();
            for (WorldKnowledgePoint point : survivor.memory().worldPoints()) {
                JsonObject p = new JsonObject();
                p.addProperty("key", point.key());
                p.addProperty("x", point.pos().getX());
                p.addProperty("y", point.pos().getY());
                p.addProperty("z", point.pos().getZ());
                p.addProperty("category", point.category());
                p.addProperty("danger", point.danger());
                p.addProperty("lastVerifiedTick", point.lastVerifiedTick());
                p.addProperty("needsRevalidation", point.needsRevalidation());
                pointsJson.add(p);
            }

            JsonArray raidsJson = new JsonArray();
            for (RaidTargetKnowledge raid : survivor.memory().raidTargets()) {
                JsonObject r = new JsonObject();
                r.addProperty("structureName", raid.structureName());
                r.addProperty("x", raid.position().getX());
                r.addProperty("y", raid.position().getY());
                r.addProperty("z", raid.position().getZ());
                r.addProperty("knownChestCount", raid.knownChestCount());
                r.addProperty("defenderCount", raid.defenderCount());
                r.addProperty("estimatedLootValue", raid.estimatedLootValue());
                r.addProperty("expectedRaidCost", raid.expectedRaidCost());
                r.addProperty("wallHardness", raid.wallHardness());
                r.addProperty("hasKnownEntryPoint", raid.hasKnownEntryPoint());
                raidsJson.add(r);
            }

            memoryJson.add("worldPoints", pointsJson);
            memoryJson.add("raidTargets", raidsJson);
            root.add("memory", memoryJson);

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private Path storageDir(ServerLevel level) {
        return level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("rai_players");
    }
}
