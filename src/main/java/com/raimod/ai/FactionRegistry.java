package com.raimod.ai;

import com.raimod.config.RAIServerConfig;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global faction registry used by squads.
 *
 * <p>Static rules:
 * <ul>
 *   <li>RAIDERS are hostile to everyone.</li>
 *   <li>STAYERS and SETTLERS are dynamically adjusted by trade/kill history.</li>
 * </ul>
 */
public final class FactionRegistry {
    public enum FactionId {
        STAYERS,
        RAIDERS,
        SETTLERS
    }

    private final Map<UUID, FactionId> squadFaction = new java.util.HashMap<>();
    private final EnumMap<FactionId, EnumMap<FactionId, Double>> relationMatrix = new EnumMap<>(FactionId.class);

    private double stayerSettlerRelation;
    private double tradeGain;
    private double killLoss;

    public FactionRegistry() {
        for (FactionId a : FactionId.values()) {
            EnumMap<FactionId, Double> row = new EnumMap<>(FactionId.class);
            for (FactionId b : FactionId.values()) {
                row.put(b, a == b ? 1.0 : 0.0);
            }
            relationMatrix.put(a, row);
        }

        relationMatrix.get(FactionId.RAIDERS).put(FactionId.STAYERS, -1.0);
        relationMatrix.get(FactionId.RAIDERS).put(FactionId.SETTLERS, -1.0);
        relationMatrix.get(FactionId.STAYERS).put(FactionId.RAIDERS, -1.0);
        relationMatrix.get(FactionId.SETTLERS).put(FactionId.RAIDERS, -1.0);

        stayerSettlerRelation = 0.25;
        tradeGain = 0.08;
        killLoss = 0.15;
        refreshDynamicRelation();
    }

    public void configure(RAIServerConfig.RuntimeValues runtime) {
        tradeGain = runtime.factionTradeGain();
        killLoss = runtime.factionKillLoss();
    }

    public void assignSquad(UUID squadId) {
        if (squadFaction.containsKey(squadId)) {
            return;
        }

        FactionId[] weighted = {
            FactionId.STAYERS,
            FactionId.STAYERS,
            FactionId.SETTLERS,
            FactionId.RAIDERS
        };
        FactionId selected = weighted[Math.floorMod(squadId.hashCode(), weighted.length)];
        squadFaction.put(squadId, selected);
    }

    public FactionId factionOf(UUID squadId) {
        return squadFaction.getOrDefault(squadId, FactionId.STAYERS);
    }

    public boolean areHostile(UUID squadA, UUID squadB) {
        FactionId a = factionOf(squadA);
        FactionId b = factionOf(squadB);
        return relationMatrix.get(a).getOrDefault(b, 0.0) < 0.0;
    }

    public void recordTrade(UUID squadA, UUID squadB) {
        FactionId a = factionOf(squadA);
        FactionId b = factionOf(squadB);
        if (isStayerSettlerPair(a, b)) {
            stayerSettlerRelation = clamp(stayerSettlerRelation + tradeGain);
            refreshDynamicRelation();
        }
    }

    public void recordKill(UUID killerSquad, UUID victimSquad) {
        FactionId a = factionOf(killerSquad);
        FactionId b = factionOf(victimSquad);
        if (isStayerSettlerPair(a, b)) {
            stayerSettlerRelation = clamp(stayerSettlerRelation - killLoss);
            refreshDynamicRelation();
        }
    }

    private boolean isStayerSettlerPair(FactionId a, FactionId b) {
        return (a == FactionId.STAYERS && b == FactionId.SETTLERS)
            || (a == FactionId.SETTLERS && b == FactionId.STAYERS);
    }

    private void refreshDynamicRelation() {
        relationMatrix.get(FactionId.STAYERS).put(FactionId.SETTLERS, stayerSettlerRelation);
        relationMatrix.get(FactionId.SETTLERS).put(FactionId.STAYERS, stayerSettlerRelation);
    }

    private double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
