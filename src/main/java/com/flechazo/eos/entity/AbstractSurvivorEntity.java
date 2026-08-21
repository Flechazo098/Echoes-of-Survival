package com.flechazo.eos.entity;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.config.EosConfigs;
import com.flechazo.eos.data.bubble.SurvivorBubbleDefinition;
import com.flechazo.eos.data.trade.ProfessionDefinition;
import com.flechazo.eos.entity.ai.goal.SurvivorAiUtil;
import com.flechazo.eos.profile.SurvivorAffiliations;
import com.flechazo.eos.profile.SurvivorProfile;
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Attempts;
import com.mrbysco.nbt.network.message.AddBubblePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractSurvivorEntity extends WanderingTrader
        implements RangedAttackMob, CrossbowAttackMob {

    private static final EntityDataAccessor<String> SKIN_UUID =
            SynchedEntityData.defineId(AbstractSurvivorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_USERNAME =
            SynchedEntityData.defineId(AbstractSurvivorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PROFESSION_ID =
            SynchedEntityData.defineId(AbstractSurvivorEntity.class, EntityDataSerializers.STRING);

    protected static final String NBT_SKIN_UUID = "EosSkinUuid";
    protected static final String NBT_SKIN_USERNAME = "EosSkinUsername";
    protected static final String NBT_PROFESSION_ID = "EosProfessionId";
    private static final String NBT_TACTICAL = "EosTactical";
    private static final ResourceLocation PROFILE_ATTACK_MODIFIER = modifierId("profile_attack_damage");
    private static final ResourceLocation PROFILE_HEALTH_MODIFIER = modifierId("profile_max_health");
    private static final ResourceLocation PROFILE_SPEED_MODIFIER = modifierId("profile_movement_speed");
    private static final ResourceLocation PROFILE_KNOCKBACK_MODIFIER = modifierId("profile_knockback_resistance");

    protected boolean chargingCrossbow = false;
    protected int lastCombatTick = -100000;
    @Nullable private LivingEntity bubbleTrackedTarget;
    private int bubbleLastTargetSeenTick = -100000;
    private final Map<String, Integer> bubbleCooldowns = new HashMap<>();
    @Nullable private SurvivorProfile survivorProfile;

    public final ItemStackHandler tacticalInventory = new ItemStackHandler(10);

    protected AbstractSurvivorEntity(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_UUID, "");
        builder.define(SKIN_USERNAME, "");
        builder.define(PROFESSION_ID, "");
    }


    public Maybe<UUID> getSkinUuid() {
        return Maybe.ofNullable(this.entityData.get(SKIN_UUID))
                .filter(raw -> !raw.isBlank())
                .flatMap(raw -> Attempts.maybe(() -> UUID.fromString(raw)));
    }

    public void setSkinUuid(@Nullable UUID uuid) {
        this.entityData.set(SKIN_UUID, uuid != null ? uuid.toString() : "");
        if (uuid != null) {
            EosDatapackIndex.skinLibraryUsername(uuid).ifPresent(this::setSkinUsername);
        }
        updateProfileSkin();
    }

    public Maybe<String> getSkinUsername() {
        return Maybe.ofNullable(this.entityData.get(SKIN_USERNAME)).filter(raw -> !raw.isBlank());
    }

    public void setSkinUsername(@Nullable String username) {
        this.entityData.set(SKIN_USERNAME, username != null ? username.trim() : "");
        updateProfileSkin();
    }

    public void setSkinProfile(@Nullable EosDatapackIndex.SkinProfile profile) {
        if (profile == null) {
            setSkinUuid(null);
            setSkinUsername(null);
            return;
        }
        setSkinUuid(profile.uuid().fold(() -> null, uuid -> uuid));
        setSkinUsername(profile.name());
    }

    protected void ensureSkinUsernameAssigned() {
        if (this.level().isClientSide) return;
        if (getSkinUsername().isDefined()) return;
        getSkinUuid()
                .flatMap(EosDatapackIndex::skinLibraryUsername)
                .ifPresent(this::setSkinUsername);
    }

    protected void ensureSkinAssigned() {
        if (this.level().isClientSide) return;
        if (getSkinUuid().isDefined()) return;
        EosDatapackIndex.pickSkinProfile(this.getUUID()).ifPresent(this::setSkinProfile);
    }

    public Maybe<ResourceLocation> getProfessionId() {
        return Maybe.ofNullable(this.entityData.get(PROFESSION_ID))
                .filter(raw -> !raw.isBlank())
                .flatMap(raw -> Maybe.ofNullable(ResourceLocation.tryParse(raw)));
    }

    public void setProfessionId(ResourceLocation id) {
        this.entityData.set(PROFESSION_ID, id != null ? id.toString() : "");
        if (this.survivorProfile != null) {
            this.survivorProfile = this.survivorProfile.withProfession(id);
        }
    }

    public Maybe<SurvivorProfile> getSurvivorProfile() {
        return Maybe.ofNullable(this.survivorProfile);
    }

    public ResourceLocation getAffiliationId() {
        if (this.survivorProfile != null) return this.survivorProfile.affiliationId();
        if (this instanceof HostileSurvivorEntity) return SurvivorAffiliations.RAIDER;
        return SurvivorAffiliations.INDEPENDENT;
    }

    public SurvivorProfile.Temperament getTemperament() {
        return this.survivorProfile == null
                ? SurvivorProfile.Temperament.CAUTIOUS
                : this.survivorProfile.temperament();
    }

    public boolean hasSpecialty(SurvivorProfile.Specialty specialty) {
        return this.survivorProfile != null && this.survivorProfile.specialties().contains(specialty);
    }

    public float retreatHealthThreshold(float baseThreshold) {
        EosConfigs.TraitConfig config = EosConfigs.TRAITS.get();
        double adjusted = switch (getTemperament()) {
            case CAUTIOUS -> baseThreshold + config.cautiousRetreatThresholdBonus();
            case TIMID -> baseThreshold + config.timidRetreatThresholdBonus();
            case BRAVE -> baseThreshold - config.braveRetreatThresholdReduction();
            default -> baseThreshold;
        };
        return (float) Math.clamp(adjusted, 0.0, 1.0);
    }

    public double fleeSpeedMultiplier() {
        return getTemperament() == SurvivorProfile.Temperament.TIMID ? 1.05D : 1.0D;
    }

    protected void ensureSurvivorProfile(MobSpawnType spawnType) {
        if (this.level().isClientSide || this.survivorProfile != null) return;
        ResourceLocation dimension = this.level().dimension().location();
        this.survivorProfile = SurvivorProfile.create(
                this.getUUID(),
                getSkinUuid().fold(() -> null, uuid -> uuid),
                getSkinUsername().orElse(""),
                getAffiliationId(),
                getProfessionId().fold(() -> null, id -> id),
                spawnType,
                this.level().getGameTime(),
                dimension,
                this.blockPosition()
        );
        applyProfileModifiers();
    }

    private void updateProfileSkin() {
        if (this.survivorProfile == null) return;
        this.survivorProfile = this.survivorProfile.withSkin(
                getSkinUuid().fold(() -> null, uuid -> uuid),
                getSkinUsername().orElse("")
        );
    }

    @Override
    public Component getDisplayName() {
        Component name = getSurvivorProfile()
                .<Component>map(profile -> Component.literal(profile.name()))
                .orElseGet(() -> getSkinUsername()
                .<Component>map(Component::literal)
                .orElseGet(super::getDisplayName));
        Maybe<ResourceLocation> professionId = getProfessionId();
        if (professionId.isEmpty()) {
            return name;
        }
        return Component.translatableWithFallback(
                "entity.echoes_of_survival.survivor.with_profession",
                "%s (%s)",
                name,
                professionName(professionId.get())
        );
    }

    protected Component professionName(ResourceLocation professionId) {
        return Component.translatableWithFallback(
                "profession." + professionId.getNamespace() + "." + professionId.getPath(),
                professionId.getPath()
        );
    }

    protected void assignRandomProfession() {
        assignRandomProfession(null);
    }

    protected void assignRandomProfession(@Nullable MobSpawnType spawnType) {
        String encounter = spawnType == null ? null : switch (spawnType) {
            case STRUCTURE -> "structure";
            case EVENT, TRIGGERED -> "event";
            default -> "wilderness";
        };
        var prof = EosDatapackIndex.randomProfession(
                new java.util.Random(this.random.nextLong()), encounter, getAffiliationId());
        prof.ifPresent(p -> {
            setProfessionId(p.id());
            applyProfessionEquipment(p);
        });
    }

    protected void ensureSkinAssigned(@Nullable Object professionOrNull) {
        if (getSkinUuid().isDefined()) return;
        if (professionOrNull instanceof ProfessionDefinition prof && prof.skinLibrary().isDefined()) return;
        ensureSkinAssigned();
    }


    public int getLastCombatTick() {
        return lastCombatTick;
    }

    protected void markCombat() {
        this.lastCombatTick = this.tickCount;
    }

    @Override
    public boolean hurt(DamageSource s, float a) {
        boolean r = super.hurt(s, a);
        if (r) {
            markCombat();
            emitBubbleEvent("combat", "hurt");
        }
        return r;
    }

    @Override
    public boolean doHurtTarget(Entity t) {
        boolean r = super.doHurtTarget(t);
        if (r) {
            markCombat();
            this.getMainHandItem().hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
        }
        return r;
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damageAmount) {
        this.doHurtEquipment(
                damageSource,
                damageAmount,
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD
        );
    }

    @Override
    protected void hurtHelmet(DamageSource damageSource, float damageAmount) {
        this.doHurtEquipment(damageSource, damageAmount, EquipmentSlot.HEAD);
    }

    @Override
    protected void hurtCurrentlyUsedShield(float damageAmount) {
        ItemStack shield = this.getUseItem();
        if (!shield.canPerformAction(ItemAbilities.SHIELD_BLOCK) || damageAmount < 3.0F) return;

        InteractionHand hand = this.getUsedItemHand();
        EquipmentSlot slot = LivingEntity.getSlotForHand(hand);
        shield.hurtAndBreak(1 + Mth.floor(damageAmount), this, slot);
        if (shield.isEmpty()) {
            this.setItemSlot(slot, ItemStack.EMPTY);
            this.stopUsingItem();
            this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.random.nextFloat() * 0.4F);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        for (int slot = 0; slot < this.tacticalInventory.getSlots(); slot++) {
            ItemStack stack = this.tacticalInventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            this.spawnAtLocation(stack.copy());
            this.tacticalInventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected float getEquipmentDropChance(EquipmentSlot slot) {
        return this.getItemBySlot(slot).isEmpty() ? 0.0F : 2.0F;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (!this.level().isClientSide && target != null) {
            if (SurvivorAiUtil.isLowHp(this, 0.35F) && this.getTarget() != null && target != this.getTarget()) return;
            markCombat();
        }
        super.setTarget(target);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getSkinUuid().ifPresent(uuid -> tag.putString(NBT_SKIN_UUID, uuid.toString()));
        getSkinUsername().ifPresent(username -> tag.putString(NBT_SKIN_USERNAME, username));
        getProfessionId().ifPresent(id -> tag.putString(NBT_PROFESSION_ID, id.toString()));
        tag.put(NBT_TACTICAL, tacticalInventory.serializeNBT(this.registryAccess()));
        if (this.survivorProfile != null) {
            tag.put(SurvivorProfile.NBT_KEY, this.survivorProfile.save());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_SKIN_UUID)) {
            Maybe.ofNullable(tag.getString(NBT_SKIN_UUID))
                    .filter(raw -> !raw.isBlank())
                    .flatMap(raw -> Attempts.maybe(() -> UUID.fromString(raw)))
                    .ifPresent(this::setSkinUuid);
        }
        if (tag.contains(NBT_SKIN_USERNAME)) {
            setSkinUsername(tag.getString(NBT_SKIN_USERNAME));
        }
        if (tag.contains(NBT_PROFESSION_ID)) {
            Maybe.ofNullable(tag.getString(NBT_PROFESSION_ID))
                    .filter(raw -> !raw.isBlank())
                    .flatMap(raw -> Maybe.ofNullable(ResourceLocation.tryParse(raw)))
                    .ifPresent(this::setProfessionId);
        }
        if (tag.contains(NBT_TACTICAL, Tag.TAG_COMPOUND)) {
            tacticalInventory.deserializeNBT(this.registryAccess(), tag.getCompound(NBT_TACTICAL));
            if (tacticalInventory.getSlots() < 10) {
                tacticalInventory.setSize(10);
            }
        }
        SurvivorProfile.load(tag).ifPresent(profile -> {
            this.survivorProfile = profile;
            if (profile.skinUuid() != null) this.entityData.set(SKIN_UUID, profile.skinUuid().toString());
            if (!profile.skinName().isBlank()) this.entityData.set(SKIN_USERNAME, profile.skinName());
            if (profile.professionId() != null) this.entityData.set(PROFESSION_ID, profile.professionId().toString());
            applyProfileModifiers();
        });
    }

    protected void applyArmorSet(ResourceLocation armorSetId) {
        EosDatapackIndex.armorSet(armorSetId).ifPresent(def -> {
            if (def.set() == null || def.set().isEmpty()) return;
            var variants = new ArrayList<>(def.set().values());
            var chosen = variants.get(this.random.nextInt(variants.size()));
            for (var entry : chosen.slots().entrySet()) {
                BuiltInRegistries.ITEM
                        .getOptional(entry.getValue())
                        .ifPresent(item -> this.setItemSlot(entry.getKey(), new ItemStack(item)));
            }
        });
    }

    protected void applyProfessionEquipment(ProfessionDefinition prof) {
        prof.initialEquipment().armorSetId().ifPresent(this::applyArmorSet);
        for (ItemStack stack : prof.initialEquipment().tacticalItems()) {
            if (stack.isEmpty()) continue;
            var copy = stack.copy();
            for (int i = 0; i < tacticalInventory.getSlots(); i++) {
                if (tacticalInventory.getStackInSlot(i).isEmpty()) {
                    tacticalInventory.setStackInSlot(i, copy);
                    break;
                }
            }
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distFactor) {
        if (this.isHolding(s -> s.getItem() instanceof CrossbowItem)) {
            this.performCrossbowAttack(this, 1.6F);
            return;
        }
        var bowHand = ProjectileUtil.getWeaponHoldingHand(this, i -> i instanceof BowItem);
        ItemStack bow = this.getItemInHand(bowHand);
        if (bow.getItem() instanceof BowItem) {
            fireArrow(target, distFactor, bow, bowHand);
            return;
        }
        var tridentHand = ProjectileUtil.getWeaponHoldingHand(this, i -> i instanceof TridentItem);
        ItemStack trident = this.getItemInHand(tridentHand);
        if (trident.getItem() instanceof TridentItem) {
            fireArrow(target, distFactor, trident, tridentHand);
        }
    }

    private void fireArrow(LivingEntity target, float distFactor, ItemStack weapon, InteractionHand hand) {
        var proj = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, proj, distFactor, weapon);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double d3 = Math.sqrt(dx * dx + dz * dz);
        float inaccuracy = (float) (14 - this.level().getDifficulty().getId() * 4);
        if (hasSpecialty(SurvivorProfile.Specialty.STEADY_HAND)) inaccuracy *= 0.90F;
        arrow.shoot(dx, dy + d3 * 0.2F, dz, 1.6F, inaccuracy);
        this.level().addFreshEntity(arrow);
        weapon.hurtAndBreak(1, this, LivingEntity.getSlotForHand(hand));
    }

    @Override
    public ItemStack getProjectile(ItemStack ws) {
        if (!ws.isEmpty() && (ws.getItem() instanceof CrossbowItem || ws.getItem() instanceof BowItem))
            return Items.ARROW.getDefaultInstance();
        return super.getProjectile(ws);
    }

    @Override
    public void setChargingCrossbow(boolean c) {
        this.chargingCrossbow = c;
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    private int shieldActiveTicks = 0;

    protected void showBubble(Component message) {
        showBubble(message, 200);
    }

    protected void showBubble(Component message, int duration) {
        String author = this.getUUID() + "_" + this.tickCount;
        var payload = new AddBubblePayload(
                this.getUUID(), author, message.getString(), duration);
        for (var player : this.level().getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(100))) {
            player.connection.send(payload);
        }
    }

    public void emitBubbleEvent(String category, String event) {
        if (this.level().isClientSide || category == null || event == null) return;
        if (("interaction".equals(category) || "status".equals(category)) && !(this instanceof FriendlySurvivorEntity)) return;
        SurvivorBubbleDefinition.SurvivorType survivorType = bubbleSurvivorType();
        String id = survivorType.serializedName() + "." + category + "." + event;
        if (this.tickCount < this.bubbleCooldowns.getOrDefault(id, 0)) return;
        EosDatapackIndex.survivorBubble(survivorType, category, event).ifPresent(entry -> {
            if (this.random.nextDouble() > entry.chance()) return;
            String key = entry.keys().get(this.random.nextInt(entry.keys().size()));
            showBubble(Component.translatable(key), entry.duration());
            this.bubbleCooldowns.put(id, this.tickCount + entry.cooldown());
        });
    }

    private SurvivorBubbleDefinition.SurvivorType bubbleSurvivorType() {
        if (this instanceof FriendlySurvivorEntity) return SurvivorBubbleDefinition.SurvivorType.FRIENDLY;
        if (this instanceof HostileSurvivorEntity) return SurvivorBubbleDefinition.SurvivorType.HOSTILE;
        return SurvivorBubbleDefinition.SurvivorType.NEUTRAL;
    }
    private void tickBubbleEvents() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            if (target != this.bubbleTrackedTarget) emitBubbleEvent("combat", "enemy_spotted");
            this.bubbleTrackedTarget = target;
            this.bubbleLastTargetSeenTick = this.tickCount;
        } else if (this.bubbleTrackedTarget != null && this.tickCount - this.bubbleLastTargetSeenTick > 60) {
            emitBubbleEvent("combat", "target_lost");
            this.bubbleTrackedTarget = null;
        }
        if (this.tickCount % 200 != 0) return;
        if (this.level().isNight()) emitBubbleEvent("environment", "night");
        if (this.level().isRainingAt(this.blockPosition())) emitBubbleEvent("environment", "rain");
        if (this.getHealth() <= this.getMaxHealth() * 0.30F) emitBubbleEvent("environment", "low_health");
        if (this.getNavigation().isInProgress() && this.getTarget() == null) emitBubbleEvent("environment", "patrol");
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            ensureSurvivorProfile(null);
            ensureSkinUsernameAssigned();
            tickReactiveShield();
            tickBubbleEvents();
        }
    }

    private void applyProfileModifiers() {
        if (this.level().isClientSide || this.survivorProfile == null) return;
        var config = EosConfigs.TRAITS.get();
        double attackBonus = this.survivorProfile.temperament() == SurvivorProfile.Temperament.AGGRESSIVE
                ? config.aggressiveAttackDamageBonus() : 0.0D;
        double healthBonus = hasSpecialty(SurvivorProfile.Specialty.TOUGH) ? config.toughMaxHealthBonus() : 0.0D;
        double speedBonus = hasSpecialty(SurvivorProfile.Specialty.AGILE) ? config.agileMovementSpeedBonus() : 0.0D;
        double knockbackBonus = 0.0D;
        if (this.survivorProfile.temperament() == SurvivorProfile.Temperament.BRAVE) {
            knockbackBonus += config.braveKnockbackResistanceBonus();
        }
        if (hasSpecialty(SurvivorProfile.Specialty.VETERAN)) {
            knockbackBonus += config.veteranKnockbackResistanceBonus();
        }
        replaceModifier(Attributes.ATTACK_DAMAGE, PROFILE_ATTACK_MODIFIER, attackBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        replaceModifier(Attributes.MAX_HEALTH, PROFILE_HEALTH_MODIFIER, healthBonus, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(Attributes.MOVEMENT_SPEED, PROFILE_SPEED_MODIFIER, speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        replaceModifier(Attributes.KNOCKBACK_RESISTANCE, PROFILE_KNOCKBACK_MODIFIER, knockbackBonus, AttributeModifier.Operation.ADD_VALUE);
        if (this.getHealth() > this.getMaxHealth()) this.setHealth(this.getMaxHealth());
    }

    private void replaceModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                 ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        var instance = this.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0.0D) instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(com.flechazo.eos.EchoesofSurvival.MODID, path);
    }

    private void tickReactiveShield() {
        ItemStack off = getOffhandItem();
        if (off.isEmpty() || !off.getItem().canPerformAction(off, ItemAbilities.SHIELD_BLOCK)) return;

        if (shieldActiveTicks > 0) shieldActiveTicks--;

        Entity incoming = getIncomingProjectile();

        if (isBlocking()) {
            if (incoming != null) {
                shieldActiveTicks = 20;
                faceProjectile(incoming);
            }
            if (shieldActiveTicks <= 0) stopUsingItem();
            return;
        }

        if (isUsingItem()) return;

        if (incoming != null) {
            shieldActiveTicks = 20;
            faceProjectile(incoming);
            getNavigation().stop();
            startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    private Entity getIncomingProjectile() {
        Entity best = null;
        double bestDistSq = Double.MAX_VALUE;
        Vec3 lookDir = this.getLookAngle();
        var nearby = this.level().getEntities(this, this.getBoundingBox().inflate(25.0));
        for (var entity : nearby) {
            if (!entity.isAlive() || entity.isRemoved() || entity == this || entity.getDeltaMovement().lengthSqr() < 0.001)
                continue;
            if (!(entity instanceof AbstractArrow || entity instanceof ThrowableProjectile
                    || entity instanceof AbstractHurtingProjectile
                    || entity instanceof FireworkRocketEntity || entity instanceof LlamaSpit))
                continue;
            // Only react to projectiles within field of view (in front, not behind)
            Vec3 toProj = entity.position().subtract(this.position()).normalize();
            if (lookDir.dot(toProj) < -0.1) continue;
            if (!isHeadingTowards(entity)) continue;
            double d = entity.distanceToSqr(this);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = entity;
            }
        }
        return best;
    }

    private void faceProjectile(Entity projectile) {
        Vec3 vel = projectile.getDeltaMovement();
        if (vel.lengthSqr() < 0.0001) return;
        Vec3 dir = vel.normalize();
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180F / Math.PI)) - 90F + 180F;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    private boolean isHeadingTowards(Entity projectile) {
        Vec3 vel = projectile.getDeltaMovement();
        if (vel.lengthSqr() < 0.001) return false;
        Vec3 mobEye = this.getEyePosition();
        Vec3 toMob = mobEye.subtract(projectile.position());
        double dist = toMob.length();
        if (dist > 25.0) return false;
        if (dist < 0.25) return true;

        Vec3 velDir = vel.normalize();
        Vec3 dirToMob = toMob.normalize();
        if (velDir.dot(dirToMob) < 0.55) return false;

        double speed = vel.length();
        if (speed < 0.01) return false;
        if (dist / speed > 25.0) return false;

        // Closest-approach distance on projectile trajectory
        double t = -toMob.dot(vel) / (speed * speed);
        t = Mth.clamp(t, 0, 40);
        if (projectile.position().add(vel.scale(t)).distanceToSqr(mobEye) < 16.0) return true;

        for (int tick = 5; tick <= 40; tick += 5) {
            if (projectile.position().add(vel.scale(tick)).distanceToSqr(mobEye) < 16.0) return true;
        }
        return false;
    }

    @Override
    protected abstract void registerGoals();
}
