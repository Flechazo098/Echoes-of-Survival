package com.flechazo.eos.menu;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.trade.ProfessionDefinition;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.quest.PlayerQuestState;
import com.flechazo.eos.quest.QuestApi;
import com.flechazo.eos.reputation.ReputationApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SurvivorQuestMenu extends AbstractContainerMenu {
    private final int survivorEntityId;
    private final List<ResourceLocation> questIds;
    private final List<QuestEntry> questEntries;
    private int playerReputation;

    private static final int SUBMIT_OFFSET = 1000;
    private static final int CLAIM_OFFSET = 2000;

    public SurvivorQuestMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(EosMenus.SURVIVOR_QUEST.get(), containerId);
        this.survivorEntityId = buf.readVarInt();
        this.playerReputation = buf.readVarInt();
        int size = buf.readVarInt();
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ids.add(buf.readResourceLocation());
        }
        this.questIds = List.copyOf(ids);
        List<QuestEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation questId = buf.readResourceLocation();
            int progressSize = buf.readVarInt();
            List<Integer> objectiveProgress = new ArrayList<>();
            for (int j = 0; j < progressSize; j++) {
                objectiveProgress.add(buf.readVarInt());
            }
            boolean completed = buf.readBoolean();
            boolean claimed = buf.readBoolean();
            entries.add(new QuestEntry(questId, List.copyOf(objectiveProgress), completed, claimed));
        }
        this.questEntries = entries;
    }

    public SurvivorQuestMenu(int containerId, Inventory inventory, FriendlySurvivorEntity survivor, List<ResourceLocation> questIds) {
        super(EosMenus.SURVIVOR_QUEST.get(), containerId);
        this.survivorEntityId = survivor.getId();
        this.questIds = List.copyOf(questIds);
        this.questEntries = new ArrayList<>();
        this.playerReputation = 0;
    }

    private SurvivorQuestMenu(int containerId, Inventory inventory, FriendlySurvivorEntity survivor, List<ResourceLocation> questIds, List<QuestEntry> questEntries, int playerReputation) {
        super(EosMenus.SURVIVOR_QUEST.get(), containerId);
        this.survivorEntityId = survivor.getId();
        this.questIds = List.copyOf(questIds);
        this.questEntries = new ArrayList<>(questEntries);
        this.playerReputation = playerReputation;
    }

    public int survivorEntityId() {
        return survivorEntityId;
    }

    public List<ResourceLocation> questIds() {
        return questIds;
    }

    public int playerReputation() {
        return playerReputation;
    }

    public void addPlayerReputation(int amount) {
        this.playerReputation += amount;
    }

    public QuestEntry questEntry(int index) {
        if (index < 0 || index >= questEntries.size()) {
            return QuestEntry.available(index >= 0 && index < questIds.size() ? questIds.get(index) : null);
        }
        return questEntries.get(index);
    }

    public void updateQuestEntry(int index, QuestEntry entry) {
        if (index < 0 || index >= questEntries.size() || entry == null) return;
        questEntries.set(index, entry);
    }

    @Override
    public boolean stillValid(Player player) {
        Entity entity = player.level().getEntity(survivorEntityId);
        if (!(entity instanceof FriendlySurvivorEntity survivor)) return false;
        return player.distanceToSqr(survivor) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer sp)) return false;

        int idx;
        Action action;
        if (buttonId >= CLAIM_OFFSET) {
            action = Action.CLAIM;
            idx = buttonId - CLAIM_OFFSET;
        } else if (buttonId >= SUBMIT_OFFSET) {
            action = Action.SUBMIT;
            idx = buttonId - SUBMIT_OFFSET;
        } else {
            action = Action.ACCEPT;
            idx = buttonId;
        }
        if (idx < 0 || idx >= questIds.size()) return false;

        ResourceLocation questId = questIds.get(idx);
        return switch (action) {
            case ACCEPT -> QuestApi.accept(sp, questId);
            case SUBMIT -> QuestApi.submitItems(sp, questId);
            case CLAIM -> QuestApi.claim(sp, questId);
        };
    }

    public static void open(ServerPlayer player, FriendlySurvivorEntity survivor) {
        if (player.level().isClientSide) return;

        ResourceLocation professionId = survivor.getProfessionId().orElse(null);
        if (professionId == null) return;

        ProfessionDefinition profession = EosDatapackIndex.profession(professionId).orElse(null);
        if (profession == null) return;

        List<ResourceLocation> quests = EosDatapackIndex.questIdsFromPools(profession.logic().questPools(), new Random(questSeed(survivor)));

        player.openMenu(
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("gui.echoes_of_survival.survivor.quest");
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
                        return new SurvivorQuestMenu(containerId, inv, survivor, quests, questEntriesFor(player, quests), ReputationApi.get(player));
                    }
                },
                buf -> {
                    buf.writeVarInt(survivor.getId());
                    buf.writeVarInt(ReputationApi.get(player));
                    buf.writeVarInt(quests.size());
                    for (ResourceLocation q : quests) {
                        buf.writeResourceLocation(q);
                    }
                    List<QuestEntry> entries = questEntriesFor(player, quests);
                    for (QuestEntry entry : entries) {
                        buf.writeResourceLocation(entry.questId());
                        buf.writeVarInt(entry.objectiveProgress().size());
                        for (int value : entry.objectiveProgress()) {
                            buf.writeVarInt(value);
                        }
                        buf.writeBoolean(entry.completed());
                        buf.writeBoolean(entry.claimed());
                    }
                }
        );
    }

    private static List<QuestEntry> questEntriesFor(ServerPlayer player, List<ResourceLocation> quests) {
        Map<ResourceLocation, PlayerQuestState.QuestProgress> active = QuestApi.getState(player).active();
        List<QuestEntry> entries = new ArrayList<>();
        for (ResourceLocation questId : quests) {
            PlayerQuestState.QuestProgress progress = active.get(questId);
            if (progress == null) {
                entries.add(QuestEntry.available(questId));
            } else {
                entries.add(new QuestEntry(
                        questId,
                        progress.objectiveProgress(),
                        progress.completed(),
                        progress.claimed()
                ));
            }
        }
        return entries;
    }

    private static long questSeed(FriendlySurvivorEntity survivor) {
        UUID uuid = survivor.getUUID();
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
    }

    public record QuestEntry(
            ResourceLocation questId,
            List<Integer> objectiveProgress,
            boolean completed,
            boolean claimed
    ) {
        public static QuestEntry available(ResourceLocation questId) {
            return new QuestEntry(questId, List.of(), false, false);
        }

        public boolean accepted() {
            return !objectiveProgress.isEmpty() || completed || claimed;
        }
    }

    private enum Action {
        ACCEPT,
        SUBMIT,
        CLAIM
    }
}
