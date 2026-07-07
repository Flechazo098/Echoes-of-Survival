package com.flechazo.eos.reputation;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.hkt.Maybe;

import java.util.Map;

public final class ReputationTiers {
    private ReputationTiers() {
    }

    public static Maybe<Map.Entry<String, ReputationTiersDefinition.Tier>> tierForValue(int reputation) {
        Map.Entry<String, ReputationTiersDefinition.Tier> lowest = null;
        Map.Entry<String, ReputationTiersDefinition.Tier> highest = null;
        for (Map.Entry<String, ReputationTiersDefinition.Tier> entry : EosDatapackIndex.reputationTiers()) {
            ReputationTiersDefinition.Tier tier = entry.getValue();
            if (reputation >= tier.min() && reputation <= tier.max()) {
                return Maybe.some(entry);
            }
            if (lowest == null || tier.min() < lowest.getValue().min()) {
                lowest = entry;
            }
            if (highest == null || tier.max() > highest.getValue().max()) {
                highest = entry;
            }
        }
        if (highest != null && reputation > highest.getValue().max()) {
            return Maybe.some(highest);
        }
        if (lowest != null && reputation < lowest.getValue().min()) {
            return Maybe.some(lowest);
        }
        return Maybe.none();
    }

    public static double priceMultiplier(int reputation) {
        return tierForValue(reputation).map(e -> e.getValue().priceMultiplier()).orElse(1.0);
    }

    public static boolean canTradeFriendly(int reputation) {
        return tierForValue(reputation).map(e -> e.getValue().canTradeFriendly()).orElse(true);
    }

    public static boolean canRecruit(int reputation) {
        return tierForValue(reputation).map(e -> e.getValue().canRecruit()).orElse(false);
    }

    public static boolean isHostileToPlayer(int reputation) {
        return tierForValue(reputation).map(e -> e.getValue().hostileToPlayer()).orElse(false);
    }
}
