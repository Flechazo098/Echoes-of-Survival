package com.flechazo.eos.menu;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.quest.PlayerQuestState;
import com.flechazo.eos.quest.QuestApi;
import com.flechazo.eos.reputation.ReputationApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PlayerQuestJournalMenu extends SurvivorQuestMenu {
    private static final int NO_SURVIVOR = -1;

    public PlayerQuestJournalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, readMenuState(buf));
    }

    private PlayerQuestJournalMenu(int containerId, MenuState state) {
        super(EosMenus.PLAYER_QUEST_JOURNAL.get(), containerId, state);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return false;
    }

    @Override
    public void removed(Player player) {
        // The journal has no survivor interaction to release.
    }

    public static void open(ServerPlayer player) {
        MenuState state = createState(player);
        player.openMenu(
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("gui.echoes_of_survival.quest.journal.title");
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                        return new PlayerQuestJournalMenu(containerId, state);
                    }
                },
                buf -> writeMenuState(buf, state)
        );
    }

    private static MenuState createState(ServerPlayer player) {
        PlayerQuestState state = QuestApi.getState(player);
        List<ResourceLocation> questIds = new ArrayList<>();
        List<QuestEntry> entries = new ArrayList<>();

        state.active().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation questId = entry.getKey();
                    PlayerQuestState.QuestProgress progress = entry.getValue();
                    if (questId == null || progress == null) return;
                    questIds.add(questId);
                    entries.add(new QuestEntry(
                            questId,
                            progress.objectiveProgress(),
                            progress.completed(),
                            progress.claimed(),
                            false,
                            state.completions().getOrDefault(questId, 0)
                    ));
                });

        state.completions().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation questId = entry.getKey();
                    List<Integer> completedProgress = EosDatapackIndex.quest(questId)
                            .map(quest -> quest.objectives().stream()
                                    .map(objective -> objective == null ? 0 : objective.count())
                                    .toList())
                            .orElse(List.of());
                    questIds.add(questId);
                    entries.add(new QuestEntry(
                            questId,
                            completedProgress,
                            true,
                            true,
                            false,
                            entry.getValue()
                    ));
                });

        return new MenuState(
                NO_SURVIVOR,
                ReputationApi.get(player),
                List.copyOf(questIds),
                List.copyOf(entries)
        );
    }
}
