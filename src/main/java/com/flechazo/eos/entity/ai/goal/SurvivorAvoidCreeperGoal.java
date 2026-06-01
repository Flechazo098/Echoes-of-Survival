package com.flechazo.eos.entity.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * If the creeper is priming (swell dir &gt; 0 / ignited) we always avoid it.
 * Otherwise, we avoid it unless this survivor has the "fight creepers" personality.
 * </p>
 */
public class SurvivorAvoidCreeperGoal extends Goal {
    private final PathfinderMob mob;
    private final float maxDist;
    private final PathNavigation navigation;
    private final double walkSpeed;
    private final double sprintSpeed;

    @Nullable
    private Creeper toAvoid;
    @Nullable
    private Path path;

    public SurvivorAvoidCreeperGoal(PathfinderMob mob, float maxDist, double walkSpeed, double sprintSpeed) {
        this.mob = mob;
        this.maxDist = maxDist;
        this.walkSpeed = walkSpeed;
        this.sprintSpeed = sprintSpeed;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        List<Creeper> creepers = mob.level().getEntitiesOfClass(
                Creeper.class,
                mob.getBoundingBox().inflate(this.maxDist, 10.0, this.maxDist),
                c -> c != null && c.isAlive()
        );
        if (creepers.isEmpty()) return false;

        this.toAvoid = nearest(creepers);
        if (this.toAvoid == null) return false;

        boolean priming = this.toAvoid.isIgnited() || this.toAvoid.getSwellDir() > 0;
        if (!priming && SurvivorAiUtil.shouldFightCreeper(mob)) {
            return false;
        }

        Vec3 away = DefaultRandomPos.getPosAway(this.mob, 16, 7, this.toAvoid.position());
        if (away == null) return false;

        if (this.toAvoid.distanceToSqr(away.x, away.y, away.z) < this.toAvoid.distanceToSqr(this.mob)) {
            return false;
        }

        this.path = this.navigation.createPath(away.x, away.y, away.z, 0);
        return this.path != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.navigation.isDone() && this.toAvoid != null && this.toAvoid.isAlive();
    }

    @Override
    public void start() {
        if (this.path != null) {
            this.navigation.moveTo(this.path, this.walkSpeed);
        }
    }

    @Override
    public void stop() {
        this.toAvoid = null;
        this.path = null;
        this.mob.getNavigation().setSpeedModifier(1.0);
    }

    @Override
    public void tick() {
        if (this.toAvoid == null) return;
        this.mob.setTarget(null);
        double distSqr = this.mob.distanceToSqr(this.toAvoid);
        this.mob.getNavigation().setSpeedModifier(distSqr < 7.0 * 7.0 ? this.sprintSpeed : this.walkSpeed);
    }

    @Nullable
    private Creeper nearest(List<Creeper> creepers) {
        Creeper best = null;
        double bestDist = Double.MAX_VALUE;
        for (Creeper creeper : creepers) {
            double d = this.mob.distanceToSqr(creeper);
            if (d < bestDist) {
                bestDist = d;
                best = creeper;
            }
        }
        return best;
    }
}

