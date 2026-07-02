package com.flechazo.eos.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.flechazo.eos.entity.AbstractSurvivorEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

public class SurvivorCapeLayer<T extends AbstractSurvivorEntity> extends RenderLayer<T, PlayerModel<T>> {
    private final SurvivorPlayerRenderer<T> renderer;

    public SurvivorCapeLayer(SurvivorPlayerRenderer<T> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (entity.isInvisible() || entity.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            return;
        }

        ResourceLocation cape = this.renderer.skin(entity).cape().orElse(null);
        if (cape == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.isCrouching() ? 28.0F : 6.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entitySolid(cape));
        this.getParentModel().renderCloak(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
