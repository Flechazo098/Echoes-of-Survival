package com.flechazo.eos.entity.ai.goal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Smart-ish shield behavior: blocks when close to target or when target is ranged.
 */
public class SurvivorRaiseShieldGoal extends Goal {
    private final Mob mob;
    private int cooldownTicks = 0;
    private int activeTicks = 0;
    private static final int MAX_ACTIVE_TICKS = 16;

    public SurvivorRaiseShieldGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (cooldownTicks > 0) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        // Don't block while charging bow/crossbow (it breaks ranged combat flow).
        if (mob.isUsingItem()) {
            ItemStack using = mob.getUseItem();
            if (!using.isEmpty() && (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem)) {
                return false;
            }
        }

        ItemStack off = mob.getOffhandItem();
        if (off.isEmpty() || !off.getItem().canPerformAction(off, ItemAbilities.SHIELD_BLOCK)) {
            return false;
        }

        double dist = mob.distanceTo(target);
        boolean close = dist <= 3.5D;
        boolean targetRanged = target instanceof net.minecraft.world.entity.monster.RangedAttackMob;
        // Only keep the "ranged block" behavior when the threat is within a sensible range.
        return close || (targetRanged && dist <= 12.0D);
    }

    @Override
    public boolean canContinueToUse() {
        if (activeTicks >= MAX_ACTIVE_TICKS) return false;
        if (!mob.isUsingItem()) return false;
        ItemStack using = mob.getUseItem();
        return !using.isEmpty() && using.getItem().canPerformAction(using, ItemAbilities.SHIELD_BLOCK) && canUse();
    }

    @Override
    public void start() {
        activeTicks = 0;
        mob.startUsingItem(InteractionHand.OFF_HAND);
    }

    @Override
    public void stop() {
        mob.stopUsingItem();
        cooldownTicks = 20;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cooldownTicks > 0) cooldownTicks--;
        if (mob.isUsingItem()) activeTicks++;
    }
}
