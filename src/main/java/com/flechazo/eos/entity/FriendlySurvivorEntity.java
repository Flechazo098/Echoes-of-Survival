package com.flechazo.eos.entity;

import cc.sighs.oelib.data.DataManager;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.config.EosConfigs;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.data.trade.ProfessionDefinition;
import com.flechazo.eos.data.trade.TradePoolDefinition;
import com.flechazo.eos.entity.ai.goal.*;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import com.flechazo.eos.reputation.ReputationApi;
import com.flechazo.eos.reputation.ReputationEventService;
import com.flechazo.eos.reputation.ReputationTiers;
import com.flechazo.eos.squad.SquadApi;
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Attempts;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.Map;

public class FriendlySurvivorEntity extends AbstractSurvivorEntity {
    private static final int INTERACTION_COMBAT_GRACE_TICKS = 100;
    private static final double GUARD_COMBAT_RADIUS = 16.0D;
    private static final double GUARD_COMBAT_RADIUS_SQ = GUARD_COMBAT_RADIUS * GUARD_COMBAT_RADIUS;
    private static final EntityDataAccessor<String> RECRUIT_OWNER_UUID =
            SynchedEntityData.defineId(FriendlySurvivorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PATROL_MODE =
            SynchedEntityData.defineId(FriendlySurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_MODE =
            SynchedEntityData.defineId(FriendlySurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> GUARD_POS =
            SynchedEntityData.defineId(FriendlySurvivorEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private static final String NBT_RECRUIT_OWNER_UUID = "EosRecruitOwnerUuid";
    private static final String NBT_PATROL_MODE = "EosPatrolMode";
    private static final String NBT_ATTACK_MODE = "EosAttackMode";
    private static final String NBT_TRADE_LEDGER = "EosTradeLedger";

    public enum PatrolMode {
        WANDER, FOLLOW, GUARD, SETTLEMENT, WORK, PATROL, REST
    }

    public enum AttackMode {
        PASSIVE, NEUTRAL, AGGRESSIVE, RAID
    }

    private final List<String> offerLedgerKeys = new ArrayList<>();
    private final List<TradePoolDefinition.TradeMode> offerModes = new ArrayList<>();
    private final List<Integer> offerBudgetCosts = new ArrayList<>();
    private final Map<String, Integer> persistentTradeUses = new HashMap<>();
    private int procurementBudget;
    private long lastRestockDay = Long.MIN_VALUE;
    private long lastTradeTrustDay = Long.MIN_VALUE;
    private boolean chargingCrossbow = false;
    private int lastCombatTick = -100000;
    private final FoodData survivorFood = new FoodData();
    @Nullable
    private UUID interactingPlayerId;
    private InteractionLockMode interactionLockMode = InteractionLockMode.NONE;
    private int tradesDuringCurrentSession;

    public FriendlySurvivorEntity(EntityType<? extends AbstractSurvivorEntity> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData groupData) {
        var data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        ensureProfessionAssigned();
        getProfessionId()
                .flatMap(EosDatapackIndex::profession)
                .ifPresent(profession -> {
                    ensureSkinAssigned(profession);
                    applyProfessionEquipment(profession);
                });
        ensureSurvivorProfile(spawnType);
        return data;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RECRUIT_OWNER_UUID, "");
        builder.define(PATROL_MODE, 0);
        builder.define(ATTACK_MODE, AttackMode.NEUTRAL.ordinal());
        builder.define(GUARD_POS, Optional.empty());
    }

    public Optional<BlockPos> getGuardPos() {
        return this.entityData.get(GUARD_POS);
    }

    public void setGuardPos(@Nullable BlockPos pos) {
        this.entityData.set(GUARD_POS, Optional.ofNullable(pos));
    }

    public PatrolMode getPatrolMode() {
        int value = this.entityData.get(PATROL_MODE);
        return value >= 0 && value < PatrolMode.values().length ? PatrolMode.values()[value] : PatrolMode.WANDER;
    }

    public void setPatrolMode(PatrolMode mode) {
        this.entityData.set(PATROL_MODE, mode.ordinal());
        if (isStationaryAssignment(mode)) {
            setGuardPos(this.blockPosition());
        }
        if (mode == PatrolMode.REST) {
            this.setTarget(null);
            this.getNavigation().stop();
        }
    }

    public PatrolMode cyclePatrolMode() {
        PatrolMode[] commands = {PatrolMode.FOLLOW, PatrolMode.GUARD, PatrolMode.SETTLEMENT,
                PatrolMode.WORK, PatrolMode.PATROL, PatrolMode.REST};
        int current = java.util.Arrays.asList(commands).indexOf(getPatrolMode());
        PatrolMode next = commands[(current + 1) % commands.length];
        setPatrolMode(next);
        return next;
    }

    private static boolean isStationaryAssignment(PatrolMode mode) {
        return mode == PatrolMode.GUARD || mode == PatrolMode.SETTLEMENT || mode == PatrolMode.WORK;
    }

    public AttackMode getAttackMode() {
        return AttackMode.values()[this.entityData.get(ATTACK_MODE)];
    }

    public void setAttackMode(AttackMode mode) {
        this.entityData.set(ATTACK_MODE, mode.ordinal());
    }

    public AttackMode cycleAttackMode() {
        AttackMode[] vals = AttackMode.values();
        AttackMode next = vals[(getAttackMode().ordinal() + 1) % vals.length];
        setAttackMode(next);
        return next;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SurvivorUsePotionGoal(this, () -> this.tacticalInventory));
        this.goalSelector.addGoal(2, new ModeAwareFleeGoal());
        this.goalSelector.addGoal(3, new ModeAwareAvoidCreeperGoal());
        this.goalSelector.addGoal(3, new ReturnToGuardGoal());
        this.goalSelector.addGoal(4, new SurvivorLadderClimbGoal(this));
        this.goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(5, new OpenFenceGoal(this, true));
        this.goalSelector.addGoal(6, new SurvivorEatFoodGoal(this, 0.45F, 20 * 6, 20 * 10, 10.0F));
        this.goalSelector.addGoal(8, new FollowRecruitOwnerGoal(this, 1.05, 4.0F, 16.0F));
        this.goalSelector.addGoal(9, new SurvivorWeaponSwitchGoal(this, 4.0));

        this.goalSelector.addGoal(10, new ModeAwareAttackGoal());
        this.goalSelector.addGoal(11, new SurvivorRaiseShieldGoal(this));
        this.goalSelector.addGoal(12, new ModeAwareStrollGoal());

        this.goalSelector.addGoal(13, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(14, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new ModeAwareTargetRaidLiving());
        this.targetSelector.addGoal(3, new ModeAwareTargetPlayers());
        this.targetSelector.addGoal(4, new ModeAwareTargetHostile());
        this.targetSelector.addGoal(5, new ModeAwareTargetNeutral());
        this.targetSelector.addGoal(6, new ModeAwareTargetFriendly());
        this.targetSelector.addGoal(7, new ModeAwareTargetMonsters());
    }

    private class ModeAwareFleeGoal extends Goal {
        private final Goal wrapped = new SurvivorFleeGoal(FriendlySurvivorEntity.this, 0.35F, 0.60F, 15.0F, 1.10, 1.30);

        @Override public boolean canUse() {
            if (isStationaryAssignment(getPatrolMode()) || getPatrolMode() == PatrolMode.REST) return false;
            if (getAttackMode() == AttackMode.RAID || getAttackMode() == AttackMode.AGGRESSIVE) return false;
            return wrapped.canUse();
        }
        @Override public boolean canContinueToUse() { return wrapped.canContinueToUse(); }
        @Override public void start() { wrapped.start(); }
        @Override public void stop() { wrapped.stop(); }
        @Override public void tick() { wrapped.tick(); }
    }

    private class ModeAwareAvoidCreeperGoal extends Goal {
        private final Goal wrapped = new SurvivorAvoidCreeperGoal(FriendlySurvivorEntity.this, 10.0F, 1.10, 1.35);

        @Override public boolean canUse() {
            if (isStationaryAssignment(getPatrolMode()) || getPatrolMode() == PatrolMode.REST) return false;
            return wrapped.canUse();
        }
        @Override public boolean canContinueToUse() { return wrapped.canContinueToUse(); }
        @Override public void start() { wrapped.start(); }
        @Override public void stop() { wrapped.stop(); }
        @Override public void tick() { wrapped.tick(); }
    }

    private class ReturnToGuardGoal extends Goal {
        private static final double RETURN_DIST_SQ = 4.0 * 4.0;
        private static final double TELEPORT_DIST_SQ = 64.0 * 64.0;
        private int repath;

        @Override public boolean canUse() {
            if (!isStationaryAssignment(getPatrolMode())) return false;
            if (getTarget() != null) return false;
            return getGuardPos()
                    .map(pos -> distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > RETURN_DIST_SQ)
                    .orElse(false);
        }
        @Override public boolean canContinueToUse() {
            return getTarget() == null && canUse();
        }
        @Override public void start() { this.repath = 0; }
        @Override public void tick() {
            Optional<BlockPos> guardPos = getGuardPos();
            if (guardPos.isEmpty()) return;
            BlockPos pos = guardPos.get();
            if (distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) >= TELEPORT_DIST_SQ) {
                teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            } else if (--this.repath <= 0) {
                this.repath = 20;
                getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0);
            }
        }
        @Override public void stop() { getNavigation().stop(); }
    }

    private class ModeAwareAttackGoal extends Goal {
        private final Goal crossbow = new SurvivorRangedCrossbowAttackGoal<>(FriendlySurvivorEntity.this, 1.10, 16.0F);
        private final Goal bow = new SurvivorRangedBowAttackGoal<>(FriendlySurvivorEntity.this, 1.10, 20, 16.0F);
        private final Goal melee = new MeleeAttackGoal(FriendlySurvivorEntity.this, 1.15, false);
        private final Goal gun = new SurvivorGunAttackGoal(FriendlySurvivorEntity.this);
        @Nullable
        private Goal active;

        private ModeAwareAttackGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() {
            if (!canFightCurrentTarget()) return false;
            this.active = selectGoal();
            return this.active != null;
        }
        @Override public boolean canContinueToUse() {
            if (!canFightCurrentTarget()) return false;
            return this.active != null && this.active.canContinueToUse();
        }
        @Override public void start() {
            if (this.active != null) {
                this.active.start();
            }
        }
        @Override public void stop() {
            if (this.active != null) {
                this.active.stop();
                this.active = null;
            }
        }
        @Override public boolean requiresUpdateEveryTick() {
            return true;
        }
        @Override public void tick() {
            if (this.active != null) {
                this.active.tick();
            }
        }

        @Nullable
        private Goal selectGoal() {
            if (getMainHandItem().getItem() instanceof com.atsuishio.superbwarfare.item.gun.GunItem) return gun;
            if (crossbow.canUse()) return crossbow;
            if (bow.canUse()) return bow;
            if (melee.canUse()) return melee;
            return null;
        }
    }

    private class ModeAwareStrollGoal extends Goal {
        private final Goal wrapped = new RandomStrollGoal(FriendlySurvivorEntity.this, 0.9);
        @Override public boolean canUse() {
            return (getPatrolMode() == PatrolMode.WANDER || getPatrolMode() == PatrolMode.PATROL) && wrapped.canUse();
        }
        @Override public boolean canContinueToUse() {
            return (getPatrolMode() == PatrolMode.WANDER || getPatrolMode() == PatrolMode.PATROL)
                    && wrapped.canContinueToUse();
        }
        @Override public void start() { wrapped.start(); }
        @Override public void stop() { wrapped.stop(); }
        @Override public void tick() { wrapped.tick(); }
    }

    private class ModeAwareTargetPlayers extends NearestAttackableTargetGoal<Player> {
        ModeAwareTargetPlayers() {
            super(FriendlySurvivorEntity.this, Player.class, 10, true, false,
                    e -> e instanceof ServerPlayer sp
                            && !FriendlySurvivorEntity.this.isRecruitOwner(sp)
                            && FriendlySurvivorEntity.this.canAggressiveTargetPlayer(sp)
                            && FriendlySurvivorEntity.this.isWithinGuardCombatArea(sp));
        }
        @Override public boolean canUse() {
            if (getAttackMode() != AttackMode.AGGRESSIVE) return false;
            return super.canUse();
        }
    }

    private class ModeAwareTargetHostile extends NearestAttackableTargetGoal<HostileSurvivorEntity> {
        ModeAwareTargetHostile() {
            super(FriendlySurvivorEntity.this, HostileSurvivorEntity.class, 5, true, false,
                    target -> target instanceof HostileSurvivorEntity hostile
                            && FriendlySurvivorEntity.this.canTargetHostileSurvivor(hostile)
                            && FriendlySurvivorEntity.this.isWithinGuardCombatArea(hostile));
        }
        @Override public boolean canUse() {
            return getAttackMode() != AttackMode.PASSIVE && getAttackMode() != AttackMode.RAID && super.canUse();
        }
    }

    private class ModeAwareTargetNeutral extends NearestAttackableTargetGoal<NeutralSurvivorEntity> {
        ModeAwareTargetNeutral() {
            super(FriendlySurvivorEntity.this, NeutralSurvivorEntity.class, 5, true, false,
                    target -> target instanceof NeutralSurvivorEntity neutral
                            && FriendlySurvivorEntity.this.canTargetNeutralSurvivor(neutral)
                            && FriendlySurvivorEntity.this.isWithinGuardCombatArea(neutral));
        }
        @Override public boolean canUse() {
            return getAttackMode() != AttackMode.PASSIVE && getAttackMode() != AttackMode.RAID && super.canUse();
        }
    }

    private class ModeAwareTargetFriendly extends NearestAttackableTargetGoal<FriendlySurvivorEntity> {
        ModeAwareTargetFriendly() {
            super(FriendlySurvivorEntity.this, FriendlySurvivorEntity.class, 5, true, false,
                    target -> target instanceof FriendlySurvivorEntity survivor
                            && FriendlySurvivorEntity.this.canTargetFriendlySurvivor(survivor)
                            && FriendlySurvivorEntity.this.isWithinGuardCombatArea(survivor));
        }
        @Override public boolean canUse() {
            return getAttackMode() == AttackMode.AGGRESSIVE && super.canUse();
        }
    }

    private class ModeAwareTargetMonsters extends NearestAttackableTargetGoal<Monster> {
        ModeAwareTargetMonsters() {
            super(FriendlySurvivorEntity.this, Monster.class, 5, true, false,
                    target -> target instanceof Monster monster
                            && FriendlySurvivorEntity.this.canTargetMonster(monster)
                            && FriendlySurvivorEntity.this.isWithinGuardCombatArea(monster));
        }
        @Override public boolean canUse() {
            return getAttackMode() != AttackMode.PASSIVE && getAttackMode() != AttackMode.RAID && super.canUse();
        }
    }

    private class ModeAwareTargetRaidLiving extends NearestAttackableTargetGoal<LivingEntity> {
        ModeAwareTargetRaidLiving() {
            super(FriendlySurvivorEntity.this, LivingEntity.class, 5, true, false,
                    target -> FriendlySurvivorEntity.this.canRaidTarget(target)
                            && FriendlySurvivorEntity.this.isWithinGuardCombatArea(target));
        }
        @Override public boolean canUse() {
            return getAttackMode() == AttackMode.RAID && super.canUse();
        }
    }

    private boolean canFightCurrentTarget() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (isOwnedBy(target)) return false;
        if (!isWithinGuardCombatArea(target)) return false;
        if (this.getLastHurtByMob() == target) return true;
        return canTargetByCurrentAttackMode(target);
    }

