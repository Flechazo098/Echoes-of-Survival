package com.flechazo.eos.entity.ai.goal;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/**
 * Small ladder/vine climb assist, inspired by HostileHumans.
 * <p>
 * Vanilla path navigation can sometimes "stall" on climbables; this goal adds
 * a bit of Y motion in the direction of the next path node while on climbables.
 * </p>
 */
public class SurvivorLadderClimbGoal extends Goal {
    private final Mob mob;
    private Path path;

    public SurvivorLadderClimbGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() == null) return false;
        if (mob.getNavigation().isDone()) return false;

        this.path = mob.getNavigation().getPath();
        return this.path != null && mob.onClimbable();
    }

    @Override
    public void tick() {
        if (this.path == null) return;

        int i = this.path.getNextNodeIndex();
        if (i + 1 >= this.path.getNodeCount()) return;

        Node current = this.path.getNode(i);
        Node next = this.path.getNode(i + 1);

        int currentY = current.y;
        BlockState down = mob.level().getBlockState(mob.blockPosition().below());

        double yMotion;
        if (next.y < currentY || (next.y == currentY && !down.is(BlockTags.CLIMBABLE))) {
            yMotion = -0.15;
        } else {
            yMotion = 0.15;
        }

        mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, yMotion, 0.0));
    }
}

