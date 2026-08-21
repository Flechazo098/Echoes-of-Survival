package com.flechazo.eos.data.trade;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.util.OptionalOps;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Locale;

@DataDriven(
        folder = "survivor_trade_pools",
        syncToClient = true,
        validator = TradePoolDefinition.Validator.class
)
public record TradePoolDefinition(
        ResourceLocation profession,
        List<Trade> trades
) {
    public static final Codec<TradePoolDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("profession").forGetter(TradePoolDefinition::profession),
            Trade.CODEC.listOf().fieldOf("trades").forGetter(TradePoolDefinition::trades)
    ).apply(instance, TradePoolDefinition::new));

    public record Trade(
            ItemStack buy,
            ItemStack sell,
            int reputation,
            int maxUses,
            int reputationRequirement,
            Optional<Either<Integer, String>> unlockCondition,
            TradeMode mode,
            int procurementBudgetCost
    ) {
        public static final Codec<Trade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("buy").forGetter(Trade::buy),
                ItemStack.CODEC.fieldOf("sell").forGetter(Trade::sell),
                Codec.INT.optionalFieldOf("reputation", 0).forGetter(Trade::reputation),
                Codec.INT.optionalFieldOf("max_uses", 1).forGetter(Trade::maxUses),
                Codec.INT.optionalFieldOf("reputation_requirement", 0).forGetter(Trade::reputationRequirement),
                Codec.either(Codec.INT, Codec.STRING).optionalFieldOf("unlock_condition").forGetter(Trade::unlockCondition),
                TradeMode.CODEC.optionalFieldOf("mode", TradeMode.SELL_TO_PLAYER).forGetter(Trade::mode),
                Codec.INT.optionalFieldOf("procurement_budget_cost", 1).forGetter(Trade::procurementBudgetCost)
        ).apply(instance, Trade::new));

        public Maybe<Either<Integer, String>> unlockRequirement() {
            return OptionalOps.toMaybe(unlockCondition);
        }
    }

    public enum TradeMode {
        SELL_TO_PLAYER,
        PROCURE_FROM_PLAYER;

        public static final Codec<TradeMode> CODEC = Codec.STRING.xmap(
                value -> TradeMode.valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT)
        );
    }

    public static final class Validator implements DataValidator<TradePoolDefinition> {
        @Override
        public ValidationResult validate(TradePoolDefinition data, ResourceLocation source) {
            if (data == null) return ValidationResult.failure("trade pool is null");
            if (data.profession == null) return ValidationResult.failure("profession is required");
            if (data.trades == null || data.trades.isEmpty())
                return ValidationResult.failure("trades must not be empty");
            for (Trade trade : data.trades) {
                if (trade == null) return ValidationResult.failure("trade entry is null");
                if (trade.buy == null || trade.buy.isEmpty() || trade.sell == null || trade.sell.isEmpty())
                    return ValidationResult.failure("trade buy/sell is required");
                if (trade.maxUses <= 0) return ValidationResult.failure("trade max_uses must be > 0");
                if (trade.procurementBudgetCost < 0) {
                    return ValidationResult.failure("trade procurement_budget_cost must be >= 0");
                }
            }
            return ValidationResult.success();
        }
    }
}
