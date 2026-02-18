package com.raimod.ai;

import com.raimod.RAIMod;
import com.raimod.config.RAIServerConfig;
import com.raimod.entity.SimulatedSurvivor;
import com.raimod.integration.ModIntegrationRegistry;
import com.raimod.persistence.SurvivorPersistence;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AIDirectorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIDirectorService.class);

    private final SurvivorPersistence persistence;
    private final ModIntegrationRegistry integrationRegistry;
    private RAIServerConfig.RuntimeValues config;
    private boolean initialized;
    private int saveTimer;

    public AIDirectorService() {
        this.persistence = new SurvivorPersistence();
        this.integrationRegistry = new ModIntegrationRegistry();
        this.config = RAIServerConfig.runtime();
    }

    public void tick(MinecraftServer server) {
        if (!initialized) {
            initialize(server);
            initialized = true;
        }

        integrationRegistry.tick(server);

        List<SimulatedSurvivor> all = persistence.activeSurvivors(server.overworld());
        all.forEach(bot -> bot.configureRuntime(integrationRegistry, config));
        all.forEach(SimulatedSurvivor::tick);

        if (all.size() < config.maxPlayers()) {
            spawnMissingSurvivors(server, config.maxPlayers() - all.size());
        }

        saveTimer++;
        if (saveTimer >= 100) {
            persistence.saveAll(server.overworld());
            saveTimer = 0;
        }
    }

    public void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (!event.getConfig().getModId().equals(RAIMod.MOD_ID)) {
            return;
        }

        RAIServerConfig.RuntimeValues newConfig = RAIServerConfig.runtime();
        LOGGER.info("RAI config reloaded, restarting AI runtime. New maxPlayers={}, chunks={}..{}",
            newConfig.maxPlayers(),
            newConfig.minActiveChunks(),
            newConfig.maxActiveChunks());

        this.config = newConfig;
        this.initialized = false;
    }

    private void initialize(MinecraftServer server) {
        integrationRegistry.bootstrap(server);

        List<SimulatedSurvivor> restored = persistence.restore(server.overworld(), config);
        LOGGER.info("Restored {} simulated survivors from persistent storage", restored.size());

        restored.forEach(bot -> {
            bot.configureRuntime(integrationRegistry, config);
            bot.resetRuntime(config);
        });
    }

    private void spawnMissingSurvivors(MinecraftServer server, int missing) {
        List<SimulatedSurvivor> spawned = new ArrayList<>();
        for (int i = 0; i < missing; i++) {
            SimulatedSurvivor survivor = SimulatedSurvivor.bootstrap(UUID.randomUUID(), config, server.overworld());
            survivor.configureRuntime(integrationRegistry, config);
            persistence.store(server.overworld(), survivor);
            spawned.add(survivor);
        }

        if (!spawned.isEmpty()) {
            LOGGER.info("Spawned {} new simulated survivors to match desired population", spawned.size());
        }
    }
}
