package com.flechazo.eos.entity;

import com.flechazo.eos.entity.ai.goal.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class HostileSurvivorEntity extends PathfinderMob implements RangedAttackMob, CrossbowAttackMob {
    private static final EntityDataAccessor<String> SKIN_UUID =
            SynchedEntityData.defineId(HostileSurvivorEntity.class, EntityDataSerializers.STRING);
    private static final String NBT_SKIN_UUID = "EosSkinUuid";

    private boolean chargingCrossbow = false;
    private final List<ResourceLocation> tacticalEffects = new ArrayList<>();
    private int lastCombatTick = -100000;

    public HostileSurvivorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);

        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }

        // basic tactical effects for first testing
        tacticalEffects.add(ResourceLocation.withDefaultNamespace("regeneration"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_UUID, "");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SurvivorFleeGoal(this, 0.35F, 0.70F, 15.0F, 1.20, 1.45));
        this.goalSelector.addGoal(2, new SurvivorAvoidCreeperGoal(this, 10.0F, 1.15, 1.50));
        this.goalSelector.addGoal(3, new SurvivorLadderClimbGoal(this));
        this.goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new OpenFenceGoal(this, true));
        this.goalSelector.addGoal(5, new SurvivorUseTacticalEffectGoal(this, tacticalEffects, 0.45F));
        this.goalSelector.addGoal(6, new SurvivorWeaponSwitchGoal(this, 6.0));
        this.goalSelector.addGoal(7, new SurvivorRaiseShieldGoal(this));
        this.goalSelector.addGoal(8, new RangedCrossbowAttackGoal<>(this, 1.15, 18.0F));
        this.goalSelector.addGoal(8, new RangedBowAttackGoal<>(this, 1.15, 20, 18.0F));
        this.goalSelector.addGoal(9, new MeleeAttackGoal(this, 1.25, false));
        this.goalSelector.addGoal(10, new RandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this,
                Player.class,
                10,
                true,
                false,
                living -> living instanceof Player p && canSeePlayerWellEnough(p)
        ));
    }

    public Optional<UUID> getSkinUuid() {
        String raw = this.entityData.get(SKIN_UUID);
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void setSkinUuid(UUID uuid) {
        this.entityData.set(SKIN_UUID, uuid != null ? uuid.toString() : "");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getSkinUuid().ifPresent(uuid -> tag.putString(NBT_SKIN_UUID, uuid.toString()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_SKIN_UUID)) {
            try {
                setSkinUuid(UUID.fromString(tag.getString(NBT_SKIN_UUID)));
            } catch (Exception ignored) {
            }
        }
    }

    private boolean canSeePlayerWellEnough(Player player) {
        if (player == null) return false;
        if (!this.getSensing().hasLineOfSight(player)) return false;

        Vec3 toTarget = player.position().subtract(this.position());
        double distSqr = toTarget.lengthSqr();
        if (distSqr < 4.0) return true;

        Vec3 dir = toTarget.normalize();
        Vec3 look = this.getLookAngle();
        double dot = look.dot(dir); // >0 in front
        if (dot < 0.0 && distSqr > 16.0) return false; // behind & not very close
        if (dot < 0.25 && distSqr > 64.0) return false; // mostly behind
        return true;
    }

    public int getLastCombatTick() {
        return lastCombatTick;
    }

    private void markCombat() {
        this.lastCombatTick = this.tickCount;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
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
        // avoid oscillation: when low HP, prioritize fleeing over picking new targets
        if (!this.level().isClientSide && target != null) {
            if (SurvivorAiUtil.isLowHp(this, 0.35F)) {
                return;
            }
            markCombat();
        }
        super.setTarget(target);
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
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        // Use whichever hand is holding the ranged weapon (bow/crossbow/trident).
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
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty, MobSpawnType spawnType, net.minecraft.world.entity.SpawnGroupData groupData) {
        var data = super.finalizeSpawn(level, difficulty, spawnType, groupData);

        // loadout: either melee+shield, or ranged+melee (crossbow/bow + sword).
        if (this.random.nextFloat() < 0.40F) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        } else {
            if (this.random.nextFloat() < 0.60F) {
                this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            } else {
                this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            }
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));
        }

        // light armor for first test
        if (this.random.nextFloat() < 0.5F) this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        if (this.random.nextFloat() < 0.5F) this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        if (this.random.nextFloat() < 0.5F) this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        if (this.random.nextFloat() < 0.5F) this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));

        ensureSkinAssigned();

        return data;
    }

    @Override
    public ItemStack getProjectile(ItemStack weaponStack) {
        if (weaponStack != null && !weaponStack.isEmpty() && (weaponStack.getItem() instanceof CrossbowItem || weaponStack.getItem() instanceof BowItem)) {
            return Items.ARROW.getDefaultInstance();
        }
        return super.getProjectile(weaponStack);
    }

    private void ensureSkinAssigned() {
        if (this.level().isClientSide) return;
        if (getSkinUuid().isPresent()) return;

        List<UUID> pool = com.flechazo.eos.data.EosDatapackIndex.skinLibraryUuids();
        if (pool.isEmpty()) return;

        int idx = Math.floorMod(this.getUUID().hashCode(), pool.size());
        UUID picked = pool.get(idx);
        if (picked != null) setSkinUuid(picked);
    }
}
