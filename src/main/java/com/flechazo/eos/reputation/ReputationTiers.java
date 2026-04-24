package com.flechazo.eos.reputation;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;

import java.util.Map;
import java.util.Optional;

public final class ReputationTiers {
    private ReputationTiers() {
    }

    public static Optional<Map.Entry<String, ReputationTiersDefinition.Tier>> tierForValue(int reputation) {
        for (Map.Entry<String, ReputationTiersDefinition.Tier> entry : EosDatapackIndex.reputationTiers()) {
            ReputationTiersDefinition.Tier tier = entry.getValue();
            if (reputation >= tier.min() && reputation <= tier.max()) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static double priceMultiplier(int reputation) {
        return tierForValue(reputation).map(e -> e.getValue().priceMultiplier()).orElse(1.0);
    }

    public static boolean canTradeFriendly(int reputation) {
        return tierForValue(reputation).map(e -> e.getValue().canTradeFriendly()).orElse(true);
    }

    public static boolean isHostileToPlayer(int reputation) {
        return tierForValue(reputation).map(e -> e.getValue().hostileToPlayer()).orElse(false);
    }
}
