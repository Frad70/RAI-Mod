package com.raimod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RAIServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_SIMULATED_PLAYERS;
    public static final ModConfigSpec.IntValue MIN_ACTIVE_CHUNKS;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_CHUNKS;
    public static final ModConfigSpec.BooleanValue ENABLE_DYNAMIC_CHUNK_BUDGET;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVER_SOUND_AWARENESS;
    public static final ModConfigSpec.IntValue MEMORY_REVALIDATION_SECONDS;
    public static final ModConfigSpec.DoubleValue BASE_AIM_SCATTER_DEGREES;
    public static final ModConfigSpec.DoubleValue RAID_THRESHOLD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("players");
        MAX_SIMULATED_PLAYERS = builder
            .comment("Maximum number of simulated survivor players. Hard cap: 20")
            .defineInRange("maxSimulatedPlayers", 5, 1, 20);
        builder.pop();

        builder.push("chunk_loading");
        ENABLE_DYNAMIC_CHUNK_BUDGET = builder
            .comment("Dynamically adapts chunk loading budget based on server performance")
            .define("enableDynamicChunkBudget", true);
        MIN_ACTIVE_CHUNKS = builder
            .comment("Minimum loaded chunks by each AI player. Hard cap: 1")
            .defineInRange("minActiveChunks", 1, 1, 1);
        MAX_ACTIVE_CHUNKS = builder
            .comment("Maximum loaded chunks by each AI player. Hard cap: 12")
            .defineInRange("maxActiveChunks", 4, 1, 12);
        builder.pop();

        builder.push("awareness");
        ENABLE_SERVER_SOUND_AWARENESS = builder
            .comment("If true, AI consumes step/weapon/build sounds in local tactical area")
            .define("enableServerSoundAwareness", true);
        MEMORY_REVALIDATION_SECONDS = builder
            .comment("How often stale memory points are revalidated through scouting")
            .defineInRange("memoryRevalidationSeconds", 240, 30, 3600);
        builder.pop();

        builder.push("combat");
        BASE_AIM_SCATTER_DEGREES = builder
            .comment("Base human-like scatter in degrees before physical stats correction")
            .defineInRange("baseAimScatterDegrees", 2.2d, 0.0d, 15.0d);
        builder.pop();

        builder.push("raiding");
        RAID_THRESHOLD = builder
            .comment("Minimum utility score required to start raid sequence")
            .defineInRange("raidThreshold", 120.0d, 1.0d, 5000.0d);
        builder.pop();

        SPEC = builder.build();
    }

    private RAIServerConfig() {
    }

    public static RuntimeValues runtime() {
        int min = MIN_ACTIVE_CHUNKS.get();
        int max = Math.max(min, MAX_ACTIVE_CHUNKS.get());
        return new RuntimeValues(
            MAX_SIMULATED_PLAYERS.get(),
            min,
            max,
            ENABLE_DYNAMIC_CHUNK_BUDGET.get(),
            ENABLE_SERVER_SOUND_AWARENESS.get(),
            MEMORY_REVALIDATION_SECONDS.get(),
            BASE_AIM_SCATTER_DEGREES.get(),
            RAID_THRESHOLD.get()
        );
    }

    public record RuntimeValues(
        int maxPlayers,
        int minActiveChunks,
        int maxActiveChunks,
        boolean dynamicChunkBudget,
        boolean soundAwareness,
        int memoryRevalidationSeconds,
        double baseAimScatterDegrees,
        double raidThreshold
    ) {
    }
}
