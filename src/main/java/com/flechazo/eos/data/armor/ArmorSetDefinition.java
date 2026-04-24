package com.flechazo.eos.data.armor;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@DataDriven(
        folder = "survivor_armor_sets",
        syncToClient = true,
        validator = ArmorSetDefinition.Validator.class
)
public record ArmorSetDefinition(Map<String, ArmorSet> set) {
    public static final Codec<ArmorSetDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, ArmorSet.CODEC)
                    .fieldOf("set")
                    .forGetter(ArmorSetDefinition::set)
    ).apply(instance, ArmorSetDefinition::new));

    public record ArmorSet(
            Optional<ResourceLocation> head,
            Optional<ResourceLocation> chest,
            Optional<ResourceLocation> legs,
            Optional<ResourceLocation> feet
    ) {
        public static final Codec<ArmorSet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("head").forGetter(ArmorSet::head),
                ResourceLocation.CODEC.optionalFieldOf("chest").forGetter(ArmorSet::chest),
                ResourceLocation.CODEC.optionalFieldOf("legs").forGetter(ArmorSet::legs),
                ResourceLocation.CODEC.optionalFieldOf("feet").forGetter(ArmorSet::feet)
        ).apply(instance, ArmorSet::new));
    }

    public static final class Validator implements DataValidator<ArmorSetDefinition> {
        @Override
        public ValidationResult validate(ArmorSetDefinition data, ResourceLocation source) {
            if (data == null || data.set == null || data.set.isEmpty()) {
                return ValidationResult.failure("armor set 'set' must not be empty");
            }
            return ValidationResult.success();
        }
    }
}
