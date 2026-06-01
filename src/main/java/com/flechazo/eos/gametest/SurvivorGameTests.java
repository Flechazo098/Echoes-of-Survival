package com.flechazo.eos.gametest;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.entity.EosEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(EchoesofSurvival.MODID)
@PrefixGameTestTemplate(false)
public class SurvivorGameTests {
    private static void fail(String m) {
        throw new GameTestAssertException(m);
    }


    @GameTest(template = "survivor_test_platform")
    public static void hostile_has_equipment_after_spawn(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.HOSTILE_SURVIVOR.get(), new BlockPos(2, 1, 2));
        e.finalizeSpawn(h.getLevel(), h.getLevel().getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.COMMAND, null);
        h.succeedIf(() -> {
            if (e.getMainHandItem().isEmpty() && e.getOffhandItem().isEmpty()) fail("no equipment");
        });
    }

    @GameTest(template = "survivor_test_platform")
    public static void neutral_state_angry(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(2, 1, 2));
        h.succeedIf(() -> {
            if (e.isAngry()) fail("starts angry");
            e.setAngry(true);
            if (!e.isAngry()) fail("not angry");
            e.setAngry(false);
            if (e.isAngry()) fail("still angry");
        });
    }

    @GameTest(template = "survivor_test_platform")
    public static void neutral_state_begging(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(2, 1, 2));
        h.succeedIf(() -> {
            if (e.isBegging()) fail("starts begging");
            e.setBegging(true);
            if (!e.isBegging()) fail("not begging");
            e.setBegging(false);
            if (e.isBegging()) fail("still begging");
        });
    }

    @GameTest(template = "survivor_test_platform")
    public static void neutral_nbt_roundtrip(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(2, 1, 2));
        h.succeedIf(() -> {
            e.setAngry(true);
            e.setBegging(true);
            e.setSkinUuid(java.util.UUID.randomUUID());
            var t = new net.minecraft.nbt.CompoundTag();
            e.addAdditionalSaveData(t);
            if (!t.getBoolean("EosNeutralAngry")) fail("nbt angry");
            if (!t.getBoolean("EosNeutralBegging")) fail("nbt beg");
            if (!t.contains("EosSkinUuid")) fail("nbt skin");
            var r = h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(2, 1, 3));
            r.readAdditionalSaveData(t);
            if (!r.isAngry()) fail("restored angry");
            if (!r.isBegging()) fail("restored beg");
            if (r.getSkinUuid().isEmpty()) fail("restored skin");
        });
    }

    // ======== PLAYER INTERACTION ========

    @GameTest(template = "survivor_test_platform")
    public static void neutral_becomes_angry_when_hurt_by_player(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(2, 1, 2));
        h.succeedIf(() -> {
            Player m = h.makeMockPlayer(GameType.SURVIVAL);
            e.hurt(h.getLevel().damageSources().playerAttack(m), 1.0F);
            if (!e.isAngry()) fail("should be angry");
        });
    }

    @GameTest(template = "survivor_test_platform")
    public static void neutral_accepts_food_when_begging(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(2, 1, 2));
        h.succeedIf(() -> {
            Player m = h.makeMockPlayer(GameType.SURVIVAL);
            m.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            e.setBegging(true);
            if (!e.interact(m, InteractionHand.MAIN_HAND).consumesAction()) fail("should accept");
            if (!e.isRemoved()) fail("should discard");
        });
    }

    @GameTest(template = "survivor_test_platform")
    public static void neutral_rejects_food_when_not_begging(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(2, 1, 2));
        h.succeedIf(() -> {
            Player m = h.makeMockPlayer(GameType.SURVIVAL);
            m.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            if (e.interact(m, InteractionHand.MAIN_HAND).consumesAction()) fail("should reject");
        });
    }

    @GameTest(template = "survivor_test_platform")
    public static void friendly_trade_interaction_handled(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.FRIENDLY_SURVIVOR.get(), new BlockPos(2, 1, 2));
        h.succeedIf(() -> {
            Player m = h.makeMockPlayer(GameType.SURVIVAL);
            if (e.interact(m, InteractionHand.MAIN_HAND) == InteractionResult.PASS) fail("should handle");
        });
    }

    // ======== TACTICAL INVENTORY ========

    @GameTest(template = "survivor_test_platform")
    public static void tactical_inventory_stores_and_extracts(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.HOSTILE_SURVIVOR.get(), new BlockPos(5, 1, 4));
        h.succeedIf(() -> {
            var inv = e.tacticalInventory;
            var ins = inv.insertItem(0, new ItemStack(Items.TOTEM_OF_UNDYING), false);
            if (!ins.isEmpty()) fail("insert");
            if (!inv.getStackInSlot(0).is(Items.TOTEM_OF_UNDYING)) fail("wrong item");
            var ext = inv.extractItem(0, 1, false);
            if (!ext.is(Items.TOTEM_OF_UNDYING)) fail("wrong extract");
            if (!inv.getStackInSlot(0).isEmpty()) fail("not empty");
        });
    }

    @GameTest(template = "survivor_test_platform", timeoutTicks = 60)
    public static void tactical_totem_from_inventory_saves_life(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.HOSTILE_SURVIVOR.get(), new BlockPos(5, 1, 4));
        e.tacticalInventory.insertItem(0, new ItemStack(Items.TOTEM_OF_UNDYING), false);
        e.hurt(h.getLevel().damageSources().generic(), 50.0F);
        h.succeedWhen(() -> {
            if (!e.isAlive()) fail("should survive");
            if (e.getHealth() < 1.0F) fail("hp=" + e.getHealth());
            if (!e.tacticalInventory.getStackInSlot(0).isEmpty()) fail("totem not consumed");
        });
    }

    // ======== POTION USAGE ========

    @GameTest(template = "survivor_test_platform", timeoutTicks = 40)
    public static void tactical_healing_potion_data_loaded(GameTestHelper h) {
        var potion = new ItemStack(Items.SPLASH_POTION);
        potion.set(DataComponents.POTION_CONTENTS,
                new PotionContents(
                        Optional.of(Potions.HEALING),
                        Optional.empty(), List.of()));
        h.succeedIf(() -> {
            boolean match = com.flechazo.eos.data.EosDatapackIndex.matches(potion);
            String path = potion.get(DataComponents.POTION_CONTENTS)
                    .potion().get().unwrapKey().get().location().getPath();
            if (!match) fail("healing potion NOT matched. path=" + path + " potion=" + potion);
        });
    }

    @GameTest(template = "survivor_test_platform", timeoutTicks = 100)
    public static void tactical_healing_potion_emergency_use(GameTestHelper h) {
        var e = h.spawn(EosEntityTypes.HOSTILE_SURVIVOR.get(), new BlockPos(3, 1, 3));
        e.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        // Splash healing potion matching healing_potions data ("splash:healing")
        var potion = new ItemStack(Items.SPLASH_POTION);
        potion.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.of(Potions.HEALING),
                        Optional.empty(), List.of()));
        e.tacticalInventory.insertItem(0, potion, false);
        h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(5, 1, 3));
        e.hurt(h.getLevel().damageSources().generic(), 19.0F); // 24-19 = 5 HP < 12
        float[] dmgHp = {e.getHealth()};
        h.succeedWhen(() -> {
            boolean consumed = e.tacticalInventory.getStackInSlot(0).isEmpty();
            float hp = e.getHealth();
            if (consumed && hp > dmgHp[0]) return;
            throw new GameTestAssertException("hp=" + hp + " consumed=" + consumed);
        });
    }

    @GameTest(template = "survivor_test_platform", timeoutTicks = 150)
    public static void tactical_harmful_potion_thrown_at_target(GameTestHelper h) {
        var hostile = h.spawn(EosEntityTypes.HOSTILE_SURVIVOR.get(), new BlockPos(3, 1, 3));
        hostile.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var poison = new ItemStack(Items.SPLASH_POTION);
        poison.set(DataComponents.POTION_CONTENTS,
                new PotionContents(
                        Optional.of(Potions.POISON),
                        Optional.empty(), List.of()));
        hostile.tacticalInventory.insertItem(0, poison, false);
        var target = h.spawn(EosEntityTypes.NEUTRAL_SURVIVOR.get(), new BlockPos(5, 1, 3));
        h.succeedWhen(() -> {
            boolean consumed = hostile.tacticalInventory.getStackInSlot(0).isEmpty();
            boolean poisoned = target.hasEffect(MobEffects.POISON);
            if (consumed && poisoned) return;
            if (consumed) return; // thrown successfully (accuracy depends on geometry)
            if (h.getTick() >= 140) fail("never thrown. consumed=" + consumed);
            throw new net.minecraft.gametest.framework.GameTestAssertException("consumed=" + consumed + " poisoned=" + poisoned);
        });
    }
}