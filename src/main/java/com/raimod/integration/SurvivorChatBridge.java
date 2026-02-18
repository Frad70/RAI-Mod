package com.raimod.integration;

import com.raimod.entity.SimulatedSurvivor;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SurvivorChatBridge {
    public void sendGiftOffer(SimulatedSurvivor survivor, ServerPlayer target, BlockPos meetPos) {
        if (survivor.memory().relationOf(target.getUUID()) <= 0.4f) {
            return;
        }

        String name = survivor.getGameProfile().getName();
        String msg = survivor.level().random.nextBoolean()
            ? "[%s]: Hey, I've got something for you. Meet me at [%d, %d, %d]!".formatted(name, meetPos.getX(), meetPos.getY(), meetPos.getZ())
            : "[%s]: Drop your weapon and come out, I want to trade.".formatted(name);

        broadcastNearby(survivor, msg, 28.0);
    }

    public void sendThreatWarning(SimulatedSurvivor survivor, String landmark) {
        String msg = "[%s]: I see a rat near the %s... stay alert.".formatted(survivor.getGameProfile().getName(), landmark);
        broadcastNearby(survivor, msg, 48.0);
    }

    public void sendBetrayalLine(SimulatedSurvivor survivor) {
        String msg = "[%s]: Sorry, friend. I need that loot more than you.".formatted(survivor.getGameProfile().getName());
        broadcastNearby(survivor, msg, 28.0);
    }

    public boolean beginInvitationRitual(SimulatedSurvivor survivor, BlockPos buildingCenter, Direction side) {
        BlockPos waitPoint = buildingCenter.relative(side, 12);
        String msg = "[%s]: Come out to the %s side, let's talk.".formatted(survivor.getGameProfile().getName(), side.getName());
        broadcastNearby(survivor, msg, 40.0);

        survivor.teleportTo(waitPoint.getX() + 0.5, waitPoint.getY(), waitPoint.getZ() + 0.5);
        return true;
    }

    public boolean isInvitationExpired(long startedAtGameTime, long nowGameTime) {
        return (nowGameTime - startedAtGameTime) >= 400;
    }

    private void broadcastNearby(SimulatedSurvivor survivor, String msg, double radius) {
        List<ServerPlayer> players = survivor.serverLevel().getPlayers(player -> player.distanceTo(survivor) <= radius);
        Component line = Component.literal(msg);
        for (ServerPlayer player : players) {
            player.sendSystemMessage(line);
        }
    }
}
