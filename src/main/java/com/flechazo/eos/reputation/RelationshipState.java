package com.flechazo.eos.reputation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent three-layer relationship data owned by one player. */
public record RelationshipState(
        int globalReputation,
        Map<ResourceLocation, Integer> factionReputation,
        Map<UUID, Integer> personalTrust
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<RelationshipState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("global_reputation", 0).forGetter(RelationshipState::globalReputation),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .optionalFieldOf("faction_reputation", Map.of())
                    .forGetter(RelationshipState::factionReputation),
            Codec.unboundedMap(UUID_CODEC, Codec.INT)
                    .optionalFieldOf("personal_trust", Map.of())
                    .forGetter(RelationshipState::personalTrust)
    ).apply(instance, RelationshipState::new));

    public RelationshipState {
        factionReputation = Map.copyOf(factionReputation);
        personalTrust = Map.copyOf(personalTrust);
    }

    public static RelationshipState empty() {
        return new RelationshipState(0, Map.of(), Map.of());
    }

    public RelationshipState withGlobalReputation(int value) {
        return new RelationshipState(value, factionReputation, personalTrust);
    }

    public RelationshipState withFactionReputation(ResourceLocation factionId, int value) {
        Map<ResourceLocation, Integer> next = new HashMap<>(factionReputation);
        if (value == 0) next.remove(factionId);
        else next.put(factionId, value);
        return new RelationshipState(globalReputation, next, personalTrust);
    }

    public RelationshipState withPersonalTrust(UUID survivorUuid, int value) {
        Map<UUID, Integer> next = new HashMap<>(personalTrust);
        if (value == 0) next.remove(survivorUuid);
        else next.put(survivorUuid, value);
        return new RelationshipState(globalReputation, factionReputation, next);
    }
}
