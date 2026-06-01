package com.flechazo.eos.entity.ai;

import net.minecraft.world.item.*;

public final class SurvivorItemUtil {
    private SurvivorItemUtil() {
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem;
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem;
    }
}

