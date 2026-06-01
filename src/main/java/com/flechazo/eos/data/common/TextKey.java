package com.flechazo.eos.data.common;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.List;

public record TextKey(List<String> keys) {
    public static final Codec<TextKey> CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf())
            .xmap(
                    either -> new TextKey(either.map(List::of, list -> list)),
                    value -> value.keys().size() == 1
                            ? Either.left(value.keys().getFirst())
                            : Either.right(value.keys())
            );

    public Component toComponent(RandomSource random) {
        if (keys == null || keys.isEmpty()) {
            return Component.empty();
        }
        if (keys.size() == 1) {
            return Component.translatable(keys.getFirst());
        }
        int idx = Math.floorMod(random.nextInt(), keys.size());
        return Component.translatable(keys.get(idx));
    }
}

