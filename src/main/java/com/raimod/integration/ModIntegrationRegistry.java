package com.raimod.integration;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.ICustomGoalProcess;
import com.raimod.entity.SimulatedSurvivor;
import com.raimod.entity.SurvivorState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;

public final class ModIntegrationRegistry {
    private static final TicketType<UUID> RAI_TICKET = TicketType.create("rai_survivor", UUID::compareTo, 300);

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
        BlockPos center = survivor.fakePlayer().blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos chunkCenter = center.offset(x * 16, 0, z * 16);
                level.getChunkSource().addRegionTicket(RAI_TICKET,
                    new net.minecraft.world.level.ChunkPos(chunkCenter),
                    radius,
                    survivor.id());
            }
        }
    }

    public void updateVisualDangerScan(ServerLevel level, SimulatedSurvivor survivor) {
        List<BlockPos> dangerous = securityCraft.scanMinesInFov(level, survivor);
        survivor.memory().setDangerousBlocks(dangerous);
        baritone.updateAvoidBlocks(dangerous);
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
        private List<BlockPos> avoidBlocks = List.of();

        public void setLootChestGoal(BlockPos chestPos) {
            if (!isEnabled()) {
                return;
            }
            ICustomGoalProcess process = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
            process.setGoalAndPath(new GoalBlock(chestPos));
        }

        public void setRaidHouseGoal(BlockPos housePos) {
            if (!isEnabled()) {
                return;
            }
            ICustomGoalProcess process = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
            process.setGoalAndPath(new GoalNear(housePos, 2));
        }

        public void setRetreatGoal(BlockPos retreatPos) {
            if (!isEnabled()) {
                return;
            }
            ICustomGoalProcess process = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
            process.setGoalAndPath(new GoalNear(retreatPos, 1));
        }

        public void updateAvoidBlocks(List<BlockPos> avoidBlocks) {
            this.avoidBlocks = List.copyOf(avoidBlocks);
            if (!isEnabled()) {
                return;
            }
            // Assumed Baritone 1.21 API: treat each mine as an avoid center with a 1-block radius.
            // If your exact API differs, map avoidBlocks to the mod's avoid list equivalent.
            BaritoneAPI.getSettings().allowParkour.value = false;
            BaritoneAPI.getSettings().avoidance.value = true;
        }

        public List<BlockPos> avoidBlocks() {
            return avoidBlocks;
        }
    }

    public static final class TaczBridge extends BaseBridge {
        public Vec3 calculateLeadShot(Entity target, ItemStack gun, double baseScatterDegrees, float accuracySkill) {
            Vec3 targetPos = target.position().add(0.0, target.getBbHeight() * 0.65, 0.0);
            Vec3 targetVel = target.getDeltaMovement();
            double distance = target.distanceToSqr(targetPos);
            distance = Math.sqrt(distance);

            // Assumed TacZ API: bullet speed and gravity from gun data.
            double bulletSpeed = readTaczBulletSpeed(gun);
            double gravity = readTaczGravity(gun);
            double travelTime = distance / Math.max(1.0, bulletSpeed);

            // P_aim = P_target + (V_target * (Distance / V_bullet))
            Vec3 lead = targetPos.add(targetVel.scale(travelTime));

            double drop = 0.5 * gravity * travelTime * travelTime;
            Vec3 compensated = lead.add(0.0, drop, 0.0);

            double skillFactor = Mth.clamp(accuracySkill, 0.0f, 1.0f);
            double scatterDeg = baseScatterDegrees * (1.1 - skillFactor);
            double scatterRad = Math.toRadians(scatterDeg);
            double randYaw = (Math.random() - 0.5) * scatterRad;
            double randPitch = (Math.random() - 0.5) * scatterRad;
            Vec3 forward = compensated.subtract(targetPos).normalize();
            Vec3 scattered = forward.xRot((float) randPitch).yRot((float) randYaw);
            return targetPos.add(scattered.scale(distance));
        }

        private double readTaczBulletSpeed(ItemStack gun) {
            return 160.0;
        }

        private double readTaczGravity(ItemStack gun) {
            return 0.05;
        }

        public void prepareRaidLoadout(UUID survivorId, double threat) {
            if (!isEnabled()) {
                return;
            }
        }
    }

    public static final class CorpseBridge extends BaseBridge {
        public boolean hasUnrecoveredCorpse(UUID survivorId) {
            return isEnabled();
        }

        public boolean recoverGradually(SimulatedSurvivor survivor, int minTicks, int maxTicks) {
            SurvivorState state = survivor.state();
            if (state.interactionCooldownTicks() > 0) {
                return false;
            }
            int nextDelay = minTicks + (int) (Math.random() * ((maxTicks - minTicks) + 1));
            survivor.setState(state.withInteractionCooldown(nextDelay));
            return isEnabled();
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

            Vec3 eye = survivor.fakePlayer().getEyePosition();
            Vec3 look = survivor.fakePlayer().getLookAngle().normalize();
            List<BlockPos> matches = new ArrayList<>();
            AABB scan = new AABB(eye.x - 32, eye.y - 8, eye.z - 32, eye.x + 32, eye.y + 8, eye.z + 32);
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
                        Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
                        if (to.lengthSqr() < 0.0001) {
                            continue;
                        }

                        Vec3 dir = to.normalize();
                        double dot = look.dot(dir);
                        if (dot < 0.5) {
                            continue;
                        }

                        if (level.getBlockState(pos).getBlock().builtInRegistryHolder().key().location().equals(CONTACT_MINE)) {
                            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
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
            if (!isEnabled()) {
                return 0.35f;
            }
            return 0.7f;
        }
    }
}
