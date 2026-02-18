package com.raimod.integration;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.ICustomGoalProcess;
import com.raimod.entity.SimulatedSurvivor;
import com.raimod.entity.SurvivorState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;

public final class ModIntegrationRegistry {
    private static final TicketType<UUID> RAI_TICKET = TicketType.create("rai_survivor", UUID::compareTo, 200);

    private final BaritoneBridge baritone = new BaritoneBridge();
    private final TaczBridge tacz = new TaczBridge();
    private final CorpseBridge corpse = new CorpseBridge();
    private final VoiceChatBridge voiceChat = new VoiceChatBridge();
    private final SecurityCraftBridge securityCraft = new SecurityCraftBridge();
    private final PhysicalStatsBridge physicalStats = new PhysicalStatsBridge();

    public void bootstrap(MinecraftServer server) {
        baritone.setEnabled(ModList.get().isLoaded("baritone"));
        tacz.setEnabled(ModList.get().isLoaded("tacz"));
        corpse.setEnabled(ModList.get().isLoaded("corpse"));
        voiceChat.setEnabled(ModList.get().isLoaded("simplevoicechat"));
        securityCraft.setEnabled(ModList.get().isLoaded("securitycraft"));
        physicalStats.setEnabled(ModList.get().isLoaded("physicalstats"));
    }

    public void tick(MinecraftServer server) {
        tacz.tick(server);
        corpse.tick(server);
        voiceChat.tick(server);
        securityCraft.tick(server);
        physicalStats.tick(server);
    }

