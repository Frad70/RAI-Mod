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

    public static final ModConfigSpec.DoubleValue FOV_DAY;
    public static final ModConfigSpec.DoubleValue FOV_NIGHT;
    public static final ModConfigSpec.DoubleValue HEARING_RANGE;
    public static final ModConfigSpec.DoubleValue STEALTH_MULTIPLIER;

    public static final ModConfigSpec.IntValue BASE_DELAY_TICKS;
    public static final ModConfigSpec.IntValue SLOT_DISTANCE_PENALTY;
    public static final ModConfigSpec.ConfigValue<String> JUNK_LIST;

    public static final ModConfigSpec.DoubleValue ACCURACY_SKILL;
    public static final ModConfigSpec.DoubleValue RECOIL_COMPENSATION_FACTOR;
    public static final ModConfigSpec.DoubleValue BETRAYAL_CHANCE;

    public static final ModConfigSpec.DoubleValue REPUTATION_GAIN_SAVE_ALLY;
    public static final ModConfigSpec.DoubleValue REPUTATION_LOSS_FRIENDLY_FIRE;
    public static final ModConfigSpec.IntValue SQUAD_MAX_SIZE;

    public static final ModConfigSpec.IntValue CLAIM_RADIUS;
    public static final ModConfigSpec.DoubleValue STORAGE_PRIORITY_THRESHOLD;
    public static final ModConfigSpec.IntValue WINDOW_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue TRAP_SCAN_INTERVAL_TICKS;

    public static final ModConfigSpec.DoubleValue FACTION_TRADE_GAIN;
    public static final ModConfigSpec.DoubleValue FACTION_KILL_LOSS;

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
        BETRAYAL_CHANCE = builder
            .comment("[combat] Probability (0..1) of betrayal branch when distrust is high")
            .defineInRange("betrayal_chance", 0.10d, 0.0d, 1.0d);
        ACCURACY_SKILL = builder
            .comment("[combat] Base marksmanship skill (0..1), affects lead-shot scatter")
            .defineInRange("accuracy_skill", 0.55d, 0.0d, 1.0d);
        RECOIL_COMPENSATION_FACTOR = builder
            .comment("[combat] Strength of recoil compensation for automatic S-curve control")
            .defineInRange("recoil_compensation_factor", 0.85d, 0.0d, 3.0d);
        builder.pop();

        builder.push("raiding");
        RAID_THRESHOLD = builder
            .comment("Minimum utility score required to start raid sequence")
            .defineInRange("raidThreshold", 120.0d, 1.0d, 5000.0d);
        builder.pop();

        builder.push("sensory");
        FOV_DAY = builder
            .comment("[sensory] Daytime field of view in degrees for LOS cone checks")
            .defineInRange("fov_day", 60.0d, 10.0d, 180.0d);
        FOV_NIGHT = builder
            .comment("[sensory] Night/low-light field of view in degrees")
            .defineInRange("fov_night", 35.0d, 5.0d, 180.0d);
        HEARING_RANGE = builder
            .comment("[sensory] Distance in blocks where loud sounds are treated as local")
            .defineInRange("hearing_range", 10.0d, 1.0d, 128.0d);
        STEALTH_MULTIPLIER = builder
            .comment("[sensory] Visibility multiplier for sneaking entities (lower = harder to spot)")
            .defineInRange("stealth_multiplier", 0.65d, 0.1d, 2.0d);
        builder.pop();

        builder.push("inventory");
        BASE_DELAY_TICKS = builder
            .comment("[inventory] Base interaction delay in ticks for item actions")
            .defineInRange("base_delay_ticks", 1, 0, 20);
        SLOT_DISTANCE_PENALTY = builder
            .comment("[inventory] Extra delay per distant hotbar/slot jump")
            .defineInRange("slot_distance_penalty", 2, 0, 20);
        JUNK_LIST = builder
            .comment("[inventory] Comma-separated item-id tokens considered low-priority junk")
            .define("junk_list", "dirt,cobblestone,rotten_flesh");
        builder.pop();

        builder.push("social");
        REPUTATION_GAIN_SAVE_ALLY = builder
            .comment("[social] Relation gain when an ally saves this survivor")
            .defineInRange("reputation_gain_save_ally", 0.2d, 0.0d, 1.0d);
        REPUTATION_LOSS_FRIENDLY_FIRE = builder
            .comment("[social] Relation loss when taking friendly fire")
            .defineInRange("reputation_loss_friendly_fire", 0.1d, 0.0d, 1.0d);
        SQUAD_MAX_SIZE = builder
            .comment("[social] Maximum members in a squad (hard-limited to 4 by AI logic)")
            .defineInRange("squad_max_size", 4, 2, 4);
        builder.pop();

        builder.push("base");
        CLAIM_RADIUS = builder
            .comment("[base] Radius in blocks used for home-base territory checks")
            .defineInRange("claim_radius", 24, 8, 128);
        STORAGE_PRIORITY_THRESHOLD = builder
            .comment("[base] Inventory fill ratio before bot returns home to stash loot")
            .defineInRange("storage_priority_threshold", 0.60d, 0.1d, 1.0d);
        WINDOW_CHECK_INTERVAL_TICKS = builder
            .comment("[base] Tick interval between paranoid window scans in DEFEND_HOME")
            .defineInRange("window_check_interval_ticks", 80, 20, 600);
        TRAP_SCAN_INTERVAL_TICKS = builder
            .comment("[base] Tick interval for TNT/pressure-plate trap checks around home")
            .defineInRange("trap_scan_interval_ticks", 40, 20, 400);
        builder.pop();

        builder.push("factions");
        FACTION_TRADE_GAIN = builder
            .comment("[factions] Relation gain between STAYERS and SETTLERS per successful trade")
            .defineInRange("trade_gain", 0.08d, 0.0d, 1.0d);
        FACTION_KILL_LOSS = builder
            .comment("[factions] Relation penalty between STAYERS and SETTLERS per kill")
            .defineInRange("kill_loss", 0.15d, 0.0d, 1.0d);
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
            RAID_THRESHOLD.get(),
            FOV_DAY.get(),
            FOV_NIGHT.get(),
            HEARING_RANGE.get(),
            STEALTH_MULTIPLIER.get(),
            BASE_DELAY_TICKS.get(),
            SLOT_DISTANCE_PENALTY.get(),
            JUNK_LIST.get(),
            ACCURACY_SKILL.get().floatValue(),
            RECOIL_COMPENSATION_FACTOR.get(),
            BETRAYAL_CHANCE.get(),
            REPUTATION_GAIN_SAVE_ALLY.get().floatValue(),
            REPUTATION_LOSS_FRIENDLY_FIRE.get().floatValue(),
            SQUAD_MAX_SIZE.get(),
            CLAIM_RADIUS.get(),
            STORAGE_PRIORITY_THRESHOLD.get(),
            WINDOW_CHECK_INTERVAL_TICKS.get(),
            TRAP_SCAN_INTERVAL_TICKS.get(),
            FACTION_TRADE_GAIN.get(),
            FACTION_KILL_LOSS.get()
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
        double raidThreshold,
        double fovDay,
        double fovNight,
        double hearingRange,
        double stealthMultiplier,
        int baseDelayTicks,
        int slotDistancePenalty,
        String junkList,
        float accuracySkill,
        double recoilCompensationFactor,
        double betrayalChance,
        float reputationGainSaveAlly,
        float reputationLossFriendlyFire,
        int squadMaxSize,
        int claimRadius,
        double storagePriorityThreshold,
        int windowCheckIntervalTicks,
        int trapScanIntervalTicks,
        double factionTradeGain,
        double factionKillLoss
    ) {
    }
}
