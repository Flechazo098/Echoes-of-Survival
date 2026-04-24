package com.flechazo.eos.entity.ai.goal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Uses "tactical items" from datapack as mob-effect IDs (e.g. minecraft:regeneration).
 * This is a lightweight stand-in for real potion throwing/drinking for first testing.
 */
public class SurvivorUseTacticalEffectGoal extends Goal {
    private final Mob mob;
    private final List<ResourceLocation> effects;
    private final float healthThreshold;
    private int cooldownTicks;

    public SurvivorUseTacticalEffectGoal(Mob mob, List<ResourceLocation> effects, float healthThreshold) {
        this.mob = mob;
        this.effects = effects != null ? effects : new ArrayList<>();
        this.healthThreshold = healthThreshold;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        if (cooldownTicks > 0) return false;
        if (effects.isEmpty()) return false;
        return mob.getHealth() <= mob.getMaxHealth() * healthThreshold;
    }

    @Override
    public void start() {
        if (effects.isEmpty()) return;
        ResourceLocation id = effects.removeFirst();
        BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(effect -> mob.addEffect(new MobEffectInstance(effect, 20 * 15, 0)));
        cooldownTicks = 20 * 20;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cooldownTicks > 0) cooldownTicks--;
    }
}
