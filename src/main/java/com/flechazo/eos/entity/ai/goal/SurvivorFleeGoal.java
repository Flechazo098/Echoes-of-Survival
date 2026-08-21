package com.flechazo.eos.entity.ai.goal;

import com.flechazo.eos.entity.AbstractSurvivorEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class SurvivorFleeGoal extends Goal {
    private final PathfinderMob mob;
    private final PathNavigation navigation;
    private final float hpPercent;
    private final float maxThreatDist;
    private final double walkSpeed;
    private final double sprintSpeed;
    private final float chance;

    private int cooldownTicks = 0;
    private boolean wasAirborne = false;

    @Nullable
    private LivingEntity threat;
    @Nullable
    private Path path;

    public SurvivorFleeGoal(PathfinderMob mob, float hpPercent, float chance, float maxThreatDist, double walkSpeed, double sprintSpeed) {
        this.mob = mob;
        this.navigation = mob.getNavigation();
        this.hpPercent = hpPercent;
        this.chance = chance;
        this.maxThreatDist = maxThreatDist;
        this.walkSpeed = walkSpeed;
        this.sprintSpeed = sprintSpeed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return false;
        }
        if (!SurvivorAiUtil.isLowHp(mob, effectiveHpPercent())) return false;
        if (SurvivorAiUtil.hasTotemInOffhand(mob)) return false;
        if (chance < 1.0F && mob.getRandom().nextFloat() > chance) return false;

        LivingEntity candidate = mob.getTarget();
        if (candidate == null) candidate = mob.getLastHurtByMob();
        if (candidate == null) return false;
        if (!candidate.isAlive()) return false;

        if (mob.distanceTo(candidate) > maxThreatDist) return false;
        if (candidate instanceof Player p && (p.isCreative() || p.isSpectator())) return false;

        this.threat = candidate;

        return generatePathAway();
    }

    private boolean generatePathAway() {
        if (this.threat == null) return false;
        Vec3 away = null;
        for (int i = 0; i < 10; i++) {
            away = DefaultRandomPos.getPosAway(this.mob, 64, 7, this.threat.position());
            if (away != null) break;
        }
        if (away == null) return false;

        if (mob.distanceToSqr(away.x, away.y, away.z) < threat.distanceToSqr(mob)) return false;

        this.path = this.navigation.createPath(away.x, away.y, away.z, 0);
        return this.path != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!SurvivorAiUtil.isLowHp(mob, effectiveHpPercent())) return false;
        if (this.threat == null || !this.threat.isAlive()) return false;
        if (this.threat instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (this.mob.distanceToSqr(this.threat) > (double) (maxThreatDist * maxThreatDist)) return false;

        if (!this.mob.onGround()) {
            wasAirborne = true;
        } else if (wasAirborne) {
            // after landing, re-plan so it doesn't get stuck jumping over small ledges
            wasAirborne = false;
            this.navigation.stop();
            generatePathAway();
            if (this.path != null) {
                this.navigation.moveTo(this.path, adjustedSpeed(this.walkSpeed));
            }
        }

        return !this.navigation.isDone();
    }

    @Override
    public void start() {
        if (this.path != null) {
            this.navigation.moveTo(this.path, adjustedSpeed(this.walkSpeed));
        }
    }

    @Override
    public void stop() {
        this.threat = null;
        this.path = null;
        this.mob.getNavigation().setSpeedModifier(1.0);
        this.cooldownTicks = 20 * 3;
    }

    @Override
    public void tick() {
        if (this.threat == null) return;

        double distSqr = this.mob.distanceToSqr(this.threat);
        this.mob.getNavigation().setSpeedModifier(adjustedSpeed(
                distSqr < 7.0 * 7.0 ? this.sprintSpeed : this.walkSpeed));
    }

    private float effectiveHpPercent() {
        return mob instanceof AbstractSurvivorEntity survivor
                ? survivor.retreatHealthThreshold(hpPercent)
                : hpPercent;
    }

    private double adjustedSpeed(double speed) {
        return mob instanceof AbstractSurvivorEntity survivor
                ? speed * survivor.fleeSpeedMultiplier()
                : speed;
    }
}
