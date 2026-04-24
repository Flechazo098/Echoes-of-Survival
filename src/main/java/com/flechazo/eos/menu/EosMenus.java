package com.flechazo.eos.menu;

import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class EosMenus {
    private EosMenus() {
    }

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, EchoesofSurvival.MODID);

    public static final Supplier<MenuType<SurvivorQuestMenu>> SURVIVOR_QUEST = MENUS.register(
            "survivor_quest",
            () -> new MenuType<>((IContainerFactory<SurvivorQuestMenu>) SurvivorQuestMenu::new, FeatureFlags.VANILLA_SET)
    );

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}

