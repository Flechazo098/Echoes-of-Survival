package com.flechazo.eos.client.render;

import com.flechazo.eos.entity.AbstractSurvivorEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class SurvivorElytraLayer<T extends AbstractSurvivorEntity> extends ElytraLayer<T, PlayerModel<T>> {
    private final SurvivorPlayerRenderer<T> renderer;

    public SurvivorElytraLayer(SurvivorPlayerRenderer<T> renderer, EntityModelSet modelSet) {
        super(renderer, modelSet);
        this.renderer = renderer;
    }

    @Override
    public ResourceLocation getElytraTexture(ItemStack stack, T entity) {
        SurvivorPlayerSkin skin = this.renderer.skin(entity);
        return skin.elytra()
                .or(skin::cape)
                .orElseGet(() -> super.getElytraTexture(stack, entity));
    }
}
