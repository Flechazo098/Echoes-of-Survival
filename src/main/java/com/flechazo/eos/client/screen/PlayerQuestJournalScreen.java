package com.flechazo.eos.client.screen;

import com.flechazo.eos.menu.SurvivorQuestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PlayerQuestJournalScreen extends SurvivorQuestScreen {
    public PlayerQuestJournalScreen(SurvivorQuestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, true);
    }
}
