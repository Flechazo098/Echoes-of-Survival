package com.flechazo.eos.data.trade;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.util.OptionalOps;
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
        Optional<ResourceLocation> skin,
        Optional<ResourceLocation> hostileSkin,
        Optional<ResourceLocation> neutralSkin,
        InitialEquipment initialEquipment,
        Logic logic
) {
    public static final Codec<ProfessionDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ProfessionDefinition::id),
            ResourceLocation.CODEC.optionalFieldOf("skin").forGetter(ProfessionDefinition::skin),
            ResourceLocation.CODEC.optionalFieldOf("hostile_skin").forGetter(ProfessionDefinition::hostileSkin),
            ResourceLocation.CODEC.optionalFieldOf("neutral_skin").forGetter(ProfessionDefinition::neutralSkin),
            InitialEquipment.CODEC.fieldOf("initial_equipment").forGetter(ProfessionDefinition::initialEquipment),
            Logic.CODEC.fieldOf("logic").forGetter(ProfessionDefinition::logic)
    ).apply(instance, ProfessionDefinition::new));

    public Maybe<ResourceLocation> skinLibrary() {
        return OptionalOps.toMaybe(skin);
    }

    public Maybe<ResourceLocation> hostileSkinLibrary() {
        return OptionalOps.toMaybe(hostileSkin);
    }

    public Maybe<ResourceLocation> neutralSkinLibrary() {
        return OptionalOps.toMaybe(neutralSkin);
    }

    public record InitialEquipment(
            Optional<ResourceLocation> armorSet,
            List<ItemStack> tacticalItems
    ) {
        public static final Codec<InitialEquipment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("armor_set").forGetter(InitialEquipment::armorSet),
                ItemStack.CODEC.listOf().optionalFieldOf("tactical_items", List.of()).forGetter(InitialEquipment::tacticalItems)
        ).apply(instance, InitialEquipment::new));

        public Maybe<ResourceLocation> armorSetId() {
            return OptionalOps.toMaybe(armorSet);
        }
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