    private boolean canAggressiveTargetPlayer(ServerPlayer player) {
        return ReputationTiers.isHostileToPlayer(ReputationApi.get(player));
    }

    private boolean canTargetHostileSurvivor(HostileSurvivorEntity target) {
        return target != null && (getAttackMode() == AttackMode.NEUTRAL || getAttackMode() == AttackMode.AGGRESSIVE);
    }

    private boolean canTargetNeutralSurvivor(NeutralSurvivorEntity target) {
        if (target == null) return false;
        return switch (getAttackMode()) {
            case NEUTRAL -> target.isAngry();
            case AGGRESSIVE -> true;
            default -> false;
        };
    }

    private boolean canTargetFriendlySurvivor(FriendlySurvivorEntity target) {
        return target != null && target != this && getAttackMode() == AttackMode.AGGRESSIVE && !isSameFaction(target);
    }

    private boolean canTargetMonster(Monster target) {
        return target != null && (getAttackMode() == AttackMode.NEUTRAL || getAttackMode() == AttackMode.AGGRESSIVE);
    }

    private boolean canRaidTarget(LivingEntity target) {
        if (target == null || target == this) return false;
        if (target instanceof Player) return false;
        return !isSameFaction(target);
    }

    private boolean canTargetByCurrentAttackMode(LivingEntity target) {
        if (isOwnedBy(target)) return false;
        return switch (getAttackMode()) {
            case PASSIVE -> false;
            case NEUTRAL -> target instanceof HostileSurvivorEntity hostile && canTargetHostileSurvivor(hostile)
                    || target instanceof NeutralSurvivorEntity neutral && canTargetNeutralSurvivor(neutral)
                    || target instanceof Monster monster && canTargetMonster(monster);
            case AGGRESSIVE -> target instanceof ServerPlayer player && canAggressiveTargetPlayer(player)
                    || target instanceof HostileSurvivorEntity hostile && canTargetHostileSurvivor(hostile)
                    || target instanceof NeutralSurvivorEntity neutral && canTargetNeutralSurvivor(neutral)
                    || target instanceof FriendlySurvivorEntity survivor && canTargetFriendlySurvivor(survivor)
                    || target instanceof Monster monster && canTargetMonster(monster);
            case RAID -> canRaidTarget(target);
        };
    }

