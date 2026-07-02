package com.flechazo.eos.client.render;

import com.flechazo.eos.data.trade.ProfessionDefinition;
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

    public static SurvivorPlayerSkin fromDefinition(ProfessionDefinition.SkinDefinition definition) {
        return new SurvivorPlayerSkin(
                definition.texture(),
                definition.slim(),
                definition.cape(),
                definition.elytra()
        );
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
