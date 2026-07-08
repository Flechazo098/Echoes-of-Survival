package com.flechazo.eos.client.render;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.hkt.Maybe;
import net.minecraft.resources.ResourceLocation;

public record SurvivorPlayerSkin(
        ResourceLocation texture,
        boolean slim,
        Maybe<ResourceLocation> cape,
        Maybe<ResourceLocation> elytra
) {
    public static SurvivorPlayerSkin wide(ResourceLocation texture) {
        return new SurvivorPlayerSkin(texture, false, Maybe.none(), Maybe.none());
    }

    public static Maybe<SurvivorPlayerSkin> fromLocalProfile(EosDatapackIndex.SkinProfile profile) {
        if (profile == null || profile.texture().isEmpty()) {
            return Maybe.none();
        }
        return Maybe.some(new SurvivorPlayerSkin(
                profile.texture().get(),
                profile.slim(),
                profile.cape(),
                profile.elytra()
        ));
    }

    public static SurvivorPlayerSkin fromMojang(
            ResourceLocation texture,
            boolean slim,
            Maybe<ResourceLocation> cape,
            Maybe<ResourceLocation> elytra
    ) {
        return new SurvivorPlayerSkin(
                texture,
                slim,
                cape,
                elytra
        );
    }
}