    private boolean isSameFaction(LivingEntity target) {
        Maybe<UUID> ownerId = getRecruitOwnerUuid();
        if (ownerId.isEmpty() || target == null) return false;
        if (target instanceof FriendlySurvivorEntity survivor) {
            return survivor.getRecruitOwnerUuid().map(ownerId.get()::equals).orElse(false);
        }
        if (target instanceof TamableAnimal tamable) {
            return ownerId.get().equals(tamable.getOwnerUUID());
        }
        return false;
    }

    private boolean isOwnedBy(Entity entity) {
        return entity instanceof Player player && isRecruitOwner(player);
    }

    private boolean isWithinGuardCombatArea(LivingEntity target) {
        if (target == null || !isStationaryAssignment(getPatrolMode())) return true;
        return getGuardPos()
                .map(guardPos -> target.distanceToSqr(guardPos.getX() + 0.5D, guardPos.getY(), guardPos.getZ() + 0.5D) <= GUARD_COMBAT_RADIUS_SQ)
                .orElse(true);
    }

    private void tickGuardCombatLeash() {
        if (!isStationaryAssignment(getPatrolMode())) return;
        LivingEntity target = this.getTarget();
        if (target != null && !isWithinGuardCombatArea(target)) {
            this.setTarget(null);
            this.getNavigation().stop();
        }
    }

