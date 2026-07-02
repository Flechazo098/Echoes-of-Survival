package com.flechazo.eos.entity;

import com.flechazo.eos.entity.ai.goal.*;
import com.flechazo.eos.reputation.ReputationApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class NeutralSurvivorEntity extends AbstractSurvivorEntity {

    private static final EntityDataAccessor<Boolean> DATA_ANGRY =
            SynchedEntityData.defineId(NeutralSurvivorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BEGGING =
            SynchedEntityData.defineId(NeutralSurvivorEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String NBT_ANGRY = "EosNeutralAngry";
    private static final String NBT_BEGGING = "EosNeutralBegging";

    private static final double WARN_DISTANCE = 16.0;
    private static final double HOSTILE_DISTANCE = 8.0;
    private static final int HOSTILE_TRIGGER_TICKS = 100;
    private static final double BEG_HP_RATIO = 0.30;
    private static final double BEG_CHANCE = 0.70;

    private int warningTicks;
    @Nullable
    private Player warnedPlayer;
    private int warnMessageCooldown;
    private int lastHurtByPlayerTick = -100000;

    public NeutralSurvivorEntity(EntityType<? extends AbstractSurvivorEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 3.5)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANGRY, false);
        builder.define(DATA_BEGGING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SurvivorFleeGoal(this, 0.35F, 0.60F, 15.0F, 1.10, 1.35));
        this.goalSelector.addGoal(2, new SurvivorAvoidCreeperGoal(this, 10.0F, 1.10, 1.35));
        this.goalSelector.addGoal(3, new SurvivorLadderClimbGoal(this));
        this.goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new OpenFenceGoal(this, true));
        this.goalSelector.addGoal(4, new SurvivorUsePotionGoal(this, () -> this.tacticalInventory));
        this.goalSelector.addGoal(5, new SurvivorWeaponSwitchGoal(this, 6.0));
        this.goalSelector.addGoal(6, new NeutralGunAttackGoal(this));
        this.goalSelector.addGoal(7, new NeutralAttackGoal(this, 1.20, false));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, 10, true, false,
                living -> this.isAngry()
        ));
    }

    public boolean isAngry() {
        return this.entityData.get(DATA_ANGRY);
    }

    public void setAngry(boolean angry) {
        this.entityData.set(DATA_ANGRY, angry);
        if (!angry) this.setTarget(null);
    }

    public boolean isBegging() {
        return this.entityData.get(DATA_BEGGING);
    }

    public void setBegging(boolean begging) {
        this.entityData.set(DATA_BEGGING, begging);
        if (begging) this.setTarget(null);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(NBT_ANGRY, isAngry());
        tag.putBoolean(NBT_BEGGING, isBegging());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAngry(tag.getBoolean(NBT_ANGRY));
        setBegging(tag.getBoolean(NBT_BEGGING));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
                                        DifficultyInstance difficulty,
                                        MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        var data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        assignRandomProfession();
        if (getSkinUuid().isEmpty()) ensureSkinAssigned();
        return data;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (!this.level().isClientSide && target != null && isBegging()) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide) {
            if (source.getEntity() instanceof Player && !isAngry()) {
                this.lastHurtByPlayerTick = this.tickCount;
                setAngry(true);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (isBegging() && !stack.isEmpty()) {
            FoodProperties food = stack.getItem().getFoodProperties(stack, this);
            if (food != null && food.nutrition() > 0) {
                if (!this.level().isClientSide) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    this.heal(food.nutrition() * 2.0F);
                    if (player instanceof ServerPlayer sp) ReputationApi.add(sp, 20);
                    this.discard();
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }
        if (!this.level().isClientSide && isAngry()) return InteractionResult.PASS;
        return InteractionResult.PASS;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;

        Player nearest = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), WARN_DISTANCE,
                e -> e instanceof Player p && !p.isSpectator());
        if (nearest == null || nearest.isCreative() || nearest.isSpectator()) {
            this.warnedPlayer = null;
            this.warningTicks = 0;
            return;
        }

        double distSqr = nearest.distanceToSqr(this);

        if (!isBegging() && !isAngry() && getHealth() < getMaxHealth() * BEG_HP_RATIO) {
            if (this.random.nextDouble() < BEG_CHANCE) {
                setBegging(true);
                showBubble(Component.translatable("entity.echoes_of_survival.neutral_survivor.beg"));
            }
        }
        if (isBegging()) return;

        if (distSqr < WARN_DISTANCE * WARN_DISTANCE) {
            this.warnedPlayer = nearest;
            this.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
            if (warnMessageCooldown <= 0 && !isAngry()) {
                showBubble(Component.translatable("entity.echoes_of_survival.neutral_survivor.warn"));
                warnMessageCooldown = 100;
            }
            if (distSqr < HOSTILE_DISTANCE * HOSTILE_DISTANCE) {
                warningTicks++;
                if (warningTicks >= HOSTILE_TRIGGER_TICKS) {
                    setAngry(true);
                    setTarget(nearest);
                    showBubble(Component.translatable("entity.echoes_of_survival.neutral_survivor.hostile"));
                }
            } else {
                warningTicks = Math.max(0, warningTicks - 2);
            }
        } else {
            this.warnedPlayer = null;
            this.warningTicks = 0;
        }
        if (warnMessageCooldown > 0) warnMessageCooldown--;
    }

    private static class NeutralAttackGoal extends MeleeAttackGoal {
        private final NeutralSurvivorEntity neutral;

        public NeutralAttackGoal(NeutralSurvivorEntity neutral, double speed, boolean followingTargetEvenIfNotSeen) {
            super(neutral, speed, followingTargetEvenIfNotSeen);
            this.neutral = neutral;
        }

        @Override
        public boolean canUse() {
            return neutral.isAngry() && !neutral.isBegging() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return neutral.isAngry() && !neutral.isBegging() && super.canContinueToUse();
        }
    }

    private static class NeutralGunAttackGoal extends Goal {
        private final NeutralSurvivorEntity neutral;
        private final SurvivorGunAttackGoal wrapped;

        private NeutralGunAttackGoal(NeutralSurvivorEntity neutral) {
            this.neutral = neutral;
            this.wrapped = new SurvivorGunAttackGoal(neutral);
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return neutral.isAngry() && !neutral.isBegging() && wrapped.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return neutral.isAngry() && !neutral.isBegging() && wrapped.canContinueToUse();
        }

        @Override
        public void start() {
            wrapped.start();
        }

        @Override
        public void stop() {
            wrapped.stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            wrapped.tick();
        }
    }
}
