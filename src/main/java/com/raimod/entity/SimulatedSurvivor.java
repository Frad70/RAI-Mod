package com.raimod.entity;

import com.mojang.authlib.GameProfile;
import com.raimod.ai.behavior.BehaviorEngine;
import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.ai.memory.SurvivorMemory;
import com.raimod.config.RAIServerConfig;
import com.raimod.integration.ModIntegrationRegistry;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class SimulatedSurvivor extends FakePlayer {
    private final UUID survivorId;
    private final SurvivorMemory memory;
    private final BehaviorEngine behaviorEngine;
    private final ControlDelegate controlDelegate;
    private SurvivorState state;
    private ModIntegrationRegistry integrations;
    private RAIServerConfig.RuntimeValues runtime;

    public SimulatedSurvivor(ServerLevel level, GameProfile profile, RAIServerConfig.RuntimeValues config) {
        super(level, profile);
        this.survivorId = profile.getId();
        this.memory = SurvivorMemory.createEmpty(profile.getId());
        this.behaviorEngine = new BehaviorEngine();
        this.state = SurvivorState.freshSpawn(config.minActiveChunks(), config.maxActiveChunks());
        this.runtime = config;
        this.controlDelegate = new ControlDelegate(level, this);
    }

    public static SimulatedSurvivor bootstrap(UUID id, RAIServerConfig.RuntimeValues config, ServerLevel level) {
        GameProfile profile = new GameProfile(id, "rai_" + id.toString().substring(0, 8));
        return new SimulatedSurvivor(level, profile, config);
    }

    public UUID id() {
        return survivorId;
    }

    public SurvivorMemory memory() {
        return memory;
    }

    public SurvivorState state() {
        return state;
    }

    public void setState(SurvivorState newState) {
        this.state = Objects.requireNonNull(newState);
    }

    public void configureRuntime(ModIntegrationRegistry integrations, RAIServerConfig.RuntimeValues runtime) {
        this.integrations = integrations;
        this.runtime = runtime;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.server == null || integrations == null || runtime == null) {
            return;
        }

        state = state.withDynamicChunkBudget(this.server, runtime);
        controlDelegate.syncWithPlayer(this);

        integrations.applyChunkTickets(serverLevel(), this, state.loadedChunks());
        integrations.updateVisualDangerScan(serverLevel(), this);

        SurvivorContext context = new SurvivorContext(this.server, this, integrations, runtime);
        behaviorEngine.tick(context);
    }

    public void resetRuntime(RAIServerConfig.RuntimeValues config) {
        this.state = state.withResetBudgets(config.minActiveChunks(), config.maxActiveChunks());
        this.runtime = config;
    }

    public LookControl getLookControl() {
        return controlDelegate.lookControl();
    }

    public GroundPathNavigation getNavigationDelegate() {
        return controlDelegate.navigation();
    }

    public JumpControl getJumpControlDelegate() {
        return controlDelegate.jumpControl();
    }

    private static final class ControlDelegate extends PathfinderMob {
        private final LookControl lookControl;
        private final JumpControl jumpControl;
        private final GroundPathNavigation navigation;

        private ControlDelegate(ServerLevel level, ServerPlayer anchor) {
            super(EntityType.ZOMBIE, level);
            this.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
            this.lookControl = new SmoothLookControl(this);
            this.jumpControl = new JumpControl(this);
            this.navigation = new GroundPathNavigation(this, level);
            this.setPathfindingMalus(PathType.WATER, 8.0F);
        }

        public void syncWithPlayer(ServerPlayer player) {
            this.setPos(player.getX(), player.getY(), player.getZ());
            this.setYRot(player.getYRot());
            this.setXRot(player.getXRot());
            this.lookControl.tick();
        }

        public LookControl lookControl() {
            return lookControl;
        }

        public JumpControl jumpControl() {
            return jumpControl;
        }

        public GroundPathNavigation navigation() {
            return navigation;
        }

        @Override
        protected void registerGoals() {
        }
    }

    private static final class SmoothLookControl extends LookControl {
        private SmoothLookControl(PathfinderMob mob) {
            super(mob);
        }

        @Override
        protected float rotateTowards(float current, float target, float maxDelta) {
            return super.rotateTowards(current, target, Math.min(maxDelta, 3.0F));
        }
    }
}