    public FoodData getSurvivorFood() {
        return survivorFood;
    }

    public int getLastCombatTick() {
        return lastCombatTick;
    }

    public void markCombat() {
        this.lastCombatTick = this.tickCount;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean res = super.hurt(source, amount);
        if (res) markCombat();
        return res;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isOwnedBy(target)) {
            this.setTarget(null);
            return false;
        }
        boolean res = super.doHurtTarget(target);
        if (res) markCombat();
        return res;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return getPatrolMode() != PatrolMode.REST && !isOwnedBy(target) && super.canAttack(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (isOwnedBy(target)) {
            super.setTarget(null);
            return;
        }
        if (!this.level().isClientSide && target != null) {
            if (SurvivorAiUtil.isLowHp(this, 0.35F) && this.getTarget() != null && target != this.getTarget()) {
                return;
            }
            markCombat();
        }
        super.setTarget(target);
    }

    public Maybe<UUID> getRecruitOwnerUuid() {
        return Maybe.ofNullable(this.entityData.get(RECRUIT_OWNER_UUID))
                .filter(raw -> !raw.isBlank())
                .flatMap(raw -> Attempts.maybe(() -> UUID.fromString(raw)));
    }

    public boolean isRecruited() {
        return getRecruitOwnerUuid().isDefined();
    }

    public boolean isRecruitOwner(Player player) {
        return player != null && getRecruitOwnerUuid().map(player.getUUID()::equals).orElse(false);
    }

