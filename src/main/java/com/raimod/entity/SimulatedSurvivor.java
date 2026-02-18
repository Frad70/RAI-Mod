package com.raimod.entity;

import com.mojang.authlib.GameProfile;
import com.raimod.ai.behavior.BehaviorEngine;
import com.raimod.ai.behavior.SurvivorContext;
import com.raimod.ai.memory.SurvivorMemory;
import com.raimod.config.RAIServerConfig;
import com.raimod.integration.ModIntegrationRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class SimulatedSurvivor extends FakePlayer {
    private final UUID survivorId;
    private final SurvivorMemory memory;
    private final BehaviorEngine behaviorEngine;
    private final float trustFactor;
    private final Map<UUID, Integer> recentDamageByEntityTick;

    private SurvivorState state;
    private ModIntegrationRegistry integrations;
    private RAIServerConfig.RuntimeValues runtime;

    private Vec3 lookTarget;
    private double strafeInput;
    private double forwardInput;
    private UUID currentCombatTarget;

    public SimulatedSurvivor(ServerLevel level, GameProfile profile, RAIServerConfig.RuntimeValues config) {
        super(level, profile);
        this.survivorId = profile.getId();
        this.memory = SurvivorMemory.createEmpty(profile.getId());
        this.behaviorEngine = new BehaviorEngine();
        this.state = SurvivorState.freshSpawn(config.minActiveChunks(), config.maxActiveChunks());
        this.runtime = config;
        this.lookTarget = this.getEyePosition().add(this.getLookAngle().scale(3.0));
        this.trustFactor = level.random.nextFloat();
        this.recentDamageByEntityTick = new HashMap<>();
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

    public float trustFactor() {
        return trustFactor;
    }

    public UUID currentCombatTarget() {
        return currentCombatTarget;
    }

    public void setCurrentCombatTarget(UUID targetId) {
        this.currentCombatTarget = targetId;
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

        Entity lastHurt = this.getLastHurtByMob();
        if (lastHurt != null) {
            recentDamageByEntityTick.put(lastHurt.getUUID(), this.tickCount);
        }

        state = state.withDynamicChunkBudget(this.server, runtime);

        integrations.applyChunkTickets(serverLevel(), this, state.loadedChunks());
        integrations.updateVisualDangerScan(serverLevel(), this);

        SurvivorContext context = new SurvivorContext(this.server, this, integrations, runtime);
        behaviorEngine.tick(context);

        updateRotationFromLookTarget();
        applyManualMovement();
    }

    public void resetRuntime(RAIServerConfig.RuntimeValues config) {
        this.state = state.withResetBudgets(config.minActiveChunks(), config.maxActiveChunks());
        this.runtime = config;
    }

    public void aimAt(Vec3 targetEyePos) {
        this.lookTarget = targetEyePos;
    }

    public void setMovementInput(double strafe, double forward) {
        this.strafeInput = strafe;
        this.forwardInput = forward;
    }

    public void clearMovementInput() {
        this.strafeInput = 0.0;
        this.forwardInput = 0.0;
    }

    public boolean isEntityVisible(Entity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (!this.canSee(target) || !hasVisualLineOfSight(target)) {
            return false;
        }

        if (wasRecentlyDamagedBy(target, 100)) {
            return true;
        }

        Vec3 look = this.getLookAngle().normalize();
        Vec3 toTarget = target.getEyePosition().subtract(this.getEyePosition()).normalize();
        double dot = Mth.clamp(look.dot(toTarget), -1.0, 1.0);
        double angle = Math.toDegrees(Math.acos(dot));

        return angle <= 60.0;
    }

    public boolean hasVisualLineOfSight(Entity target) {
        Vec3 from = this.getEyePosition();
        Vec3 to = target.getEyePosition();
        HitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    public boolean wasRecentlyDamagedBy(Entity target, int ticks) {
        Integer tick = recentDamageByEntityTick.get(target.getUUID());
        return tick != null && (this.tickCount - tick) <= ticks;
    }

    private void updateRotationFromLookTarget() {
        Vec3 eye = this.getEyePosition();
        Vec3 delta = lookTarget.subtract(eye);
        if (delta.lengthSqr() < 0.0001) {
            return;
        }

        double xz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(delta.y, xz)));

        float yaw = lerpAngle(this.getYRot(), targetYaw, 5.0f);
        float pitch = lerp(this.getXRot(), targetPitch, 5.0f);

        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
    }

    private void applyManualMovement() {
        if (Math.abs(strafeInput) < 0.001 && Math.abs(forwardInput) < 0.001) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.55));
            return;
        }

        Vec3 look = this.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);

        Vec3 motion = forward.scale(forwardInput).add(right.scale(strafeInput));
        if (motion.lengthSqr() > 1.0) {
            motion = motion.normalize();
        }

        double speed = this.isSprinting() ? 0.23 : 0.17;
        Vec3 next = this.getDeltaMovement().scale(0.35).add(motion.scale(speed));
        this.setDeltaMovement(next.x, this.getDeltaMovement().y, next.z);
    }

    private float lerp(float current, float target, float maxStep) {
        float delta = target - current;
        if (delta > maxStep) {
            delta = maxStep;
        }
        if (delta < -maxStep) {
            delta = -maxStep;
        }
        return current + delta;
    }

    private float lerpAngle(float current, float target, float maxStep) {
        float delta = Mth.wrapDegrees(target - current);
        if (delta > maxStep) {
            delta = maxStep;
        }
        if (delta < -maxStep) {
            delta = -maxStep;
        }
        return current + delta;
    }
}
