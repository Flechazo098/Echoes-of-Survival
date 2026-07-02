package com.flechazo.eos.entity.ai.goal;

import com.flechazo.eos.entity.FriendlySurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class FollowRecruitOwnerGoal extends Goal {
    private static final double CLOSE_THREAT_DISTANCE_SQR = 6.0D * 6.0D;
    private static final double OWNER_THREAT_DISTANCE_SQR = 8.0D * 8.0D;
    private static final double RECENT_ATTACK_DISTANCE_SQR = 20.0D * 20.0D;
    private static final int COMBAT_LOCK_TICKS = 20 * 3;
    private static final double TELEPORT_EXTRA_DISTANCE = 8.0D;
    private static final int MAX_FAILED_REPATHS = 3;
    private static final int STUCK_CHECK_INTERVAL_TICKS = 20;
    private static final double STUCK_PROGRESS_EPSILON_SQR = 1.0D;

    private final FriendlySurvivorEntity survivor;
    private final double speedModifier;
    private final PathNavigation navigation;
    private final double stopDistanceSqr;
    private final double startDistanceSqr;
    private final double teleportDistanceSqr;
    private ServerPlayer owner;
    private int repathCooldown;
    private int combatLockTicks;
    private int failedRepathAttempts;
    private int stuckCheckCooldown;
    private double lastOwnerDistanceSqr;
    private float oldWaterCost;

    public FollowRecruitOwnerGoal(FriendlySurvivorEntity survivor, double speedModifier, float stopDistance, float startDistance) {
        this.survivor = survivor;
        this.speedModifier = speedModifier;
        this.navigation = survivor.getNavigation();
        this.stopDistanceSqr = stopDistance * stopDistance;
        this.startDistanceSqr = startDistance * startDistance;
        double teleportDistance = Math.max(12.0D, startDistance + TELEPORT_EXTRA_DISTANCE);
        this.teleportDistanceSqr = teleportDistance * teleportDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.survivor.level().isClientSide) return false;
        if (this.combatLockTicks > 0) {
            this.combatLockTicks--;
            return false;
        }
        if (!(this.survivor.level() instanceof ServerLevel serverLevel)) return false;

        UUID ownerId = this.survivor.getRecruitOwnerUuid().orElse(null);
        if (ownerId == null) return false;

        Player player = serverLevel.getPlayerByUUID(ownerId);
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isSpectator()) return false;
        if (this.survivor.isPassenger()) return false;
        if (shouldYieldToCombat(this.survivor.getTarget(), serverPlayer)) {
            rememberCombat();
            return false;
        }
        if (this.survivor.distanceToSqr(serverPlayer) < this.startDistanceSqr) return false;

        this.owner = serverPlayer;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.owner == null || this.owner.isRemoved() || this.owner.isSpectator()) return false;
        if (this.survivor.isPassenger()) return false;
        if (shouldYieldToCombat(this.survivor.getTarget(), this.owner)) {
            rememberCombat();
            return false;
        }
        return this.survivor.distanceToSqr(this.owner) > this.stopDistanceSqr;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.failedRepathAttempts = 0;
        this.stuckCheckCooldown = STUCK_CHECK_INTERVAL_TICKS;
        this.lastOwnerDistanceSqr = this.owner != null ? this.survivor.distanceToSqr(this.owner) : Double.MAX_VALUE;
        this.oldWaterCost = this.survivor.getPathfindingMalus(PathType.WATER);
        this.survivor.setPathfindingMalus(PathType.WATER, 0.0F);
        clearNonUrgentTarget();
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        this.survivor.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        if (this.owner == null) return;

        clearNonUrgentTarget();
        boolean shouldTeleport = shouldTryTeleportToOwner();
        boolean stuckTeleport = shouldTeleportBecauseStuck();
        if (!shouldTeleport) {
            this.survivor.getLookControl().setLookAt(this.owner, 10.0F, this.survivor.getMaxHeadXRot());
        }

        if (--this.repathCooldown <= 0) {
            this.repathCooldown = 10;
            if (shouldTeleport || stuckTeleport) {
                tryToTeleportToOwner();
            } else if (!this.navigation.moveTo(this.owner, this.speedModifier)) {
                this.failedRepathAttempts++;
            }
        }
    }

    private void clearNonUrgentTarget() {
        LivingEntity target = this.survivor.getTarget();
        if (target != null && !shouldYieldToCombat(target, this.owner)) {
            this.survivor.setTarget(null);
        }
    }

    private boolean shouldYieldToCombat(@Nullable LivingEntity target, @Nullable ServerPlayer owner) {
        if (target == null || !target.isAlive()) return false;
        if (owner == null || owner.isRemoved() || owner.isSpectator()) return false;
        if (target == owner) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;

        double survivorToOwnerSqr = this.survivor.distanceToSqr(owner);
        double survivorToTargetSqr = this.survivor.distanceToSqr(target);
        double ownerToTargetSqr = owner.distanceToSqr(target);

        if (this.survivor.getLastHurtByMob() == target && survivorToTargetSqr <= RECENT_ATTACK_DISTANCE_SQR) {
            return true;
        }
        if (owner.getLastHurtByMob() == target && ownerToTargetSqr <= RECENT_ATTACK_DISTANCE_SQR) {
            return true;
        }
        if (target instanceof Mob targetMob) {
            LivingEntity mobTarget = targetMob.getTarget();
            if (mobTarget == this.survivor || mobTarget == owner) {
                return true;
            }
        }

        if (survivorToTargetSqr <= CLOSE_THREAT_DISTANCE_SQR) return true;
        if (ownerToTargetSqr <= OWNER_THREAT_DISTANCE_SQR) return true;

        return survivorToOwnerSqr <= this.stopDistanceSqr;
    }

    private void rememberCombat() {
        this.combatLockTicks = COMBAT_LOCK_TICKS;
    }

    private boolean shouldTryTeleportToOwner() {
        return this.owner != null && this.survivor.distanceToSqr(this.owner) >= this.teleportDistanceSqr;
    }

    private boolean shouldTeleportBecauseStuck() {
        if (this.owner == null) return false;
        double distanceSqr = this.survivor.distanceToSqr(this.owner);
        if (distanceSqr <= this.startDistanceSqr) {
            this.failedRepathAttempts = 0;
            this.lastOwnerDistanceSqr = distanceSqr;
            return false;
        }

        if (--this.stuckCheckCooldown > 0) {
            return this.failedRepathAttempts >= MAX_FAILED_REPATHS;
        }

        if (this.navigation.isDone()) {
            this.failedRepathAttempts++;
        }
        if (distanceSqr + STUCK_PROGRESS_EPSILON_SQR >= this.lastOwnerDistanceSqr) {
            this.failedRepathAttempts++;
        } else {
            this.failedRepathAttempts = 0;
        }
        this.lastOwnerDistanceSqr = distanceSqr;
        this.stuckCheckCooldown = STUCK_CHECK_INTERVAL_TICKS;

        return this.failedRepathAttempts >= MAX_FAILED_REPATHS;
    }

    private void tryToTeleportToOwner() {
        if (this.owner == null) return;
        BlockPos ownerPos = this.owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int xOffset = this.survivor.getRandom().nextIntBetweenInclusive(-3, 3);
            int zOffset = this.survivor.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(xOffset) < 2 && Math.abs(zOffset) < 2) continue;

            int yOffset = this.survivor.getRandom().nextIntBetweenInclusive(-1, 1);
            if (maybeTeleportTo(ownerPos.getX() + xOffset, ownerPos.getY() + yOffset, ownerPos.getZ() + zOffset)) {
                this.failedRepathAttempts = 0;
                this.lastOwnerDistanceSqr = this.survivor.distanceToSqr(this.owner);
                return;
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!canTeleportTo(pos)) return false;

        this.survivor.moveTo((double) x + 0.5D, y, (double) z + 0.5D, this.survivor.getYRot(), this.survivor.getXRot());
        this.navigation.stop();
        return true;
    }

    private boolean canTeleportTo(BlockPos pos) {
        PathType pathType = WalkNodeEvaluator.getPathTypeStatic(this.survivor, pos);
        if (pathType != PathType.WALKABLE) return false;

        BlockState below = this.survivor.level().getBlockState(pos.below());
        if (below.getBlock() instanceof LeavesBlock) return false;

        BlockPos offset = pos.subtract(this.survivor.blockPosition());
        return this.survivor.level().noCollision(this.survivor, this.survivor.getBoundingBox().move(offset));
    }
}
