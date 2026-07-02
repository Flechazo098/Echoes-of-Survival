package com.flechazo.eos.entity;

import cc.sighs.oelib.data.DataManager;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.data.trade.ProfessionDefinition;
import com.flechazo.eos.data.trade.TradePoolDefinition;
import com.flechazo.eos.entity.ai.goal.*;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import com.flechazo.eos.reputation.ReputationApi;
import com.flechazo.eos.reputation.ReputationTiers;
import net.minecraft.nbt.CompoundTag;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FriendlySurvivorEntity extends AbstractSurvivorEntity {
    private static final int INTERACTION_COMBAT_GRACE_TICKS = 100;
    private static final EntityDataAccessor<String> PROFESSION_ID =
            SynchedEntityData.defineId(FriendlySurvivorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_UUID =
            SynchedEntityData.defineId(FriendlySurvivorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> RECRUIT_OWNER_UUID =
            SynchedEntityData.defineId(FriendlySurvivorEntity.class, EntityDataSerializers.STRING);

    private static final String NBT_PROFESSION_ID = "EosProfessionId";
    private static final String NBT_SKIN_UUID = "EosSkinUuid";
    private static final String NBT_RECRUIT_OWNER_UUID = "EosRecruitOwnerUuid";

    private final List<Integer> offerReputationGains = new ArrayList<>();
    private boolean chargingCrossbow = false;
    private int lastCombatTick = -100000;
    private final FoodData survivorFood = new FoodData();
    @Nullable
    private UUID interactingPlayerId;
    private InteractionLockMode interactionLockMode = InteractionLockMode.NONE;

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
        builder.define(PROFESSION_ID, "");
        builder.define(SKIN_UUID, "");
        builder.define(RECRUIT_OWNER_UUID, "");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SurvivorFleeGoal(this, 0.35F, 0.60F, 15.0F, 1.10, 1.30));
        this.goalSelector.addGoal(2, new SurvivorAvoidCreeperGoal(this, 10.0F, 1.10, 1.35));
        this.goalSelector.addGoal(3, new SurvivorLadderClimbGoal(this));
        this.goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new OpenFenceGoal(this, true));
        this.goalSelector.addGoal(5, new SurvivorEatFoodGoal(this, 0.45F, 20 * 6, 20 * 10, 10.0F));
        this.goalSelector.addGoal(6, new SurvivorUsePotionGoal(this, () -> this.tacticalInventory));
        this.goalSelector.addGoal(7, new FollowRecruitOwnerGoal(this, 1.05, 4.0F, 16.0F));
        this.goalSelector.addGoal(8, new SurvivorWeaponSwitchGoal(this, 4.0));

        this.goalSelector.addGoal(9, new SurvivorRangedCrossbowAttackGoal<>(this, 1.10, 16.0F));
        this.goalSelector.addGoal(9, new SurvivorRangedBowAttackGoal<>(this, 1.10, 20, 16.0F));
        this.goalSelector.addGoal(10, new MeleeAttackGoal(this, 1.15, false));

        this.goalSelector.addGoal(11, new SurvivorRaiseShieldGoal(this));
        this.goalSelector.addGoal(12, new RandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(13, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(14, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));

        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this,
                Player.class,
                10,
                true,
                false,
                living -> {
                    if (!(living instanceof ServerPlayer sp)) return false;
                    if (isRecruitOwner(sp)) return false;
                    int rep = ReputationApi.get(sp);
                    return ReputationTiers.isHostileToPlayer(rep);
                }
        ));

        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, HostileSurvivorEntity.class, 5, false, false, null));

        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(
                this,
                NeutralSurvivorEntity.class,
                5,
                false,
                false,
                living -> living instanceof NeutralSurvivorEntity n && n.isAngry()
        ));

        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Monster.class, 5, false, false, null));
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
        boolean res = super.doHurtTarget(target);
        if (res) markCombat();
        return res;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (!this.level().isClientSide && target != null) {
            if (SurvivorAiUtil.isLowHp(this, 0.35F) && this.getTarget() != null && target != this.getTarget()) {
                return;
            }
            markCombat();
        }
        super.setTarget(target);
    }

    public Optional<ResourceLocation> getProfessionId() {
        String raw = this.entityData.get(PROFESSION_ID);
        if (raw.isBlank()) return Optional.empty();
        return Optional.ofNullable(ResourceLocation.tryParse(raw));
    }

    public void setProfessionId(ResourceLocation id) {
        this.entityData.set(PROFESSION_ID, id.toString());
    }

    public Optional<UUID> getSkinUuid() {
        String raw = this.entityData.get(SKIN_UUID);
        if (raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void setSkinUuid(UUID uuid) {
        this.entityData.set(SKIN_UUID, uuid != null ? uuid.toString() : "");
    }

    public Optional<UUID> getRecruitOwnerUuid() {
        String raw = this.entityData.get(RECRUIT_OWNER_UUID);
        if (raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public boolean isRecruited() {
        return getRecruitOwnerUuid().isPresent();
    }

    public boolean isRecruitOwner(Player player) {
        return player != null && getRecruitOwnerUuid().map(player.getUUID()::equals).orElse(false);
    }

    public boolean recruit(ServerPlayer player) {
        if (this.level().isClientSide || player == null || !this.isAlive() || this.isBaby()) return false;

        Optional<UUID> currentOwner = getRecruitOwnerUuid();
        if (currentOwner.isPresent()) {
            player.displayClientMessage(Component.translatable(
                    currentOwner.get().equals(player.getUUID())
                            ? "message.echoes_of_survival.recruit.already_yours"
                            : "message.echoes_of_survival.recruit.already_recruited"
            ), true);
            return false;
        }

        int reputation = ReputationApi.get(player);
        if (!ReputationTiers.canRecruit(reputation)) {
            player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.locked"), true);
            return false;
        }

        this.entityData.set(RECRUIT_OWNER_UUID, player.getUUID().toString());
        this.getNavigation().stop();
        this.setTarget(null);
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
        this.getNavigation().stop();
        this.setTarget(null);
        player.displayClientMessage(Component.translatable("message.echoes_of_survival.recruit.dismissed", this.getDisplayName()), true);
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getProfessionId().ifPresent(id -> tag.putString(NBT_PROFESSION_ID, id.toString()));
        getSkinUuid().ifPresent(uuid -> tag.putString(NBT_SKIN_UUID, uuid.toString()));
        getRecruitOwnerUuid().ifPresent(uuid -> tag.putString(NBT_RECRUIT_OWNER_UUID, uuid.toString()));
        this.survivorFood.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_PROFESSION_ID)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(NBT_PROFESSION_ID));
            if (id != null) {
                setProfessionId(id);
            }
        }
        if (tag.contains(NBT_SKIN_UUID)) {
            try {
                setSkinUuid(UUID.fromString(tag.getString(NBT_SKIN_UUID)));
            } catch (Exception ignored) {
            }
        }
        if (tag.contains(NBT_RECRUIT_OWNER_UUID)) {
            try {
                this.entityData.set(RECRUIT_OWNER_UUID, UUID.fromString(tag.getString(NBT_RECRUIT_OWNER_UUID)).toString());
            } catch (Exception ignored) {
                this.entityData.set(RECRUIT_OWNER_UUID, "");
            }
        }
        this.survivorFood.readAdditionalSaveData(tag);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        super.setTradingPlayer(player);
        if (!this.level().isClientSide && player != null) {
            this.offers = new MerchantOffers();
            this.offerReputationGains.clear();
            this.updateTrades();
        }
    }

    @Override
    protected void updateTrades() {
        if (this.level().isClientSide) return;

        ensureProfessionAssigned();

        Player tradingPlayer = getTradingPlayer();
        int reputation = tradingPlayer instanceof ServerPlayer sp ? ReputationApi.get(sp) : 0;
        if (tradingPlayer instanceof ServerPlayer && !ReputationTiers.canTradeFriendly(reputation)) {
            this.offers = new MerchantOffers();
            this.offerReputationGains.clear();
            return;
        }

        Optional<ResourceLocation> profIdOpt = getProfessionId();
        if (profIdOpt.isEmpty()) {
            this.offers = new MerchantOffers();
            this.offerReputationGains.clear();
            return;
        }
        ResourceLocation professionId = profIdOpt.get();

        ProfessionDefinition profession = EosDatapackIndex.profession(professionId).orElse(null);
        if (profession == null) {
            this.offers = new MerchantOffers();
            this.offerReputationGains.clear();
            return;
        }

        double multiplier = ReputationTiers.priceMultiplier(reputation);

        MerchantOffers nextOffers = new MerchantOffers();
        List<Integer> nextRep = new ArrayList<>();

        List<TradePoolDefinition> pools = EosDatapackIndex.tradePools(professionId, profession.logic().tradePools());
        for (TradePoolDefinition pool : pools) {
            for (TradePoolDefinition.Trade trade : pool.trades()) {
                if (trade == null) continue;
                if (tradingPlayer instanceof ServerPlayer sp) {
                    if (!isTradeUnlocked(trade, ReputationApi.get(sp))) continue;
                } else {
                    if (trade.reputationRequirement() > 0) continue;
                }

                ItemStack buy = trade.buy().toStack();
                ItemStack sell = trade.sell().toStack();
                if (buy.isEmpty() || sell.isEmpty()) continue;

                MerchantOffer offer = new MerchantOffer(
                        new ItemCost(buy.getItem(), buy.getCount()),
                        sell,
                        trade.maxUses(),
                        0,
                        0.0F
                );

                int desiredCount = Math.max(1, (int) Math.ceil(buy.getCount() * multiplier));
                int diff = desiredCount - buy.getCount();
                if (diff != 0) {
                    offer.setSpecialPriceDiff(diff);
                }

                nextOffers.add(offer);
                nextRep.add(trade.reputation());
            }
        }

        this.offers = nextOffers;
        this.offerReputationGains.clear();
        this.offerReputationGains.addAll(nextRep);
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

        this.getNavigation().stop();
        this.setTarget(null);
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }

        this.setTradingPlayer(player);
        this.offers = new MerchantOffers();
        this.offerReputationGains.clear();
        this.updateTrades();

        if (this.getOffers().isEmpty()) {
            if (isTradeUnavailableDueToReputation(player)) {
                player.displayClientMessage(Component.translatable("message.echoes_of_survival.trade.locked"), true);
            }
            this.setTradingPlayer(null);
            return false;
        }

        this.openTradingScreen(player, this.getDisplayName(), 1);
        return true;
    }

    private boolean isTradeUnavailableDueToReputation(ServerPlayer player) {
        int reputation = ReputationApi.get(player);
        if (!ReputationTiers.canTradeFriendly(reputation)) return true;

        ResourceLocation professionId = getProfessionId().orElse(null);
        if (professionId == null) return false;

        ProfessionDefinition profession = EosDatapackIndex.profession(professionId).orElse(null);
        if (profession == null) return false;

        boolean hasLockedTrade = false;
        List<TradePoolDefinition> pools = EosDatapackIndex.tradePools(professionId, profession.logic().tradePools());
        for (TradePoolDefinition pool : pools) {
            if (pool == null || pool.trades() == null) continue;
            for (TradePoolDefinition.Trade trade : pool.trades()) {
                if (trade == null || trade.buy() == null || trade.sell() == null) continue;
                if (trade.buy().toStack().isEmpty() || trade.sell().toStack().isEmpty()) continue;
                if (isTradeUnlocked(trade, reputation)) return false;
                hasLockedTrade = true;
            }
        }
        return hasLockedTrade;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        if (this.getTradingPlayer() instanceof ServerPlayer player) {
            int rep = reputationGainForOffer(offer);
            if (rep != 0) {
                ReputationApi.add(player, rep);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide && source.getEntity() instanceof ServerPlayer player) {
            getProfessionId()
                    .flatMap(EosDatapackIndex::profession)
                    .ifPresent(prof -> {
                        int delta = prof.logic().reputationOnDeath();
                        if (delta != 0) {
                            ReputationApi.add(player, delta);
                        }
                    });
        }
    }

    private int reputationGainForOffer(MerchantOffer offer) {
        if (offer == null || this.offers == null) return 0;
        int idx = this.offers.indexOf(offer);
        if (idx < 0 || idx >= offerReputationGains.size()) return 0;
        return offerReputationGains.get(idx);
    }

    private static boolean isTradeUnlocked(TradePoolDefinition.Trade trade, int reputation) {
        int required = trade.reputationRequirement();
        if (trade.unlockCondition().isPresent()) {
            required = Math.max(required, trade.unlockCondition().get().map(
                    v -> v,
                    tierName -> EosDatapackIndex.reputationTierByName(tierName)
                            .map(ReputationTiersDefinition.Tier::min)
                            .orElse(0)
            ));
        }
        return reputation >= required;
    }

    private void ensureProfessionAssigned() {
        if (this.level().isClientSide) return;
        if (getProfessionId().isPresent()) return;

        List<ProfessionDefinition> defs = DataManager.getDataList(ProfessionDefinition.class);
        if (defs.isEmpty()) return;

        ProfessionDefinition picked = defs.get(this.random.nextInt(defs.size()));
        if (picked != null && picked.id() != null) {
            setProfessionId(picked.id());
        }
    }

    private void ensureSkinAssigned(ProfessionDefinition profession) {
        if (this.level().isClientSide) return;
        if (getSkinUuid().isPresent()) return;

        if (profession != null && profession.skin() != null && profession.skin().isPresent()) {
            return;
        }

        List<UUID> pool = EosDatapackIndex.skinLibraryUuids();
        if (pool.isEmpty()) return;

        int idx = Math.floorMod(this.getUUID().hashCode(), pool.size());
        UUID picked = pool.get(idx);
        if (picked != null) setSkinUuid(picked);
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
