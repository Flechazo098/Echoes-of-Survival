package com.flechazo.eos.data.armor;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.flechazo.hkt.Maybe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Map;

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


    public record ArmorSet(Map<EquipmentSlot, ResourceLocation> slots) {
        public static final Codec<ArmorSet> CODEC = Codec.unboundedMap(
                EquipmentSlot.CODEC,
                ResourceLocation.CODEC
        ).xmap(ArmorSet::new, ArmorSet::slots);
    }

    public static final class Validator implements DataValidator<ArmorSetDefinition> {
        @Override
        public ValidationResult validate(ArmorSetDefinition data, ResourceLocation source) {
            if (data == null || data.set == null || data.set.isEmpty()) {
                return ValidationResult.failure("armor set 'set' must not be empty");
            }
            for (Map.Entry<String, ArmorSet> entry : data.set().entrySet()) {
                ArmorSet armorSet = entry.getValue();
                boolean offHandGun = isGun(armorSet.slots().get(EquipmentSlot.OFFHAND));
                boolean mainHandGun = isGun(armorSet.slots().get(EquipmentSlot.MAINHAND));

                if (offHandGun && !mainHandGun) {
                    return ValidationResult.failure(
                            "armor set '" + entry.getKey()
                                    + "' has a gun in offhand, but guns can only be equipped in main hand"
                    );
                }
            }
            return ValidationResult.success();
        }
    }

    private static boolean isGun(ResourceLocation id) {
        return Maybe.ofNullable(id)
                .map(BuiltInRegistries.ITEM::get)
                .filter(item -> item instanceof GunItem)
                .isDefined();
    }
}
