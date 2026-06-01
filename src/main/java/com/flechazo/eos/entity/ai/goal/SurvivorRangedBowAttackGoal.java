package com.flechazo.eos.entity.ai.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.Items;

public class SurvivorRangedBowAttackGoal<T extends Mob & RangedAttackMob> extends RangedBowAttackGoal<T> {
    private final T mob;

    public SurvivorRangedBowAttackGoal(T mob, double speed, int attackInterval, float range) {
        super(mob, speed, attackInterval, range);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (!mob.getMainHandItem().is(Items.BOW)) return false;
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (!mob.getMainHandItem().is(Items.BOW)) return false;
        return super.canContinueToUse();
    }
}