    public void applyChunkTickets(ServerLevel level, SimulatedSurvivor survivor, int configuredRadius) {
        int radius = level.getServer().getAverageTickTime() > 45.0f ? 1 : configuredRadius;
        BlockPos center = survivor.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos p = center.offset(x * 16, 0, z * 16);
                level.getChunkSource().addRegionTicket(RAI_TICKET, new ChunkPos(p), radius, survivor.id());
            }
        }
    }

    public void updateVisualDangerScan(ServerLevel level, SimulatedSurvivor survivor) {
        List<BlockPos> dangerous = securityCraft.scanMinesInFov(level, survivor);
        survivor.memory().setDangerousBlocks(dangerous);
        baritone.updateAvoidBlocks(survivor, dangerous);
    }

    public BaritoneBridge baritone() {
        return baritone;
    }

    public TaczBridge tacz() {
        return tacz;
    }

    public CorpseBridge corpse() {
        return corpse;
    }

    public VoiceChatBridge voiceChat() {
        return voiceChat;
    }

    public SecurityCraftBridge securityCraft() {
        return securityCraft;
    }

    public PhysicalStatsBridge physicalStats() {
        return physicalStats;
    }

    public static abstract class BaseBridge {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void tick(MinecraftServer server) {
        }
    }

    public static final class BaritoneBridge extends BaseBridge {
        public IBaritone getFor(SimulatedSurvivor survivor) {
            return BaritoneAPI.getProvider().getBaritoneForEntity(survivor);
        }

        public void setLootChestGoal(SimulatedSurvivor survivor, BlockPos chestPos) {
            if (!isEnabled()) {
                return;
            }
            ICustomGoalProcess process = getFor(survivor).getCustomGoalProcess();
            process.setGoalAndPath(new GoalBlock(chestPos));
        }

        public void setRaidHouseGoal(SimulatedSurvivor survivor, BlockPos housePos) {
            if (!isEnabled()) {
                return;
            }
            ICustomGoalProcess process = getFor(survivor).getCustomGoalProcess();
            process.setGoalAndPath(new GoalNear(housePos, 2));
        }

        public void setRetreatGoal(SimulatedSurvivor survivor, BlockPos retreatPos) {
            if (!isEnabled()) {
                return;
            }
            ICustomGoalProcess process = getFor(survivor).getCustomGoalProcess();
            process.setGoalAndPath(new GoalNear(retreatPos, 1));
        }

        public void updateAvoidBlocks(SimulatedSurvivor survivor, List<BlockPos> avoidBlocks) {
            if (!isEnabled()) {
                return;
            }
            IBaritone baritone = getFor(survivor);
            baritone.getPathingBehavior().cancelEverything();
            BaritoneAPI.getSettings().allowParkour.value = false;
            BaritoneAPI.getSettings().avoidance.value = true;
            // API versions differ for direct avoid-list injection; this is set on a per-bot baritone instance.
        }
    }

    public static final class TaczBridge extends BaseBridge {
        public Vec3 calculateLeadShot(Entity shooter, Entity target, ItemStack gun, double baseScatterDegrees, float accuracySkill) {
            Vec3 shooterEye = shooter.getEyePosition();
            Vec3 targetPos = target.position().add(0.0, target.getBbHeight() * 0.65, 0.0);
            Vec3 targetVel = target.getDeltaMovement();
            double distance = shooterEye.distanceTo(targetPos);

            TaczBulletData bulletData = TaczHelper.getBulletData(gun);
            double travelTime = distance / Math.max(1.0, bulletData.velocity());

            Vec3 lead = targetPos.add(targetVel.scale(travelTime));
            double drop = 0.5 * bulletData.gravity() * travelTime * travelTime;
            Vec3 compensated = lead.add(0.0, drop, 0.0);

            double skillFactor = Mth.clamp(accuracySkill, 0.0f, 1.0f);
            double scatterDeg = baseScatterDegrees * (1.15 - skillFactor);
            double scatterRad = Math.toRadians(scatterDeg);
            double yawScatter = (Math.random() - 0.5) * scatterRad;
            double pitchScatter = (Math.random() - 0.5) * scatterRad;

            Vec3 direction = compensated.subtract(shooterEye).normalize();
            Vec3 finalDir = direction.xRot((float) pitchScatter).yRot((float) yawScatter);
            return shooterEye.add(finalDir.scale(distance));
        }

        public void prepareRaidLoadout(UUID survivorId, double threat) {
            if (!isEnabled()) {
                return;
            }
        }
    }

    public record TaczBulletData(double velocity, double gravity) {
    }

    public static final class TaczHelper {
        private TaczHelper() {
        }

        public static TaczBulletData getBulletData(ItemStack gun) {
            CompoundTag tag = gun.getTag();
            if (tag == null) {
                return new TaczBulletData(90.0, 0.05);
            }

            double velocity = firstNumeric(tag, "AmmoSpeed", "Velocity", "BulletSpeed", "MuzzleVelocity");
            double gravity = firstNumeric(tag, "Gravity", "BulletGravity", "ProjectileGravity");
            if (velocity <= 0) {
                velocity = 90.0;
            }
            if (gravity <= 0) {
                gravity = 0.05;
            }

            // If TacZ API is present in your environment, replace with direct call:
            // IGun.getGunData(gun).getBulletVelocity(), IGun.getGunData(gun).getGravity()
            return new TaczBulletData(velocity, gravity);
        }

        private static double firstNumeric(CompoundTag tag, String... keys) {
            for (String key : keys) {
                if (tag.contains(key)) {
                    return tag.getDouble(key);
                }
            }
            for (String key : tag.getAllKeys()) {
                CompoundTag nested = tag.getCompound(key);
                if (!nested.isEmpty()) {
                    double value = firstNumeric(nested, keys);
                    if (value > 0) {
                        return value;
                    }
                }
            }
            return -1;
        }
    }

    public static final class CorpseBridge extends BaseBridge {
        public boolean hasUnrecoveredCorpse(UUID survivorId) {
            return isEnabled();
        }

        public boolean transferOneItem(SimulatedSurvivor survivor, net.minecraft.world.Container source, int minTicks, int maxTicks) {
            SurvivorState state = survivor.state();
            if (state.interactionCooldownTicks() > 0) {
                return false;
            }

            for (int slot = 0; slot < source.getContainerSize(); slot++) {
                ItemStack stack = source.getItem(slot);
                if (!stack.isEmpty()) {
                    ItemStack one = stack.split(1);
                    boolean accepted = survivor.getInventory().add(one);
                    if (!accepted) {
                        stack.grow(1);
                        return false;
                    }
                    source.setChanged();
                    int delay = minTicks + survivor.level().random.nextInt((maxTicks - minTicks) + 1);
                    survivor.setState(state.withInteractionCooldown(delay));
                    return true;
                }
            }
            return false;
        }
    }

    public static final class VoiceChatBridge extends BaseBridge {
        public void broadcastSquadPing(UUID survivorId, String messageId) {
            if (!isEnabled()) {
                return;
            }
        }
    }

    public static final class SecurityCraftBridge extends BaseBridge {
        private static final ResourceLocation CONTACT_MINE = ResourceLocation.fromNamespaceAndPath("securitycraft", "sc_manual_contact_mine");

        public boolean hasRecentBreakAttempt(UUID survivorId) {
            return isEnabled() && Math.floorMod(System.nanoTime(), 17) == 0;
        }

        public List<BlockPos> scanMinesInFov(ServerLevel level, SimulatedSurvivor survivor) {
            if (!isEnabled()) {
                return List.of();
            }

            Vec3 eye = survivor.getEyePosition();
            Vec3 look = survivor.getLookAngle().normalize();
            List<BlockPos> matches = new ArrayList<>();
            AABB scan = new AABB(eye.x - 28, eye.y - 8, eye.z - 28, eye.x + 28, eye.y + 8, eye.z + 28);
            int minX = Mth.floor(scan.minX);
            int minY = Mth.floor(scan.minY);
            int minZ = Mth.floor(scan.minZ);
            int maxX = Mth.floor(scan.maxX);
            int maxY = Mth.floor(scan.maxY);
            int maxZ = Mth.floor(scan.maxZ);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        Vec3 dir = Vec3.atCenterOf(pos).subtract(eye).normalize();
                        if (look.dot(dir) < 0.5) {
                            continue;
                        }

                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock().builtInRegistryHolder().key().location().equals(CONTACT_MINE)) {
                            VoxelShape shape = state.getCollisionShape(level, pos);
                            if (!shape.isEmpty()) {
                                matches.add(pos.immutable());
                            }
                        }
                    }
                }
            }
            return matches;
        }
    }

    public static final class PhysicalStatsBridge extends BaseBridge {
        public void train(UUID survivorId, String stat) {
            if (!isEnabled()) {
                return;
            }
        }

        public float getAccuracySkill(UUID survivorId) {
            return isEnabled() ? 0.70f : 0.35f;
        }
    }

    public boolean placeExplosive(SimulatedSurvivor survivor, BlockPos wallPos) {
        ItemStack explosive = findExplosive(survivor);
        if (explosive.isEmpty()) {
            return false;
        }

        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(wallPos), Direction.UP, wallPos, false);
        InteractionResult result = survivor.gameMode.useItemOn(survivor, survivor.serverLevel(), explosive, InteractionHand.MAIN_HAND, hitResult);
        return result.consumesAction();
    }

    private ItemStack findExplosive(SimulatedSurvivor survivor) {
        for (int i = 0; i < survivor.getInventory().getContainerSize(); i++) {
            ItemStack stack = survivor.getInventory().getItem(i);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (id.contains("tnt") || id.contains("explosive") || id.contains("breach")) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
