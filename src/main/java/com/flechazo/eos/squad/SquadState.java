package com.flechazo.eos.squad;

import com.mojang.serialization.Codec;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record SquadState(Set<UUID> members) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<SquadState> CODEC = UUID_CODEC.listOf().xmap(
            values -> new SquadState(new LinkedHashSet<>(values)),
            state -> state.members().stream().toList()
    );

    public SquadState {
        members = Set.copyOf(members);
    }

    public static SquadState empty() {
        return new SquadState(Set.of());
    }
}
