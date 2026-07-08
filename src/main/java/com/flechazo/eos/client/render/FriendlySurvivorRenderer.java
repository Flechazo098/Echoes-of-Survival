package com.flechazo.eos.client.render;

import com.flechazo.eos.client.skin.MojangSkinCache;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.hkt.Maybe;
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
        return entity.getProfessionId()
                .flatMap(EosDatapackIndex::profession)
                .flatMap(profession -> profession.skinLibrary()
                        .flatMap(library -> EosDatapackIndex.pickSkinProfile(library, entity.getUUID())))
                .flatMap(FriendlySurvivorRenderer::fromProfile)
                .or(() -> entity.getSkinUuid()
                        .flatMap(MojangSkinCache::getOrRequest))
                .orElse(SurvivorPlayerSkin.wide(SurvivorSkins.pick(entity.getUUID(), SurvivorSkins.PRESET_POOL, DEFAULT_TEXTURE)));
    }

    private static Maybe<SurvivorPlayerSkin> fromProfile(EosDatapackIndex.SkinProfile profile) {
        return profile.uuid()
                .flatMap(uuid ->
                        MojangSkinCache.getOrRequest(uuid)
                                .or(() -> SurvivorPlayerSkin.fromLocalProfile(profile))
                );
    }
}
