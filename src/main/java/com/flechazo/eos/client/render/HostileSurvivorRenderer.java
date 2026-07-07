package com.flechazo.eos.client.render;

import com.flechazo.eos.client.skin.MojangSkinCache;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.entity.HostileSurvivorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class HostileSurvivorRenderer extends SurvivorPlayerRenderer<HostileSurvivorEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("echoes", "textures/entity/survivor/hostile_survivor.png");

    public HostileSurvivorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SurvivorPlayerSkin skin(HostileSurvivorEntity entity) {
        SurvivorPlayerSkin professionSkin = entity.getProfessionId()
                .flatMap(EosDatapackIndex::profession)
                .flatMap(profession -> profession.hostileSkinLibrary()
                        .flatMap(library -> EosDatapackIndex.pickSkinProfile(library, entity.getUUID())))
                .map(HostileSurvivorRenderer::fromProfile)
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

        return SurvivorPlayerSkin.wide(SurvivorSkins.pick(entity.getUUID(), SurvivorSkins.PRESET_POOL, TEXTURE));
    }

    private static SurvivorPlayerSkin fromProfile(EosDatapackIndex.SkinProfile profile) {
        return profile.uuid()
                .map(MojangSkinCache::getOrRequest)
                .map(maybe -> maybe.or(() -> SurvivorPlayerSkin.fromLocalProfile(profile)))
                .flatMap(maybe -> maybe)
                .orElse(null);
    }
}
