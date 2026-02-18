package com.raimod.integration;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

public final class ModIntegrationRegistry {
    private final TaczBridge tacz = new TaczBridge();
    private final CorpseBridge corpse = new CorpseBridge();
    private final VoiceChatBridge voiceChat = new VoiceChatBridge();
    private final SecurityCraftBridge securityCraft = new SecurityCraftBridge();
    private final PhysicalStatsBridge physicalStats = new PhysicalStatsBridge();

    public void bootstrap(MinecraftServer server) {
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

    public static final class TaczBridge extends BaseBridge {
        public void prepareRaidLoadout(UUID survivorId, double threat) {
            if (!isEnabled()) {
                return;
            }
            // Hook point for TACZ weapon table crafting + ballistic profile preload.
        }
    }

    public static final class CorpseBridge extends BaseBridge {
        public boolean hasUnrecoveredCorpse(UUID survivorId) {
            return isEnabled();
        }

        public void recoverGradually(UUID survivorId, int maxItemsPerTick) {
            if (!isEnabled()) {
                return;
            }
            // Hook point: transfer corpse inventory with throttling.
        }
    }

    public static final class VoiceChatBridge extends BaseBridge {
        public void broadcastSquadPing(UUID survivorId, String messageId) {
            if (!isEnabled()) {
                return;
            }
            // Hook point: proximity speech / contextual ping.
        }
    }

    public static final class SecurityCraftBridge extends BaseBridge {
        public boolean hasRecentBreakAttempt(UUID survivorId) {
            return isEnabled() && Math.floorMod(System.nanoTime(), 17) == 0;
        }
    }

    public static final class PhysicalStatsBridge extends BaseBridge {
        public void train(UUID survivorId, String stat) {
            if (!isEnabled()) {
                return;
            }
            // Hook point: xp/stat bridge.
        }
    }
}
