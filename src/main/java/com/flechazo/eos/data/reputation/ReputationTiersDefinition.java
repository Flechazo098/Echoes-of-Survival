package com.flechazo.eos.data.reputation;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

@DataDriven(
        folder = "survivor_reputation_tiers",
        syncToClient = true,
        validator = ReputationTiersDefinition.Validator.class
)
public record ReputationTiersDefinition(Map<ResourceLocation, Map<String, Tier>> values) {
    public static final Codec<ReputationTiersDefinition> CODEC = RecordCodecBuilder.create(
            (RecordCodecBuilder.Instance<ReputationTiersDefinition> instance) -> instance.group(
                    Codec.unboundedMap(
                                    ResourceLocation.CODEC,
                                    Codec.unboundedMap(Codec.STRING, Tier.CODEC)
                            )
                            .fieldOf("values")
                            .forGetter(ReputationTiersDefinition::values)
            ).apply(instance, ReputationTiersDefinition::new)
    );

    public record Tier(
            int min,
            int max,
            boolean hostileToPlayer,
            boolean canTradeFriendly,
            double priceMultiplier,
            boolean canRecruit
    ) {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("min").forGetter(Tier::min),
                Codec.INT.fieldOf("max").forGetter(Tier::max),
                Codec.BOOL.optionalFieldOf("hostile_to_player", false).forGetter(Tier::hostileToPlayer),
                Codec.BOOL.optionalFieldOf("can_trade_friendly", true).forGetter(Tier::canTradeFriendly),
                Codec.DOUBLE.optionalFieldOf("price_multiplier", 1.0).forGetter(Tier::priceMultiplier),
                Codec.BOOL.optionalFieldOf("can_recruit", false).forGetter(Tier::canRecruit)
        ).apply(instance, Tier::new));
    }

    public static final class Validator implements DataValidator<ReputationTiersDefinition> {
        @Override
        public ValidationResult validate(ReputationTiersDefinition data, ResourceLocation source) {
            if (data == null || data.values == null || data.values.isEmpty()) {
                return ValidationResult.failure("'values' must not be empty");
            }
            for (Map.Entry<ResourceLocation, Map<String, Tier>> entry : data.values.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    return ValidationResult.failure("tier map '" + entry.getKey() + "' must not be empty");
                }
            }
            return ValidationResult.success();
        }
    }
}
