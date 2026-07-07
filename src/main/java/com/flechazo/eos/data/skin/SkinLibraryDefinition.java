package com.flechazo.eos.data.skin;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.flechazo.hkt.Maybe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@DataDriven(
        folder = "survivor_skin_library",
        syncToClient = true,
        validator = SkinLibraryDefinition.Validator.class
)
public record SkinLibraryDefinition(
        ResourceLocation id,
        List<SkinEntry> skins
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(
            SkinLibraryDefinition::parseUuid,
            UUID::toString
    );

    public static final Codec<SkinLibraryDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(SkinLibraryDefinition::id),
            SkinEntry.CODEC.listOf().fieldOf("skins").forGetter(SkinLibraryDefinition::skins)
    ).apply(instance, SkinLibraryDefinition::new));

    public Maybe<SkinEntry> pick(UUID seed) {
        if (skins == null || skins.isEmpty() || seed == null) return Maybe.none();
        int idx = Math.floorMod(seed.hashCode(), skins.size());
        return Maybe.ofNullable(skins.get(idx));
    }

    public record SkinEntry(
            String name,
            Optional<UUID> uuid,
            Optional<ResourceLocation> texture,
            Optional<String> model,
            Optional<ResourceLocation> cape,
            Optional<ResourceLocation> elytra
    ) {
        public static final Codec<SkinEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(SkinEntry::name),
                UUID_CODEC.optionalFieldOf("uuid").forGetter(SkinEntry::uuid),
                ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(SkinEntry::texture),
                Codec.STRING.optionalFieldOf("model").forGetter(SkinEntry::model),
                ResourceLocation.CODEC.optionalFieldOf("cape").forGetter(SkinEntry::cape),
                ResourceLocation.CODEC.optionalFieldOf("elytra").forGetter(SkinEntry::elytra)
        ).apply(instance, SkinEntry::new));

        public boolean mojang() {
            return uuid().isPresent();
        }

        public boolean local() {
            return texture().isPresent();
        }

        public boolean slim() {
            return model()
                    .map(value -> value.equalsIgnoreCase("slim"))
                    .orElse(false);
        }
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
            for (SkinEntry entry : data.skins) {
                if (entry == null) return ValidationResult.failure("'skins' contains a null entry");
                if (entry.name() == null || entry.name().isBlank()) {
                    return ValidationResult.failure("'skins' contains an entry with blank name");
                }
                if (entry.mojang() == entry.local()) {
                    return ValidationResult.failure("'skins' entry '" + entry.name() + "' must contain exactly one of 'uuid' or 'texture'");
                }
                if (entry.mojang() && (entry.cape().isPresent() || entry.elytra().isPresent())) {
                    return ValidationResult.failure("'skins' entry '" + entry.name() + "' uses uuid and must not define local cape/elytra");
                }
            }
            return ValidationResult.success();
        }
    }
}
