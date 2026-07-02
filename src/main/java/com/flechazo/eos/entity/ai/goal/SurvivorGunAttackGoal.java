package com.flechazo.eos.entity.ai.goal;

import com.atsuishio.superbwarfare.data.gun.FireMode;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.atsuishio.superbwarfare.tools.MillisTimer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SurvivorGunAttackGoal extends Goal {
    private final Mob mob;
    private int aimTime;
    private final MillisTimer shootTimer = new MillisTimer();
    private static final int AIM_THRESHOLD = 5;
    private final double shootRangeSqr;
    private final double chaseRangeSqr;
    private static final long DEFAULT_COOLDOWN = 500;
    private static final long SEMI_INTERVAL = 200;

    public SurvivorGunAttackGoal(Mob mob) {
        this(mob, 48.0, 64.0);
    }

    public SurvivorGunAttackGoal(Mob mob, double shootRange, double chaseRange) {
        this.mob = mob;
        this.shootRangeSqr = shootRange * shootRange;
        this.chaseRangeSqr = Math.max(this.shootRangeSqr, chaseRange * chaseRange);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return false;
        GunData gunData = currentGunData();
        if (gunData == null) return false;
        return hasAmmoAvailable(gunData);
    }

    @Override
    public boolean canContinueToUse() {
        GunData gunData = currentGunData();
        return (this.canUse() || !this.mob.getNavigation().isDone())
                && gunData != null
                && hasAmmoAvailable(gunData);
    }

    @Override
    public void start() {
        this.aimTime = 0;
        this.mob.setAggressive(true);
    }

    @Override
    public void stop() {
        this.mob.setAggressive(false);
        this.mob.stopUsingItem();
        this.shootTimer.stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;
        GunData gunData = currentGunData();
        if (gunData == null) return;

        double distSq = this.mob.distanceToSqr(target);
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);

        if (canSee) {
            this.aimTime = Math.min(AIM_THRESHOLD, this.aimTime + 1);
        } else {
            this.aimTime = 0;
        }

        this.mob.lookAt(target, 30.0F, 30.0F);
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (distSq > this.shootRangeSqr && distSq <= this.chaseRangeSqr) {
            this.mob.getNavigation().moveTo(target, 1.0);
        } else {
            this.mob.getNavigation().stop();
        }

        gunData.tick(this.mob, true);

        if (gunData.shouldStartReloading(this.mob)) {
            gunData.startReload();
        }
        if (gunData.shouldStartBolt()) {
            gunData.startBolt();
        }

        if (!gunData.canShoot(this.mob)) {
            this.shootTimer.stop();
            return;
        }
        if (this.aimTime < AIM_THRESHOLD) {
            this.shootTimer.stop();
            return;
        }

        double rps = gunData.get(GunProp.RPM).doubleValue() / 60.0;
        long cooldown = rps > 0 ? Math.round(1000.0 / rps) : DEFAULT_COOLDOWN;

        FireMode fireMode = gunData.selectedFireModeInfo().mode;
        if (fireMode == FireMode.SEMI || (fireMode == FireMode.BURST && gunData.burstAmount.get() == 0)) {
            cooldown += SEMI_INTERVAL;
        }

        if (!this.shootTimer.started()) {
            this.shootTimer.start();
            this.shootTimer.setProgress(cooldown + 1);
        }

        if (this.shootTimer.getProgress() >= cooldown) {
            long progress = this.shootTimer.getProgress();
            do {
                gunData.shoot(this.mob, 0.5, false, target.getUUID());
                progress -= cooldown;
            } while (progress - cooldown > 0);
            this.shootTimer.setProgress(progress);
        }
    }

    private GunData currentGunData() {
        if (!(this.mob.getMainHandItem().getItem() instanceof GunItem)) return null;
        return GunData.from(this.mob.getMainHandItem());
    }

    private boolean hasAmmoAvailable(GunData gunData) {
        return gunData.countBackupAmmo(this.mob) > 0 || gunData.hasEnoughAmmoToShoot(this.mob);
    }
}
