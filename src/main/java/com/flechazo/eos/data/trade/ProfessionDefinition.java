package com.flechazo.eos.data.trade;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

@DataDriven(
        folder = "survivor_professions",
        syncToClient = true,
        validator = ProfessionDefinition.Validator.class
)
public record ProfessionDefinition(
        ResourceLocation id,
        Optional<SkinDefinition> skin,
        Optional<SkinDefinition> hostileSkin,
        Optional<SkinDefinition> neutralSkin,
        InitialEquipment initialEquipment,
        Logic logic
) {
    public static final Codec<ProfessionDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ProfessionDefinition::id),
            SkinDefinition.CODEC.optionalFieldOf("skin").forGetter(ProfessionDefinition::skin),
            SkinDefinition.CODEC.optionalFieldOf("hostile_skin").forGetter(ProfessionDefinition::hostileSkin),
            SkinDefinition.CODEC.optionalFieldOf("neutral_skin").forGetter(ProfessionDefinition::neutralSkin),
            InitialEquipment.CODEC.fieldOf("initial_equipment").forGetter(ProfessionDefinition::initialEquipment),
            Logic.CODEC.fieldOf("logic").forGetter(ProfessionDefinition::logic)
    ).apply(instance, ProfessionDefinition::new));

    public record SkinDefinition(
            ResourceLocation texture,
            Optional<String> model,
            Optional<ResourceLocation> cape,
            Optional<ResourceLocation> elytra
    ) {
        private static final Codec<SkinDefinition> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("texture").forGetter(SkinDefinition::texture),
                Codec.STRING.optionalFieldOf("model").forGetter(SkinDefinition::model),
                ResourceLocation.CODEC.optionalFieldOf("cape").forGetter(SkinDefinition::cape),
                ResourceLocation.CODEC.optionalFieldOf("elytra").forGetter(SkinDefinition::elytra)
        ).apply(instance, SkinDefinition::new));

        public static final Codec<SkinDefinition> CODEC = Codec.either(ResourceLocation.CODEC, OBJECT_CODEC)
                .xmap(
                        either -> either.map(SkinDefinition::wide, value -> value),
                        value -> value.isTextureOnlyWide() ? Either.left(value.texture()) : Either.right(value)
                );

        public static SkinDefinition wide(ResourceLocation texture) {
            return new SkinDefinition(texture, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public boolean slim() {
            return model()
                    .map(value -> value.equalsIgnoreCase("slim"))
                    .orElse(false);
        }

        private boolean isTextureOnlyWide() {
            return model().isEmpty() && cape().isEmpty() && elytra().isEmpty();
        }
    }

    public record InitialEquipment(
            Optional<ResourceLocation> armorSet,
            List<ItemStack> tacticalItems
    ) {
        public static final Codec<InitialEquipment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("armor_set").forGetter(InitialEquipment::armorSet),
                ItemStack.CODEC.listOf().optionalFieldOf("tactical_items", List.of()).forGetter(InitialEquipment::tacticalItems)
        ).apply(instance, InitialEquipment::new));
    }

    public record Logic(
            List<ResourceLocation> tradePools,
            List<ResourceLocation> questPools,
            int reputationOnDeath
    ) {
        public static final Codec<Logic> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("trade_pools", List.of()).forGetter(Logic::tradePools),
                ResourceLocation.CODEC.listOf().optionalFieldOf("quest_pools", List.of()).forGetter(Logic::questPools),
                Codec.INT.optionalFieldOf("reputation_on_death", 0).forGetter(Logic::reputationOnDeath)
        ).apply(instance, Logic::new));
    }

    public static final class Validator implements DataValidator<ProfessionDefinition> {
        @Override
        public ValidationResult validate(ProfessionDefinition data, ResourceLocation source) {
            if (data == null) return ValidationResult.failure("profession is null");
            if (data.id == null) return ValidationResult.failure("id is required");
            if (data.initialEquipment == null) return ValidationResult.failure("initial_equipment is required");
            if (data.logic == null) return ValidationResult.failure("logic is required");
            return ValidationResult.success();
        }
    }
}
