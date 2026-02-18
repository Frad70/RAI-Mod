package com.raimod.integration;

import com.raimod.entity.SimulatedSurvivor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SurvivorChatBridge {
    private final EnumMap<PhraseCategory, List<WeightedPhrase>> phraseBook = new EnumMap<>(PhraseCategory.class);

    public SurvivorChatBridge() {
        registerDefaults();
    }

    public void sendGiftOffer(SimulatedSurvivor survivor, ServerPlayer target, BlockPos meetPos) {
        if (survivor.memory().relationOf(target.getUUID()) <= 0.4f) {
            return;
        }

        String body = pick(PhraseCategory.GREETING, survivor, target.getName().getString(), meetPos);
        broadcastNearby(survivor, withName(survivor, body), 28.0);
    }

    public void sendThreatWarning(SimulatedSurvivor survivor, String landmark) {
        String body = "Contact! Multiple targets near " + landmark + ". Stay alert.";
        broadcastNearby(survivor, withName(survivor, body), 48.0);
    }

    public void sendBetrayalLine(SimulatedSurvivor survivor) {
        String body = pick(PhraseCategory.TAUNT, survivor, "friend", survivor.blockPosition());
        broadcastNearby(survivor, withName(survivor, body), 28.0);
    }

    public void sendTaunt(SimulatedSurvivor survivor, String playerName) {
        String body = pick(PhraseCategory.TAUNT, survivor, playerName, survivor.blockPosition());
        broadcastNearby(survivor, withName(survivor, body), 40.0);
    }

    public void sendPanic(SimulatedSurvivor survivor, String playerName) {
        String body = pick(PhraseCategory.PANIC, survivor, playerName, survivor.blockPosition());
        broadcastNearby(survivor, withName(survivor, body), 40.0);
    }

    public void sendLootFound(SimulatedSurvivor survivor, String itemName) {
        String body = pick(PhraseCategory.LOOT_FOUND, survivor, itemName, survivor.blockPosition());
        broadcastNearby(survivor, withName(survivor, body), 40.0);
    }

    public boolean beginInvitationRitual(SimulatedSurvivor survivor, BlockPos buildingCenter, Direction side) {
        BlockPos waitPoint = buildingCenter.relative(side, 12);
        String msg = withName(survivor, "Come out to the " + side.getName() + " side, let's talk.");
        broadcastNearby(survivor, msg, 40.0);

        survivor.teleportTo(waitPoint.getX() + 0.5, waitPoint.getY(), waitPoint.getZ() + 0.5);
        return true;
    }

    public boolean isInvitationExpired(long startedAtGameTime, long nowGameTime) {
        return (nowGameTime - startedAtGameTime) >= 400;
    }

    private String pick(PhraseCategory category, SimulatedSurvivor survivor, String token, BlockPos pos) {
        List<WeightedPhrase> variants = phraseBook.get(category);
        if (variants == null || variants.isEmpty()) {
            return "...";
        }

        int total = 0;
        for (WeightedPhrase phrase : variants) {
            total += phrase.weight;
        }

        int roll = survivor.level().random.nextInt(total);
        int cursor = 0;
        for (WeightedPhrase phrase : variants) {
            cursor += phrase.weight;
            if (roll < cursor) {
                return phrase.template
                    .replace("{name}", token)
                    .replace("{x}", Integer.toString(pos.getX()))
                    .replace("{y}", Integer.toString(pos.getY()))
                    .replace("{z}", Integer.toString(pos.getZ()));
            }
        }

        return variants.get(0).template;
    }

    private String withName(SimulatedSurvivor survivor, String body) {
        return "[" + survivor.getGameProfile().getName() + "]: " + body;
    }

    private void broadcastNearby(SimulatedSurvivor survivor, String msg, double radius) {
        List<ServerPlayer> players = survivor.serverLevel().getPlayers(player -> player.distanceTo(survivor) <= radius);
        Component line = Component.literal(msg);
        for (ServerPlayer player : players) {
            player.sendSystemMessage(line);
        }
    }

    private void registerDefaults() {
        phraseBook.put(PhraseCategory.GREETING, new ArrayList<>(List.of(
            new WeightedPhrase("Hey {name}, I've got something for you. Meet me at [{x}, {y}, {z}]!", 5),
            new WeightedPhrase("Drop your weapon and come out, I want to trade.", 3),
            new WeightedPhrase("Stay cool, {name}. Let's make a deal near [{x}, {y}, {z}].", 2)
        )));

        phraseBook.put(PhraseCategory.TAUNT, new ArrayList<>(List.of(
            new WeightedPhrase("Yo {name}, back off!", 4),
            new WeightedPhrase("Nice gear, I'll take it.", 4),
            new WeightedPhrase("Sorry, friend. I need that loot more than you.", 2)
        )));

        phraseBook.put(PhraseCategory.PANIC, new ArrayList<>(List.of(
            new WeightedPhrase("I'm dry! Need ammo now!", 4),
            new WeightedPhrase("No meds, no bullets, this is bad!", 3),
            new WeightedPhrase("Contact! Multiple targets! Falling back!", 3)
        )));

        phraseBook.put(PhraseCategory.LOOT_FOUND, new ArrayList<>(List.of(
            new WeightedPhrase("Found {name}. Jackpot.", 4),
            new WeightedPhrase("This {name} is mine.", 3),
            new WeightedPhrase("Loot secured: {name}.", 3)
        )));
    }

    private enum PhraseCategory {
        GREETING,
        TAUNT,
        PANIC,
        LOOT_FOUND
    }

    private record WeightedPhrase(String template, int weight) {
    }
}
