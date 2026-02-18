package com.raimod.entity;

import com.mojang.authlib.GameProfile;
import com.raimod.ai.behavior.BehaviorEngine;
import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.ai.memory.SurvivorMemory;
import com.raimod.config.RAIServerConfig;
import com.raimod.integration.ModIntegrationRegistry;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public final class SimulatedSurvivor {
    private final UUID id;
    private final SurvivorMemory memory;
    private final BehaviorEngine behaviorEngine;
    private final FakePlayer fakePlayer;
    private final SurvivorBody body;
    private SurvivorState state;

    private SimulatedSurvivor(UUID id, SurvivorMemory memory, BehaviorEngine behaviorEngine, FakePlayer fakePlayer,
                              SurvivorBody body, SurvivorState state) {
        this.id = id;
        this.memory = memory;
        this.behaviorEngine = behaviorEngine;
        this.fakePlayer = fakePlayer;
        this.body = body;
        this.state = state;
    }

    public static SimulatedSurvivor bootstrap(UUID id, RAIServerConfig.RuntimeValues config, ServerLevel level) {
        SurvivorMemory memory = SurvivorMemory.createEmpty(id);
        SurvivorState state = SurvivorState.freshSpawn(config.minActiveChunks(), config.maxActiveChunks());
        GameProfile profile = new GameProfile(id, "rai_" + id.toString().substring(0, 8));
        FakePlayer fakePlayer = FakePlayerFactory.get(level, profile);
        SurvivorBody body = new SurvivorBody(level, fakePlayer.blockPosition());
        return new SimulatedSurvivor(id, memory, new BehaviorEngine(), fakePlayer, body, state);
    }

    public UUID id() {
        return id;
    }

    public SurvivorMemory memory() {
        return memory;
    }

    public SurvivorState state() {
        return state;
    }

    public FakePlayer fakePlayer() {
        return fakePlayer;
    }

    public SurvivorBody body() {
        return body;
    }

    public void tick(MinecraftServer server, ModIntegrationRegistry integrations, RAIServerConfig.RuntimeValues config) {
        state = state.withDynamicChunkBudget(config, server);

        if (fakePlayer.level() instanceof ServerLevel serverLevel) {
            body.syncFromFakePlayer(fakePlayer);
            integrations.applyChunkTickets(serverLevel, this, state.loadedChunks());
            integrations.updateVisualDangerScan(serverLevel, this);
        }

        SurvivorContext context = new SurvivorContext(server, this, integrations, config);
        behaviorEngine.tick(context);
    }

    public void resetRuntime(RAIServerConfig.RuntimeValues config) {
        this.state = state.withResetBudgets(config.minActiveChunks(), config.maxActiveChunks());
    }

    public void setState(SurvivorState newState) {
        this.state = Objects.requireNonNull(newState);
    }

    public static final class SurvivorBody extends PathfinderMob {
        private final SmoothLookControl smoothLookControl;
        private final BotJumpControl botJumpControl;

        public SurvivorBody(Level level, BlockPos startPos) {
            super(net.minecraft.world.entity.EntityType.ZOMBIE, level);
            this.setPos(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
            this.smoothLookControl = new SmoothLookControl(this);
            this.botJumpControl = new BotJumpControl(this);
            this.lookControl = smoothLookControl;
            this.jumpControl = botJumpControl;
            this.navigation = new GroundPathNavigation(this, level);
            this.setPathfindingMalus(PathType.WATER, 16.0F);
        }

        public void syncFromFakePlayer(Player fakePlayer) {
            this.setPos(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ());
            this.setYRot(fakePlayer.getYRot());
            this.setXRot(fakePlayer.getXRot());
        }

        public SmoothLookControl smoothLookControl() {
            return smoothLookControl;
        }

        public BotJumpControl botJumpControl() {
            return botJumpControl;
        }

        @Override
        public MobType getMobType() {
            return MobType.UNDEFINED;
        }

        @Override
        protected void registerGoals() {
        }
    }

    public static final class SmoothLookControl extends LookControl {
        public SmoothLookControl(PathfinderMob mob) {
            super(mob);
        }

        @Override
        protected float rotateTowards(float current, float target, float maxDelta) {
            return super.rotateTowards(current, target, Math.min(maxDelta, 4.0F));
        }
    }

    public static final class BotJumpControl extends JumpControl {
        public BotJumpControl(PathfinderMob mob) {
            super(mob);
        }
    }
}
