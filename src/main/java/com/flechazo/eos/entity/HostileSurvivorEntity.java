package com.flechazo.eos.entity;

import com.flechazo.eos.entity.ai.goal.*;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class HostileSurvivorEntity extends AbstractSurvivorEntity {

    public HostileSurvivorEntity(EntityType<? extends AbstractSurvivorEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SurvivorUsePotionGoal(this, () -> this.tacticalInventory));
        this.goalSelector.addGoal(2, new SurvivorFleeGoal(this, 0.35F, 0.70F, 15.0F, 1.20, 1.45));
        this.goalSelector.addGoal(3, new SurvivorAvoidCreeperGoal(this, 10.0F, 1.15, 1.50));
        this.goalSelector.addGoal(3, new SurvivorLadderClimbGoal(this));
        this.goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new OpenFenceGoal(this, true));
        this.goalSelector.addGoal(7, new SurvivorWeaponSwitchGoal(this, 6.0));
        this.goalSelector.addGoal(8, new SurvivorGunAttackGoal(this));
        this.goalSelector.addGoal(9, new SurvivorRangedCrossbowAttackGoal<>(this, 1.15, 18.0F));
        this.goalSelector.addGoal(9, new SurvivorRangedBowAttackGoal<>(this, 1.15, 20, 18.0F));
        this.goalSelector.addGoal(10, new MeleeAttackGoal(this, 1.25, false));
        this.goalSelector.addGoal(11, new SurvivorRaiseShieldGoal(this));
        this.goalSelector.addGoal(12, new RandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(13, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(14, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, 5, false, false,
                living -> living instanceof Player p && canSeePlayerWellEnough(p)
        ));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, NeutralSurvivorEntity.class, 5, false, false,
                living -> living instanceof NeutralSurvivorEntity
        ));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(
                this, FriendlySurvivorEntity.class, 5, false, false, null
        ));
    }

    private boolean canSeePlayerWellEnough(Player player) {
        if (player == null) return false;
        if (!this.getSensing().hasLineOfSight(player)) return false;

        Vec3 toTarget = player.position().subtract(this.position());
        double distSqr = toTarget.lengthSqr();
        if (distSqr < 4.0) return true;

        Vec3 dir = toTarget.normalize();
        Vec3 look = this.getLookAngle();
        double dot = look.dot(dir);
        if (dot < 0.0 && distSqr > 16.0) return false;
        return !(dot < 0.25) || !(distSqr > 64.0);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
                                        DifficultyInstance difficulty,
                                        MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        var data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        assignRandomProfession(spawnType);
        if (getSkinUuid().isEmpty()) ensureSkinAssigned();
        ensureSurvivorProfile(spawnType);
        return data;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }
}
