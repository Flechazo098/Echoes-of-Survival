package com.flechazo.eos.entity.ai.goal;

import net.minecraft.world.entity.Mob;

public class OpenFenceGoal extends FenceInteractGoal {
    private final boolean closeFence;
    private int forgetTime;

    public OpenFenceGoal(Mob mob, boolean closeFence) {
        super(mob);
        this.closeFence = closeFence;
    }

    @Override
    public boolean canContinueToUse() {
        return this.closeFence && this.forgetTime > 0 && super.canContinueToUse();
    }

    @Override
    public void start() {
        this.forgetTime = 20;
        this.setOpen(true);
    }

    @Override
    public void stop() {
        this.setOpen(false);
    }

    @Override
    public void tick() {
        --this.forgetTime;
        super.tick();
    }
}

