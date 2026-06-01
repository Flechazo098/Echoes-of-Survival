package com.flechazo.eos.entity.ai.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public final class SurvivorAiUtil {
    private SurvivorAiUtil() {
    }

    public static boolean hasTotemInOffhand(LivingEntity entity) {
        if (entity == null) return false;
        ItemStack offhand = entity.getOffhandItem();
        return offhand.is(Items.TOTEM_OF_UNDYING);
    }

    public static boolean isLowHp(LivingEntity entity, float hpPercent) {
        if (entity == null) return false;
        if (hpPercent <= 0) return false;
        if (hasTotemInOffhand(entity)) return false;
        return entity.getHealth() < entity.getMaxHealth() * hpPercent;
    }

    public static boolean shouldFightCreeper(LivingEntity entity) {
        if (entity == null) return false;
        UUID uuid = entity.getUUID();
        return Math.floorMod(uuid.hashCode(), 100) < 20;
    }
}

