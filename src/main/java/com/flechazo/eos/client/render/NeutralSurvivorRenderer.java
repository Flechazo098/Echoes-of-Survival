package com.flechazo.eos.client.render;

import com.flechazo.eos.client.skin.MojangSkinCache;
import com.flechazo.eos.entity.NeutralSurvivorEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class NeutralSurvivorRenderer extends HumanoidMobRenderer<NeutralSurvivorEntity, HumanoidModel<NeutralSurvivorEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("echoes", "textures/entity/survivor/neutral_survivor.png");

    public NeutralSurvivorRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(NeutralSurvivorEntity entity) {
        ResourceLocation mojang = entity.getSkinUuid()
                .flatMap(MojangSkinCache::getOrRequest)
                .orElse(null);
        if (mojang != null) {
            return mojang;
        }
        return SurvivorSkins.pick(entity.getUUID(), SurvivorSkins.PRESET_POOL, TEXTURE);
    }
}
