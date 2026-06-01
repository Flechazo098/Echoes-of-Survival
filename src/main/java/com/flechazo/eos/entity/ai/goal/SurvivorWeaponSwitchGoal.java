package com.flechazo.eos.entity.ai.goal;

import com.flechazo.eos.entity.ai.SurvivorItemUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class SurvivorWeaponSwitchGoal extends Goal {
    private final Mob mob;
    private final double meleeDistanceSqr;
    private final double rangedDistanceSqr;
    private int cooldownTicks = 0;
    private boolean preferMeleeMode = false;

    public SurvivorWeaponSwitchGoal(Mob mob, double preferMeleeDistance) {
        this.mob = mob;
        double melee = Math.max(1.0, preferMeleeDistance);
        double ranged = melee + 2.0;
        this.meleeDistanceSqr = melee * melee;
        this.rangedDistanceSqr = ranged * ranged;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && mob.getTarget().isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        if (mob.isUsingItem()) return;

        var target = mob.getTarget();
        if (target == null) return;

        ItemStack main = mob.getMainHandItem();
        ItemStack off = mob.getOffhandItem();
        boolean mainRanged = SurvivorItemUtil.isRangedWeapon(main);
        boolean offRanged = SurvivorItemUtil.isRangedWeapon(off);
        boolean mainMelee = SurvivorItemUtil.isMeleeWeapon(main);
        boolean offMelee = SurvivorItemUtil.isMeleeWeapon(off);

        if (!((mainRanged && offMelee) || (mainMelee && offRanged))) return;

        double dist = mob.distanceToSqr(target);

        // Update mode with hysteresis: once we decide melee/ranged, we don't immediately flip back
        // unless the target crosses the opposite threshold.
        if (!preferMeleeMode && dist <= meleeDistanceSqr) {
            preferMeleeMode = true;
        } else if (preferMeleeMode && dist >= rangedDistanceSqr) {
            preferMeleeMode = false;
        }
        boolean wantMelee = preferMeleeMode;

        if (wantMelee && mainRanged && offMelee) {
            swapHands();
        } else if (!wantMelee && mainMelee && offRanged) {
            swapHands();
        }
    }

    private void swapHands() {
        ItemStack main = mob.getMainHandItem();
        ItemStack off = mob.getOffhandItem();
        mob.setItemInHand(InteractionHand.MAIN_HAND, off);
        mob.setItemInHand(InteractionHand.OFF_HAND, main);
        cooldownTicks = 60;
    }
}
