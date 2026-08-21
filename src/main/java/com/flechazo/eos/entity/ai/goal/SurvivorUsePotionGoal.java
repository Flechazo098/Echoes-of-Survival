package com.flechazo.eos.entity.ai.goal;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.config.EosConfigs;
import com.flechazo.eos.entity.AbstractSurvivorEntity;
import com.flechazo.eos.profile.SurvivorProfile;
import com.google.common.collect.Iterables;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

public class SurvivorUsePotionGoal extends Goal {

    private static final int COOLDOWN_TICKS = 20 * 10;
    private static final int RANDOM_ACTIVATE_CHANCE = 5;

    private static final float EMERGENCY_HP = 0.50F;

    private final PathfinderMob mob;
    private final Supplier<IItemHandler> invSupplier;
    private int cooldown = 0;
    private int useTicks = 0;
    private boolean drinking = false;
    private boolean healingThrow = false;
    private int targetSlot = -1;
    private LivingEntity throwTarget = null;

    public SurvivorUsePotionGoal(PathfinderMob mob, Supplier<IItemHandler> invSupplier) {
        this.mob = mob;
        this.invSupplier = invSupplier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (mob.level().isClientSide) return false;
        healingThrow = false;

        IItemHandler inv = invSupplier.get();
        if (inv == null) return false;

        if (mob.getHealth() < mob.getMaxHealth() * EMERGENCY_HP) {
            int slot = findHealingPotion(inv);
            if (slot >= 0) {
                targetSlot = slot;
                ItemStack stack = inv.getStackInSlot(slot);
                drinking = !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION);
                if (!drinking) {
                    throwTarget = null;
                    healingThrow = true;
                }
                return true;
            }
        }

        if (mob instanceof AbstractSurvivorEntity survivor
                && survivor.hasSpecialty(SurvivorProfile.Specialty.FIRST_AID)) {
            int healingSlot = findHealingPotion(inv);
            LivingEntity ally = healingSlot < 0 ? null : findInjuredAlly(survivor);
            if (ally != null) {
                targetSlot = healingSlot;
                drinking = false;
                throwTarget = ally;
                healingThrow = true;
                return true;
            }
        }

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (mob.getRandom().nextInt(RANDOM_ACTIVATE_CHANCE) != 0) return false;

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && isPotion(stack)) slots.add(i);
        }
        if (slots.isEmpty()) return false;

        targetSlot = slots.get(mob.getRandom().nextInt(slots.size()));
        ItemStack chosen = inv.getStackInSlot(targetSlot);
        if (chosen.isEmpty()) return false;

        if (chosen.is(Items.LINGERING_POTION) || chosen.is(Items.SPLASH_POTION)) {
            PotionContents contents = chosen.get(DataComponents.POTION_CONTENTS);
            boolean harmful = contents != null && Iterables.any(
                    contents.getAllEffects(), e -> !e.getEffect().value().isBeneficial());
            throwTarget = harmful ? mob.getTarget() : null;
            drinking = false;
            return true;
        }
        if (chosen.is(Items.POTION)) {
            drinking = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return drinking && useTicks < 32 && mob.isAlive();
    }

    @Override
    public void start() {
        useTicks = 0;
        IItemHandler inv = invSupplier.get();
        if (inv == null) return;

        if (drinking) {
            ItemStack extracted = inv.extractItem(targetSlot, 1, false);
            if (!extracted.isEmpty()) {
                mob.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                mob.startUsingItem(InteractionHand.MAIN_HAND);
            }
        } else {
            ItemStack potion = inv.extractItem(targetSlot, 1, false);
            if (!potion.isEmpty()) {
                ThrownPotion entity = new ThrownPotion(mob.level(), mob);
                entity.setItem(potion);
                if (throwTarget != null) {
                    double dx = throwTarget.getX() - mob.getX();
                    double dz = throwTarget.getZ() - mob.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    double dy = throwTarget.getY(0.5) - mob.getY(0.5) + dist * 0.2;
                    double spd = Math.min(1.5, Math.sqrt(dist) * 0.4);
                    entity.shoot(dx, dy, dz, (float) spd, 6.0F);
                } else {
                    entity.shoot(0, -0.5, 0, 0.5F, 1.0F);
                }
                mob.level().addFreshEntity(entity);
                if (healingThrow && mob instanceof AbstractSurvivorEntity survivor
                        && survivor.hasSpecialty(SurvivorProfile.Specialty.FIRST_AID)) {
                    LivingEntity healed = throwTarget == null ? mob : throwTarget;
                    healed.heal((float) (4.0D * EosConfigs.TRAITS.get().firstAidHealingBonus()));
                }
            }
            cooldown = effectiveCooldown();
        }
    }

    @Override
    public void tick() {
        if (drinking) {
            useTicks++;
            mob.getNavigation().stop();
            if (useTicks >= 32 || !mob.isUsingItem()) {
                if (mob.isUsingItem()) mob.stopUsingItem();
                ItemStack held = mob.getMainHandItem();
                if (!held.isEmpty() && held.getItem() == Items.POTION) {
                    held.shrink(1);
                    if (held.isEmpty()) mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
                drinking = false;
                cooldown = effectiveCooldown();
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private int findHealingPotion(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (EosDatapackIndex.matches(stack)) return i;
        }
        return -1;
    }

    private LivingEntity findInjuredAlly(AbstractSurvivorEntity healer) {
        return healer.level().getEntitiesOfClass(AbstractSurvivorEntity.class,
                        healer.getBoundingBox().inflate(8.0D),
                        candidate -> candidate != healer
                                && candidate.isAlive()
                                && candidate.getAffiliationId().equals(healer.getAffiliationId())
                                && candidate.getHealth() < candidate.getMaxHealth() * 0.60F)
                .stream()
                .min(java.util.Comparator.comparingDouble(candidate -> candidate.getHealth() / candidate.getMaxHealth()))
                .orElse(null);
    }

    private int effectiveCooldown() {
        if (mob instanceof AbstractSurvivorEntity survivor
                && survivor.hasSpecialty(SurvivorProfile.Specialty.FIRST_AID)) {
            return Math.max(1, (int) Math.round(COOLDOWN_TICKS
                    * (1.0D - EosConfigs.TRAITS.get().firstAidCooldownReduction())));
        }
        return COOLDOWN_TICKS;
    }

    private static boolean isPotion(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }
}
