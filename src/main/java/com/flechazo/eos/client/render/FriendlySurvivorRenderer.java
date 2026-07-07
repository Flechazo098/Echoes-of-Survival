package com.flechazo.eos.client.render;

import com.flechazo.eos.client.skin.MojangSkinCache;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class FriendlySurvivorRenderer extends SurvivorPlayerRenderer<FriendlySurvivorEntity> {
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("echoes", "textures/entity/survivor/friendly_survivor.png");

    public FriendlySurvivorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SurvivorPlayerSkin skin(FriendlySurvivorEntity entity) {
        SurvivorPlayerSkin professionSkin = entity.getProfessionId()
                .flatMap(EosDatapackIndex::profession)
                .flatMap(profession -> profession.skin()
                        .flatMap(library -> EosDatapackIndex.pickSkinProfile(library, entity.getUUID())))
                .map(FriendlySurvivorRenderer::fromProfile)
                .orElse(null);
        if (professionSkin != null) {
            return professionSkin;
        }

        SurvivorPlayerSkin mojang = entity.getSkinUuid()
                .flatMap(MojangSkinCache::getOrRequest)
                .orElse(null);
        if (mojang != null) {
            return mojang;
        }

        return SurvivorPlayerSkin.wide(SurvivorSkins.pick(entity.getUUID(), SurvivorSkins.PRESET_POOL, DEFAULT_TEXTURE));
    }

    private static SurvivorPlayerSkin fromProfile(EosDatapackIndex.SkinProfile profile) {
        SurvivorPlayerSkin mojang = profile.uuid()
                .flatMap(MojangSkinCache::getOrRequest)
                .orElse(null);
        return mojang != null
                ? mojang
                : SurvivorPlayerSkin.fromLocalProfile(profile).orElse(null);
    }
}
