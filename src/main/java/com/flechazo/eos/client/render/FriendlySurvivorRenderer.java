package com.flechazo.eos.client.render;

import com.flechazo.eos.client.skin.MojangSkinCache;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class FriendlySurvivorRenderer extends HumanoidMobRenderer<FriendlySurvivorEntity, HumanoidModel<FriendlySurvivorEntity>> {
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("echoes", "textures/entity/survivor/friendly_survivor.png");

    public FriendlySurvivorRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(FriendlySurvivorEntity entity) {
        ResourceLocation forced = entity.getProfessionId()
                .flatMap(EosDatapackIndex::profession)
                .flatMap(def -> def.skin())
                .orElse(null);
        if (forced != null) {
            return forced;
        }

        ResourceLocation mojang = entity.getSkinUuid()
                .flatMap(MojangSkinCache::getOrRequest)
                .orElse(null);
        if (mojang != null) {
            return mojang;
        }

        return SurvivorSkins.pick(entity.getUUID(), SurvivorSkins.PRESET_POOL, DEFAULT_TEXTURE);
    }
}

