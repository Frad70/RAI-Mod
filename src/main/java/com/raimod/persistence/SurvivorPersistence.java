package com.raimod.persistence;

import com.raimod.entity.SimulatedSurvivor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class SurvivorPersistence {
    private final Map<UUID, SimulatedSurvivor> byId = new HashMap<>();

    public void store(ServerLevel level, SimulatedSurvivor survivor) {
        byId.put(survivor.id(), survivor);
    }

    public List<SimulatedSurvivor> activeSurvivors(ServerLevel level) {
        return new ArrayList<>(byId.values());
    }

    public List<SimulatedSurvivor> restore(ServerLevel level) {
        return new ArrayList<>(byId.values());
    }
}
