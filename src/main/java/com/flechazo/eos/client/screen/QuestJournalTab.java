package com.flechazo.eos.client.screen;

import com.flechazo.eos.network.OpenPlayerQuestJournalPayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import vodmordia.modtabs.api.tabs_menu.ScreenRegistry;
import vodmordia.modtabs.api.tabs_menu.SimpleItemTab;

public final class QuestJournalTab extends SimpleItemTab {
    public QuestJournalTab() {
        super(new ItemStack(Items.WRITABLE_BOOK));
    }

    @Override
    public void openTargetScreen(Player player) {
        if (!player.level().isClientSide || !player.isAlive() || !player.containerMenu.getCarried().isEmpty()) {
            return;
        }
        new OpenPlayerQuestJournalPayload().sendToServer();
    }

    @Override
    public boolean isEnabled(Player player) {
        return player.isAlive();
    }

    @Override
    public void initTabOnScreens() {
        ScreenRegistry.builder()
                .withDimensions(
                        player -> SurvivorQuestScreen.screenWidth(),
                        player -> SurvivorQuestScreen.screenHeight()
                )
                .registerAllTabs(PlayerQuestJournalScreen.class);
    }

    @Override
    public boolean isCurrentlyUsed(Screen screen) {
        return screen instanceof PlayerQuestJournalScreen;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("gui.echoes_of_survival.quest.journal.open");
    }

    @Override
    public int getOverrideOrder() {
        return 1;
    }
}
