package com.flechazo.eos.client.render;

import com.atsuishio.superbwarfare.client.PoseTool;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.flechazo.eos.entity.AbstractSurvivorEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public abstract class SurvivorPlayerRenderer<T extends AbstractSurvivorEntity> extends MobRenderer<T, PlayerModel<T>> {
    private final PlayerModel<T> wideModel;
    private final PlayerModel<T> slimModel;

    protected SurvivorPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new SurvivorElytraLayer<>(this, context.getModelSet()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()) {
            @Override
            protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm, PoseStack pose, MultiBufferSource buf, int light) {
                if (arm == HumanoidArm.LEFT && entity.getMainHandItem().getItem() instanceof GunItem) {
                    return;
                }
                super.renderArmWithItem(entity, stack, ctx, arm, pose, buf, light);
            }
        });
        this.addLayer(new SurvivorCapeLayer<>(this));
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = skin(entity).slim() ? this.slimModel : this.wideModel;
        this.model.rightArmPose = armPose(entity, HumanoidArm.RIGHT);
        this.model.leftArmPose = armPose(entity, HumanoidArm.LEFT);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private PlayerModel.ArmPose armPose(T entity, HumanoidArm arm) {
        InteractionHand hand = entity.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.getItem() instanceof GunItem) {
            return PoseTool.pose(entity, hand, stack);
        }
        return PlayerModel.ArmPose.EMPTY;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return skin(entity).texture();
    }

    @Override
    protected boolean shouldShowName(T entity) {
        return entity.getProfessionId().isPresent() || super.shouldShowName(entity);
    }

    protected abstract SurvivorPlayerSkin skin(T entity);
}
