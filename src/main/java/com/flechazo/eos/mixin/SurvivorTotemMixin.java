package com.flechazo.eos.mixin;

import com.flechazo.eos.entity.AbstractSurvivorEntity;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class SurvivorTotemMixin {

    @Inject(method = "checkTotemDeathProtection",
            at = @At("RETURN"),
            cancellable = true)
    private void onCheckTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof AbstractSurvivorEntity survivor)) return;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        var inv = survivor.tacticalInventory;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)
                    && CommonHooks.onLivingUseTotem(self, source, stack, InteractionHand.OFF_HAND)) {
                stack.shrink(1);

                if (self instanceof ServerPlayer sp) {
                    sp.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING), 1);
                    CriteriaTriggers.USED_TOTEM.trigger(sp, stack);
                }
                self.gameEvent(GameEvent.ITEM_INTERACT_FINISH);

                self.setHealth(1.0F);
                self.removeAllEffects();
                self.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                self.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                self.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                self.level().broadcastEntityEvent(self, (byte) 35);

                cir.setReturnValue(true);
                return;
            }
        }
    }
}
