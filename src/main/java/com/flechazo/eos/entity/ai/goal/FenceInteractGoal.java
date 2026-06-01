package com.flechazo.eos.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

public abstract class FenceInteractGoal extends Goal {
    protected Mob mob;
    protected BlockPos fencePos = BlockPos.ZERO;
    protected boolean hasFence;
    private boolean passed;
    private float doorOpenDirX;
    private float fenceOpenDirZ;

    public FenceInteractGoal(Mob mob) {
        this.mob = mob;
        if (!GoalUtils.hasGroundPathNavigation(mob)) {
            throw new IllegalArgumentException("Unsupported mob type for FenceInteractGoal");
        }
    }

    public static boolean isFence(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof FenceGateBlock;
    }

    protected boolean isOpen() {
        if (!this.hasFence) return false;
        BlockState state = this.mob.level().getBlockState(this.fencePos);
        if (!(state.getBlock() instanceof FenceGateBlock)) {
            this.hasFence = false;
            return false;
        }
        return state.getValue(FenceGateBlock.OPEN);
    }

    protected void setOpen(boolean open) {
        if (!this.hasFence) return;
        BlockState state = this.mob.level().getBlockState(this.fencePos);
        if (state.getBlock() instanceof FenceGateBlock gate) {
            setOpen(gate, this.mob, this.mob.level(), state, this.fencePos, open);
        }
    }

    public void setOpen(FenceGateBlock gate, @Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (state.is(gate) && state.getValue(FenceGateBlock.OPEN) != open) {
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, open), 10);
            playSound(level, pos, open);
            level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
    }

    private void playSound(Level level, BlockPos pos, boolean open) {
        level.levelEvent(null, open ? 1006 : 1012, pos, 0);
    }

    @Override
    public boolean canUse() {
        if (!GoalUtils.hasGroundPathNavigation(this.mob)) return false;

        GroundPathNavigation nav = (GroundPathNavigation) this.mob.getNavigation();
        Path path = nav.getPath();
        if (path != null && !path.isDone() && nav.canOpenDoors()) {
            for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); ++i) {
                Node node = path.getNode(i);
                this.fencePos = new BlockPos(node.x, node.y + 1, node.z);
                if (this.mob.distanceToSqr(this.fencePos.getX(), this.mob.getY(), this.fencePos.getZ()) <= 2.25D) {
                    this.hasFence = isFence(this.mob.level(), this.fencePos);
                    if (this.hasFence) {
                        return true;
                    }
                }
            }

            this.fencePos = this.mob.blockPosition();
            this.hasFence = isFence(this.mob.level(), this.fencePos);
            return this.hasFence;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.passed;
    }

    @Override
    public void start() {
        this.passed = false;
        this.doorOpenDirX = (float) (this.fencePos.getX() + 0.5D - this.mob.getX());
        this.fenceOpenDirZ = (float) (this.fencePos.getZ() + 0.5D - this.mob.getZ());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        float f = (float) (this.fencePos.getX() + 0.5D - this.mob.getX());
        float f1 = (float) (this.fencePos.getZ() + 0.5D - this.mob.getZ());
        float f2 = this.doorOpenDirX * f + this.fenceOpenDirZ * f1;
        if (f2 < 0.0F) {
            this.passed = true;
        }
    }
}

