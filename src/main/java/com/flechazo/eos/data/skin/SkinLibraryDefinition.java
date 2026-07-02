package com.flechazo.eos.data.skin;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@DataDriven(
        folder = "survivor_skin_library",
        syncToClient = true,
        validator = SkinLibraryDefinition.Validator.class
)
public record SkinLibraryDefinition(
        ResourceLocation id,
        Map<UUID, String> skins
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(
            SkinLibraryDefinition::parseUuid,
            UUID::toString
    );

    public static final Codec<SkinLibraryDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(SkinLibraryDefinition::id),
            Codec.unboundedMap(UUID_CODEC, Codec.STRING).fieldOf("skins").forGetter(SkinLibraryDefinition::skins)
    ).apply(instance, SkinLibraryDefinition::new));

    public Optional<UUID> pick(UUID seed) {
        if (skins == null || skins.isEmpty() || seed == null) return Optional.empty();
        var uuids = skins.keySet().stream().sorted().toList();
        int idx = Math.floorMod(seed.hashCode(), uuids.size());
        return Optional.ofNullable(uuids.get(idx));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) throw new IllegalArgumentException("uuid is null");
        String s = raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("uuid is empty");
        if (s.length() == 32) {
            // undashed Mojang id
            s = (s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20))
                    .toLowerCase(Locale.ROOT);
        }
        return UUID.fromString(s);
    }

    public static final class Validator implements DataValidator<SkinLibraryDefinition> {
        @Override
        public ValidationResult validate(SkinLibraryDefinition data, ResourceLocation source) {
            if (data == null) return ValidationResult.failure("skin library is null");
            if (data.id == null) return ValidationResult.failure("id is required");
            if (data.skins == null || data.skins.isEmpty()) {
                return ValidationResult.failure("'skins' must not be empty");
            }
            for (Map.Entry<UUID, String> entry : data.skins.entrySet()) {
                if (entry.getKey() == null) return ValidationResult.failure("'skins' contains a null uuid");
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    return ValidationResult.failure("'skins' contains a blank username for " + entry.getKey());
                }
            }
            return ValidationResult.success();
        }
    }
}