    public boolean recruit(ServerPlayer player) {
        if (this.level().isClientSide || player == null || !this.isAlive() || this.isBaby()) return false;
        ReputationEventService.ensureInitialTrust(player, this);

        Maybe<UUID> currentOwner = getRecruitOwnerUuid();
        if (currentOwner.isDefined()) {
            player.displayClientMessage(Component.translatable(
                    currentOwner.get().equals(player.getUUID())
                            ? "message.echoes_of_survival.recruit.already_yours"
                            : "message.echoes_of_survival.recruit.already_recruited"
            ), true);
            return false;
        }

        var recruitment = EosConfigs.SURVIVOR.get();
        if (ReputationEventService.global(player) < recruitment.globalRecruitThreshold()) {
            player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.locked"), true);
            return false;
        }
        if (ReputationEventService.faction(player, getAffiliationId()) < recruitment.factionRecruitThreshold()) {
            player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.faction_locked"), true);
            return false;
        }
        if (ReputationEventService.trust(player, this) < recruitment.personalTrustRecruitThreshold()) {
            player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.trust_locked",
                    recruitment.personalTrustRecruitThreshold()), true);
            return false;
        }
        if (!SquadApi.hasAvailableSlot(player)) {
            player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.squad_full",
                    SquadApi.maxFollowingSurvivors(player)), true);
            return false;
        }

        this.entityData.set(RECRUIT_OWNER_UUID, player.getUUID().toString());
        setPatrolMode(PatrolMode.FOLLOW);
        SquadApi.addMember(player, this.getUUID());
        this.getNavigation().stop();
        this.setTarget(null);
        emitBubbleEvent("interaction", "recruited");
        player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.success", this.getDisplayName()), true);
        return true;
    }

    public boolean dismissRecruitOwner(ServerPlayer player) {
        if (this.level().isClientSide || player == null || !this.isAlive() || this.isBaby()) return false;
        if (!isRecruitOwner(player)) {
            player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.not_yours"), true);
            return false;
        }

        this.entityData.set(RECRUIT_OWNER_UUID, "");
        setPatrolMode(PatrolMode.WANDER);
        SquadApi.removeMember(player, this.getUUID());
        this.getNavigation().stop();
        this.setTarget(null);
        emitBubbleEvent("interaction", "dismissed");
        player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.dismissed", this.getDisplayName()), true);
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getRecruitOwnerUuid().ifPresent(uuid -> tag.putString(NBT_RECRUIT_OWNER_UUID, uuid.toString()));
        tag.putString(NBT_PATROL_MODE, getPatrolMode().name());
        tag.putString(NBT_ATTACK_MODE, getAttackMode().name());
        this.survivorFood.addAdditionalSaveData(tag);
        CompoundTag tradeLedger = new CompoundTag();
        CompoundTag uses = new CompoundTag();
        persistentTradeUses.forEach(uses::putInt);
        tradeLedger.put("uses", uses);
        tradeLedger.putInt("procurement_budget", procurementBudget);
        tradeLedger.putLong("last_restock_day", lastRestockDay);
        tradeLedger.putLong("last_trade_trust_day", lastTradeTrustDay);
        tag.put(NBT_TRADE_LEDGER, tradeLedger);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_RECRUIT_OWNER_UUID)) {
            this.entityData.set(RECRUIT_OWNER_UUID, Maybe.ofNullable(tag.getString(NBT_RECRUIT_OWNER_UUID))
                    .filter(raw -> !raw.isBlank())
                    .flatMap(raw -> Attempts.maybe(() -> UUID.fromString(raw)))
                    .map(UUID::toString)
                    .orElse(""));
        }
        this.survivorFood.readAdditionalSaveData(tag);
        if (tag.contains(NBT_PATROL_MODE, Tag.TAG_STRING)) {
            try { setPatrolMode(PatrolMode.valueOf(tag.getString(NBT_PATROL_MODE))); } catch (Exception ignored) {}
        }
        if (tag.contains(NBT_ATTACK_MODE, Tag.TAG_STRING)) {
            try { setAttackMode(AttackMode.valueOf(tag.getString(NBT_ATTACK_MODE))); } catch (Exception ignored) {}
        }
        if (tag.contains(NBT_TRADE_LEDGER, Tag.TAG_COMPOUND)) {
            CompoundTag tradeLedger = tag.getCompound(NBT_TRADE_LEDGER);
            persistentTradeUses.clear();
            CompoundTag uses = tradeLedger.getCompound("uses");
            uses.getAllKeys().forEach(key -> persistentTradeUses.put(key, Math.max(0, uses.getInt(key))));
            procurementBudget = Math.max(0, tradeLedger.getInt("procurement_budget"));
            lastRestockDay = tradeLedger.getLong("last_restock_day");
            lastTradeTrustDay = tradeLedger.getLong("last_trade_trust_day");
        }
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        Player previous = this.getTradingPlayer();
        super.setTradingPlayer(player);
        if (!this.level().isClientSide && previous != null && player == null) {
            if (this.tradesDuringCurrentSession == 0) emitBubbleEvent("interaction", "trade_failed");
            this.tradesDuringCurrentSession = 0;
        }
        if (!this.level().isClientSide && player != null) {
            this.tradesDuringCurrentSession = 0;
            this.offers = new MerchantOffers();
            clearOfferLedgerView();
            this.updateTrades();
        }
    }

    @Override
    protected void updateTrades() {
        if (this.level().isClientSide) return;

        ensureProfessionAssigned();
        restockEconomyIfDue();

        Player tradingPlayer = getTradingPlayer();
        int reputation = tradingPlayer instanceof ServerPlayer sp ? ReputationApi.get(sp) : 0;
        if (tradingPlayer instanceof ServerPlayer && !ReputationTiers.canTradeFriendly(reputation)) {
            this.offers = new MerchantOffers();
            clearOfferLedgerView();
            return;
        }

        Maybe<ResourceLocation> profIdOpt = getProfessionId();
        if (profIdOpt.isEmpty()) {
            this.offers = new MerchantOffers();
            clearOfferLedgerView();
            return;
        }
        ResourceLocation professionId = profIdOpt.get();

        var profession = EosDatapackIndex.profession(professionId);
        if (profession.isEmpty()) {
            this.offers = new MerchantOffers();
            clearOfferLedgerView();
            return;
        }

        double multiplier = ReputationTiers.priceMultiplier(reputation);

        MerchantOffers nextOffers = new MerchantOffers();
        List<String> nextKeys = new ArrayList<>();
        List<TradePoolDefinition.TradeMode> nextModes = new ArrayList<>();
        List<Integer> nextBudgetCosts = new ArrayList<>();

        List<TradePoolDefinition> pools = EosDatapackIndex.tradePools(professionId, profession.get().logic().tradePools());
        for (TradePoolDefinition pool : pools) {
            for (TradePoolDefinition.Trade trade : pool.trades()) {
                if (trade == null) continue;
                if (tradingPlayer instanceof ServerPlayer sp) {
                    if (!isTradeUnlocked(trade, ReputationApi.get(sp))) continue;
                } else {
                    if (trade.reputationRequirement() > 0) continue;
                }

                ItemStack buy = trade.buy();
                ItemStack sell = trade.sell();
                if (buy.isEmpty() || sell.isEmpty()) continue;
                String ledgerKey = tradeLedgerKey(professionId, buy, sell, trade.mode());
                int used = persistentTradeUses.getOrDefault(ledgerKey, 0);
                if (used >= trade.maxUses()) continue;
                if (trade.mode() == TradePoolDefinition.TradeMode.PROCURE_FROM_PLAYER
                        && procurementBudget < trade.procurementBudgetCost()) continue;

                MerchantOffer offer = new MerchantOffer(
                        itemCost(buy),
                        sell.copy(),
                        trade.maxUses(),
                        0,
                        0.0F
                );
                for (int i = 0; i < used; i++) offer.increaseUses();

                int desiredCount = Math.max(1, (int) Math.ceil(buy.getCount() * multiplier));
                int diff = desiredCount - buy.getCount();
                if (diff != 0) {
                    offer.setSpecialPriceDiff(diff);
                }

                nextOffers.add(offer);
                nextKeys.add(ledgerKey);
                nextModes.add(trade.mode());
                nextBudgetCosts.add(trade.procurementBudgetCost());
            }
        }

        this.offers = nextOffers;
        this.offerLedgerKeys.clear();
        this.offerLedgerKeys.addAll(nextKeys);
        this.offerModes.clear();
        this.offerModes.addAll(nextModes);
        this.offerBudgetCosts.clear();
        this.offerBudgetCosts.addAll(nextBudgetCosts);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!itemstack.is(Items.VILLAGER_SPAWN_EGG) && this.isAlive() && !this.isTrading() && !this.isBaby()) {
            if (hand == InteractionHand.MAIN_HAND) {
                player.awardStat(Stats.TALKED_TO_VILLAGER);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    public void beginMenuInteraction(ServerPlayer player) {
        this.interactingPlayerId = player.getUUID();
        this.interactionLockMode = InteractionLockMode.QUEST_MENU;
        freezeForMenu();
    }

    public void beginOverlayInteraction(ServerPlayer player) {
        ReputationEventService.ensureInitialTrust(player, this);
        this.interactingPlayerId = player.getUUID();
        this.interactionLockMode = InteractionLockMode.OVERLAY;
        if (!isInInteractionCombat()) {
            freezeForMenu();
        }
    }

    public void endMenuInteraction(Player player) {
        if (player != null && player.getUUID().equals(this.interactingPlayerId)) {
            this.interactingPlayerId = null;
            this.interactionLockMode = InteractionLockMode.NONE;
        }
    }

    public boolean openTradeInterface(ServerPlayer player) {
        if (!this.isAlive() || this.isTrading() || this.isBaby()) return false;
        ReputationEventService.ensureInitialTrust(player, this);

        ensureProfessionAssigned();
        int reputation = ReputationApi.get(player);
        OptionalInt minimumReputation = minimumTradeReputation();
        if (!ReputationTiers.canTradeFriendly(reputation)
                || minimumReputation.isPresent() && reputation < minimumReputation.getAsInt()) {
            rejectTradeForReputation(player);
            return false;
        }

        this.getNavigation().stop();
        this.setTarget(null);
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }

        this.setTradingPlayer(player);
        this.offers = new MerchantOffers();
        clearOfferLedgerView();
        this.updateTrades();

        if (this.getOffers().isEmpty()) {
            boolean reputationLocked = isTradeUnavailableDueToReputation(player);
            this.setTradingPlayer(null);
            if (reputationLocked) rejectTradeForReputation(player);
            return false;
        }

        this.openTradingScreen(player, this.getDisplayName(), 1);
        return true;
    }

    private void rejectTradeForReputation(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.echoes_of_survival.trade.locked"), true);
        emitBubbleEvent("interaction", "trade_locked");
    }

    private boolean isTradeUnavailableDueToReputation(ServerPlayer player) {
        int reputation = ReputationApi.get(player);
        if (!ReputationTiers.canTradeFriendly(reputation)) return true;

        OptionalInt minimumReputation = minimumTradeReputation();
        return minimumReputation.isPresent() && reputation < minimumReputation.getAsInt();
    }

    private OptionalInt minimumTradeReputation() {
        Maybe<ResourceLocation> professionId = getProfessionId();
        if (professionId.isEmpty()) return OptionalInt.empty();

        var profession = EosDatapackIndex.profession(professionId.get());
        if (profession.isEmpty()) return OptionalInt.empty();

        int minimum = Integer.MAX_VALUE;
        List<TradePoolDefinition> pools = EosDatapackIndex.tradePools(professionId.get(), profession.get().logic().tradePools());
        for (TradePoolDefinition pool : pools) {
            if (pool == null || pool.trades() == null) continue;
            for (TradePoolDefinition.Trade trade : pool.trades()) {
                if (trade == null || trade.buy() == null || trade.sell() == null) continue;
                if (trade.buy().isEmpty() || trade.sell().isEmpty()) continue;
                minimum = Math.min(minimum, tradeRequiredReputation(trade));
            }
        }
        return minimum == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(minimum);
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        this.tradesDuringCurrentSession++;
        emitBubbleEvent("interaction", "trade_success");
        int index = this.offers == null ? -1 : this.offers.indexOf(offer);
        if (index >= 0 && index < offerLedgerKeys.size()) {
            persistentTradeUses.merge(offerLedgerKeys.get(index), 1, Integer::sum);
            if (offerModes.get(index) == TradePoolDefinition.TradeMode.PROCURE_FROM_PLAYER) {
                procurementBudget = Math.max(0, procurementBudget - offerBudgetCosts.get(index));
            }
        }
        if (this.getTradingPlayer() instanceof ServerPlayer player) {
            // Ordinary trades no longer grant global reputation. The persistent
            // economy ledger awards at most one small daily trust milestone.
            awardDailyTradeTrust(player);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            getRecruitOwnerUuid()
                    .map(uuid -> serverLevel.getServer().getPlayerList().getPlayer(uuid))
                    .ifPresent(owner -> SquadApi.removeMember(owner, this.getUUID()));
        }
        if (!this.level().isClientSide && source.getEntity() instanceof ServerPlayer player) {
            getProfessionId()
                    .flatMap(EosDatapackIndex::profession)
                    .ifPresent(prof -> {
                        int delta = prof.logic().reputationOnDeath();
                        if (delta != 0) {
                            ReputationEventService.apply(player, "kill_friendly_survivor", delta,
                                    getAffiliationId(), delta, getUUID(), -100);
                        }
                    });
        }
    }

    private void awardDailyTradeTrust(ServerPlayer player) {
        long day = this.level().getDayTime() / 24000L;
        if (lastTradeTrustDay == day) return;
        lastTradeTrustDay = day;
        int gain = EosConfigs.ECONOMY.get().dailyTradeTrustGain();
        if (gain > 0) {
            ReputationEventService.apply(player, "daily_trade_milestone", 0,
                    getAffiliationId(), 0, getUUID(), gain);
        }
    }

    private void restockEconomyIfDue() {
        long day = this.level().getDayTime() / 24000L;
        int interval = EosConfigs.ECONOMY.get().restockIntervalDays();
        if (lastRestockDay == Long.MIN_VALUE || day - lastRestockDay >= interval) {
            persistentTradeUses.clear();
            procurementBudget = EosConfigs.ECONOMY.get().dailyProcurementBudget();
            lastRestockDay = day;
        }
    }

    private void clearOfferLedgerView() {
        offerLedgerKeys.clear();
        offerModes.clear();
        offerBudgetCosts.clear();
    }

    private static String tradeLedgerKey(ResourceLocation professionId, ItemStack buy, ItemStack sell,
                                         TradePoolDefinition.TradeMode mode) {
        return professionId + "|" + mode.name() + "|"
                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(buy.getItem()) + "x" + buy.getCount()
                + "|" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sell.getItem()) + "x" + sell.getCount();
    }

    private static ItemCost itemCost(ItemStack stack) {
        return new ItemCost(
                stack.getItemHolder(),
                stack.getCount(),
                DataComponentPredicate.allOf(stack.getComponents())
        );
    }

    private static boolean isTradeUnlocked(TradePoolDefinition.Trade trade, int reputation) {
        return reputation >= tradeRequiredReputation(trade);
    }

    private static int tradeRequiredReputation(TradePoolDefinition.Trade trade) {
        int required = trade.reputationRequirement();
        if (trade.unlockRequirement().isDefined()) {
            required = Math.max(required, trade.unlockRequirement().get().map(
                    v -> v,
                    tierName -> EosDatapackIndex.reputationTierByName(tierName)
                            .map(ReputationTiersDefinition.Tier::min)
                            .orElse(0)
            ));
        }
        return required;
    }

    private void ensureProfessionAssigned() {
        if (this.level().isClientSide) return;
        if (getProfessionId().isDefined()) return;

        List<ProfessionDefinition> defs = DataManager.getDataList(ProfessionDefinition.class);
        if (defs.isEmpty()) return;

        ProfessionDefinition picked = defs.get(this.random.nextInt(defs.size()));
        if (picked != null && picked.id() != null) {
            setProfessionId(picked.id());
        }
    }

    private void ensureSkinAssigned(ProfessionDefinition profession) {
        if (this.level().isClientSide) return;
        if (getSkinUuid().isDefined()) return;
        EosDatapackIndex.pickSkinProfile(this.getUUID()).ifPresent(this::setSkinProfile);
    }

    @Override
    public void setChargingCrossbow(boolean chargingCrossbow) {
        this.chargingCrossbow = chargingCrossbow;
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public void performRangedAttack(net.minecraft.world.entity.LivingEntity target, float distanceFactor) {
        if (this.isHolding(stack -> stack.getItem() instanceof CrossbowItem)) {
            this.performCrossbowAttack(this, 1.6F);
            return;
        }

        var bowHand = ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem);
        ItemStack bow = this.getItemInHand(bowHand);
        if (bow.getItem() instanceof BowItem) {
            ItemStack projectile = new ItemStack(Items.ARROW);
            AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectile, distanceFactor, bow);
            double dx = target.getX() - this.getX();
            double dy = target.getY(0.3333333333333333D) - arrow.getY();
            double dz = target.getZ() - this.getZ();
            double d3 = Math.sqrt(dx * dx + dz * dz);
            arrow.shoot(dx, dy + d3 * 0.2F, dz, 1.6F, (float) (14 - this.level().getDifficulty().getId() * 4));
            this.level().addFreshEntity(arrow);
            bow.hurtAndBreak(1, this, LivingEntity.getSlotForHand(bowHand));
            return;
        }

        var tridentHand = ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof TridentItem);
        ItemStack trident = this.getItemInHand(tridentHand);
        if (trident.getItem() instanceof TridentItem) {
            ItemStack projectile = new ItemStack(Items.TRIDENT);
            AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectile, distanceFactor, trident);
            double dx = target.getX() - this.getX();
            double dy = target.getY(0.3333333333333333D) - arrow.getY();
            double dz = target.getZ() - this.getZ();
            double d3 = Math.sqrt(dx * dx + dz * dz);
            arrow.shoot(dx, dy + d3 * 0.2F, dz, 1.6F, (float) (14 - this.level().getDifficulty().getId() * 4));
            this.level().addFreshEntity(arrow);
            trident.hurtAndBreak(1, this, LivingEntity.getSlotForHand(tridentHand));
        }
    }

    @Override
    public ItemStack getProjectile(ItemStack weaponStack) {
        if (weaponStack != null && !weaponStack.isEmpty() && (weaponStack.getItem() instanceof CrossbowItem || weaponStack.getItem() instanceof BowItem)) {
            return Items.ARROW.getDefaultInstance();
        }
        return super.getProjectile(weaponStack);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.survivorFood.getExhaustionLevel() > 4.0F) {
            this.survivorFood.setExhaustion(this.survivorFood.getExhaustionLevel() - 4.0F);
        }
        if (!this.level().isClientSide) {
            tickGuardCombatLeash();
            tickFriendlyBubbleStatus();
        }
        if (this.level().isClientSide || this.interactingPlayerId == null) return;

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.interactingPlayerId = null;
            return;
        }

        Player player = serverLevel.getPlayerByUUID(this.interactingPlayerId);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            this.interactingPlayerId = null;
            this.interactionLockMode = InteractionLockMode.NONE;
            return;
        }

        if (this.interactionLockMode == InteractionLockMode.OVERLAY) {
            if (serverPlayer.distanceToSqr(this) > 64.0) {
                this.interactingPlayerId = null;
                this.interactionLockMode = InteractionLockMode.NONE;
                return;
            }
            if (isInInteractionCombat()) {
                return;
            }
            freezeForMenu();
            return;
        }

        if (!(serverPlayer.containerMenu instanceof SurvivorQuestMenu)) {
            this.interactingPlayerId = null;
            this.interactionLockMode = InteractionLockMode.NONE;
            return;
        }

        freezeForMenu();
    }

    private void tickFriendlyBubbleStatus() {
        if (this.tickCount % 200 != 0) return;
        if (this.survivorFood.getFoodLevel() <= 6) emitBubbleEvent("environment", "hungry");
        if (this.getHealth() <= this.getMaxHealth() * 0.35F && !hasHealingPotion()) emitBubbleEvent("status", "no_medicine");

        ItemStack weapon = this.getMainHandItem();
        if (weapon.getItem() instanceof com.atsuishio.superbwarfare.item.gun.GunItem) {
            com.atsuishio.superbwarfare.data.gun.GunData gun = com.atsuishio.superbwarfare.data.gun.GunData.from(weapon);
            if (gun.countBackupAmmo(this) <= 0 && !gun.hasEnoughAmmoToShoot(this)) emitBubbleEvent("status", "needs_ammo");
        }
    }

    @Override
    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        super.onEquippedItemBroken(item, slot);
        if (this.level().isClientSide) return;
        switch (slot) {
            case MAINHAND, OFFHAND -> emitBubbleEvent("status", "weapon_broken");
            case HEAD, CHEST, LEGS, FEET -> emitBubbleEvent("status", "armor_broken");
            default -> {
            }
        }
    }

    private boolean hasHealingPotion() {
        for (int i = 0; i < this.tacticalInventory.getSlots(); i++) {
            if (EosDatapackIndex.matches(this.tacticalInventory.getStackInSlot(i))) return true;
        }
        return false;
    }

    private void freezeForMenu() {
        this.getNavigation().stop();
        this.setTarget(null);
        this.setDeltaMovement(Vec3.ZERO);
        this.xxa = 0.0F;
        this.yya = 0.0F;
        this.zza = 0.0F;
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }
    }

    private boolean isInInteractionCombat() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            return true;
        }
        return this.tickCount - this.lastCombatTick <= INTERACTION_COMBAT_GRACE_TICKS;
    }

    private enum InteractionLockMode {
        NONE,
        OVERLAY,
        QUEST_MENU
    }
}
