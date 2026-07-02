package com.flechazo.eos.client.render;

import com.flechazo.eos.client.skin.MojangSkinCache;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.trade.ProfessionDefinition;
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
        SurvivorPlayerSkin mojang = entity.getSkinUuid()
                .flatMap(MojangSkinCache::getOrRequest)
                .orElse(null);
        if (mojang != null) {
            return mojang;
        }

        SurvivorPlayerSkin forced = entity.getProfessionId()
                .flatMap(EosDatapackIndex::profession)
                .flatMap(ProfessionDefinition::skin)
                .map(SurvivorPlayerSkin::fromDefinition)
                .orElse(null);
        if (forced != null) {
            return forced;
        }

        return SurvivorPlayerSkin.wide(SurvivorSkins.pick(entity.getUUID(), SurvivorSkins.PRESET_POOL, DEFAULT_TEXTURE));
    }
}
