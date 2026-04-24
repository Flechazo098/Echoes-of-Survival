package com.flechazo.eos.client.render;

import com.flechazo.eos.entity.HostileSurvivorEntity;
import com.flechazo.eos.client.skin.MojangSkinCache;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class HostileSurvivorRenderer extends HumanoidMobRenderer<HostileSurvivorEntity, HumanoidModel<HostileSurvivorEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("echoes", "textures/entity/survivor/hostile_survivor.png");

    public HostileSurvivorRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(HostileSurvivorEntity entity) {
        ResourceLocation mojang = entity.getSkinUuid()
                .flatMap(MojangSkinCache::getOrRequest)
                .orElse(null);
        if (mojang != null) {
            return mojang;
        }
        return SurvivorSkins.pick(entity.getUUID(), SurvivorSkins.PRESET_POOL, TEXTURE);
    }
}
