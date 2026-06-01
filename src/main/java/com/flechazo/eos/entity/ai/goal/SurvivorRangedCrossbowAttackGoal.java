package com.flechazo.eos.entity.ai.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.Items;

public class SurvivorRangedCrossbowAttackGoal<T extends Mob & RangedAttackMob & CrossbowAttackMob>
        extends RangedCrossbowAttackGoal<T> {

    private final T mob;

    public SurvivorRangedCrossbowAttackGoal(T mob, double speed, float range) {
        super(mob, speed, range);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (!mob.getMainHandItem().is(Items.CROSSBOW)) return false;
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (!mob.getMainHandItem().is(Items.CROSSBOW)) return false;
        return super.canContinueToUse();
    }
}
