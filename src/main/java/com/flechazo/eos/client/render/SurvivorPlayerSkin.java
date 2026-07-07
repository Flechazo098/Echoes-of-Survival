package com.flechazo.eos.client.render;

import com.flechazo.eos.data.EosDatapackIndex;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record SurvivorPlayerSkin(
        ResourceLocation texture,
        boolean slim,
        Optional<ResourceLocation> cape,
        Optional<ResourceLocation> elytra
) {
    public static SurvivorPlayerSkin wide(ResourceLocation texture) {
        return new SurvivorPlayerSkin(texture, false, Optional.empty(), Optional.empty());
    }

    public static Optional<SurvivorPlayerSkin> fromLocalProfile(EosDatapackIndex.SkinProfile profile) {
        if (profile == null || profile.texture().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SurvivorPlayerSkin(
                profile.texture().get(),
                profile.slim(),
                profile.cape(),
                profile.elytra()
        ));
    }

    public static SurvivorPlayerSkin fromProfile(EosDatapackIndex.SkinProfile profile, SurvivorPlayerSkin mojang) {
        return mojang != null
                ? mojang
                : fromLocalProfile(profile).orElse(null);
    }

    public static SurvivorPlayerSkin fromMojang(
            ResourceLocation texture,
            boolean slim,
            @Nullable ResourceLocation cape,
            @Nullable ResourceLocation elytra
    ) {
        return new SurvivorPlayerSkin(
                texture,
                slim,
                Optional.ofNullable(cape),
                Optional.ofNullable(elytra)
        );
    }
}
