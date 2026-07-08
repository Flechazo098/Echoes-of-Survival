package com.flechazo.eos.client.render;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.client.skin.MojangSkinCache;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.entity.NeutralSurvivorEntity;
import com.flechazo.hkt.Maybe;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class NeutralSurvivorRenderer extends SurvivorPlayerRenderer<NeutralSurvivorEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "textures/entity/survivor/neutral_survivor.png");

    public NeutralSurvivorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SurvivorPlayerSkin skin(NeutralSurvivorEntity entity) {
        return entity.getProfessionId()
                .flatMap(EosDatapackIndex::profession)
                .flatMap(profession -> profession.neutralSkinLibrary()
                        .flatMap(library -> EosDatapackIndex.pickSkinProfile(library, entity.getUUID())))
                .flatMap(NeutralSurvivorRenderer::fromProfile)
                .or(() -> entity.getSkinUuid()
                        .flatMap(MojangSkinCache::getOrRequest))
                .orElse(SurvivorPlayerSkin.wide(SurvivorSkins.pick(entity.getUUID(), SurvivorSkins.PRESET_POOL, TEXTURE)));
    }

    private static Maybe<SurvivorPlayerSkin> fromProfile(EosDatapackIndex.SkinProfile profile) {
        return profile.uuid()
                .flatMap(uuid -> MojangSkinCache.getOrRequest(uuid)
                        .or(() -> SurvivorPlayerSkin.fromLocalProfile(profile)))
                .or(() -> SurvivorPlayerSkin.fromLocalProfile(profile));
    }
}
