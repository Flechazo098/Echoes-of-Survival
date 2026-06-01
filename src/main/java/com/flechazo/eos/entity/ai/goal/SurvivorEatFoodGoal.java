package com.flechazo.eos.entity.ai.goal;

import com.flechazo.eos.entity.FriendlySurvivorEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public class SurvivorEatFoodGoal extends Goal {
    private final FriendlySurvivorEntity mob;
    private final float hpPercent;
    private final int outOfCombatTicks;
    private final int cooldownTicks;
    private final float healAmount;

    private int remainingUseTicks;
    private int remainingCooldown;
    private int inventorySlot = -1;
    private ItemStack storedOffhand = ItemStack.EMPTY;

    public SurvivorEatFoodGoal(FriendlySurvivorEntity mob, float hpPercent, int outOfCombatTicks, int cooldownTicks, float healAmount) {
        this.mob = mob;
        this.hpPercent = hpPercent;
        this.outOfCombatTicks = outOfCombatTicks;
        this.cooldownTicks = cooldownTicks;
        this.healAmount = healAmount;
    }

    @Override
    public boolean canUse() {
        if (remainingCooldown > 0) {
            remainingCooldown--;
            return false;
        }
        if (!SurvivorAiUtil.isLowHp(mob, hpPercent)) return false;
        if (mob.isUsingItem()) return false;
        if (mob.getTarget() != null) return false;
        if (mob.tickCount - mob.getLastCombatTick() < outOfCombatTicks) return false;

        ItemStack offhand = mob.getOffhandItem();
        if (!offhand.isEmpty()) {
            return isConsumable(offhand);
        }

        int slot = findConsumableSlot();
        return slot >= 0;
    }

    @Override
    public boolean canContinueToUse() {
        return remainingUseTicks > 0
                && mob.isAlive()
                && mob.getTarget() == null
                && mob.getHealth() < mob.getMaxHealth();
    }

    @Override
    public void start() {
        inventorySlot = -1;
        storedOffhand = ItemStack.EMPTY;

        if (mob.getOffhandItem().isEmpty()) {
            int slot = findConsumableSlot();
            if (slot >= 0) {
                inventorySlot = slot;
                storedOffhand = mob.getOffhandItem().copy();
                ItemStack stack = mob.getInventory().getItem(slot);
                mob.getInventory().setItem(slot, ItemStack.EMPTY);
                mob.setItemInHand(InteractionHand.OFF_HAND, stack);
            }
        }

        remainingUseTicks = 32;
        mob.startUsingItem(InteractionHand.OFF_HAND);
    }

    @Override
    public void tick() {
        remainingUseTicks--;
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) {
            mob.stopUsingItem();
        }

        if (mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(healAmount);
        }

        ItemStack offhand = mob.getOffhandItem();
        if (!offhand.isEmpty() && isConsumable(offhand)) {
            offhand.shrink(1);
            if (offhand.isEmpty()) {
                mob.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        }

        if (inventorySlot >= 0 && mob.getOffhandItem().isEmpty() && !storedOffhand.isEmpty()) {
            mob.setItemInHand(InteractionHand.OFF_HAND, storedOffhand);
        }

        inventorySlot = -1;
        storedOffhand = ItemStack.EMPTY;
        remainingUseTicks = 0;
        remainingCooldown = cooldownTicks;
    }

    private int findConsumableSlot() {
        var inv = mob.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isConsumable(stack)) return i;
        }
        return -1;
    }

    private static boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }
}

