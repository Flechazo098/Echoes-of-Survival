package com.flechazo.eos.menu;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.network.SurvivorQuestStatePayload;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

public class SurvivorQuestMenu extends AbstractContainerMenu {
    private final int survivorEntityId;
    private final List<ResourceLocation> questIds;
    private final List<QuestEntry> questEntries;
    private int playerReputation;

    private static final int SUBMIT_OFFSET = 1000;
    private static final int CLAIM_OFFSET = 2000;

    public SurvivorQuestMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(EosMenus.SURVIVOR_QUEST.get(), containerId, readMenuState(buf));
    }

    public SurvivorQuestMenu(int containerId, Inventory inventory, FriendlySurvivorEntity survivor, List<ResourceLocation> questIds) {
        this(EosMenus.SURVIVOR_QUEST.get(), containerId, survivor.getId(), questIds, List.of(), 0);
    }

    private SurvivorQuestMenu(int containerId, Inventory inventory, FriendlySurvivorEntity survivor, List<ResourceLocation> questIds, List<QuestEntry> questEntries, int playerReputation) {
        this(EosMenus.SURVIVOR_QUEST.get(), containerId, survivor.getId(), questIds, questEntries, playerReputation);
    }

    protected SurvivorQuestMenu(MenuType<?> menuType, int containerId, MenuState state) {
        this(
                menuType,
                containerId,
                state.survivorEntityId(),
                state.questIds(),
                state.questEntries(),
                state.playerReputation()
        );
    }

    protected SurvivorQuestMenu(
            MenuType<?> menuType,
            int containerId,
            int survivorEntityId,
            List<ResourceLocation> questIds,
            List<QuestEntry> questEntries,
            int playerReputation
    ) {
        super(menuType, containerId);
        this.survivorEntityId = survivorEntityId;
        this.questIds = new ArrayList<>(questIds);
        this.questEntries = new ArrayList<>(questEntries);
        this.playerReputation = playerReputation;
    }

    public int survivorEntityId() {
        return survivorEntityId;
    }

    public List<ResourceLocation> questIds() {
        return List.copyOf(questIds);
    }

    public int playerReputation() {
        return playerReputation;
    }

    public void addPlayerReputation(int amount) {
        this.playerReputation += amount;
    }

    public void replaceQuestState(List<ResourceLocation> questIds, List<QuestEntry> entries, int playerReputation) {
        this.questIds.clear();
        if (questIds != null) {
            this.questIds.addAll(questIds);
        }
        this.questEntries.clear();
        if (entries != null) {
            this.questEntries.addAll(entries);
        }
        this.playerReputation = playerReputation;
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
        boolean handled = switch (action) {
            case ACCEPT -> QuestApi.accept(sp, questId);
            case SUBMIT -> QuestApi.submitItems(sp, questId);
            case CLAIM -> QuestApi.claim(sp, questId);
        };
        if (handled) {
            Entity entity = sp.level().getEntity(this.survivorEntityId);
            if (entity instanceof FriendlySurvivorEntity survivor) {
                survivor.emitBubbleEvent("interaction", action == Action.ACCEPT ? "quest_accepted" : "quest_completed");
                SurvivorQuestStatePayload.create(sp, survivor).sendTo(sp);
            }
        }
        return handled;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        Entity entity = player.level().getEntity(this.survivorEntityId);
        if (entity instanceof FriendlySurvivorEntity survivor) {
            survivor.endMenuInteraction(player);
        }
    }

    public static boolean open(ServerPlayer player, FriendlySurvivorEntity survivor) {
        if (player.level().isClientSide) return false;

        var professionId = survivor.getProfessionId();
        if (professionId.isEmpty()) return false;

        var profession = EosDatapackIndex.profession(professionId.get());
        if (profession.isEmpty()) return false;

        List<ResourceLocation> quests = currentQuestIdsFor(player, survivor);
        List<QuestEntry> entries = currentQuestEntriesFor(player, quests);

        survivor.beginMenuInteraction(player);
        player.openMenu(
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return survivor.getDisplayName();
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
                        return new SurvivorQuestMenu(containerId, inv, survivor, quests, entries, ReputationApi.get(player));
                    }
                },
                buf -> writeMenuState(
                        buf,
                        new MenuState(survivor.getId(), ReputationApi.get(player), quests, entries)
                )
        );
        return true;
    }

    public static List<ResourceLocation> currentQuestIdsFor(ServerPlayer player, FriendlySurvivorEntity survivor) {
        var professionId = survivor.getProfessionId();
        if (professionId.isEmpty()) return List.of();

        var profession = EosDatapackIndex.profession(professionId.get());
        if (profession.isEmpty()) return List.of();

        return EosDatapackIndex.questIdsFromPools(
                profession.get().logic().questPools(),
                new Random(questSeed(survivor)),
                unlockedFor(player)
        );
    }

    public static List<QuestEntry> currentQuestEntriesFor(ServerPlayer player, List<ResourceLocation> quests) {
        Map<ResourceLocation, PlayerQuestState.QuestProgress> active = QuestApi.getState(player).active();
        List<QuestEntry> entries = new ArrayList<>();
        for (ResourceLocation questId : quests) {
            PlayerQuestState.QuestProgress progress = active.get(questId);
            if (progress == null) {
                var def = EosDatapackIndex.quest(questId);
                if (def.isDefined() && def.get().repeatable() && def.get().maxRepeats() > 0
                        && QuestApi.getCompletions(player, questId) >= def.get().maxRepeats()) {
                    entries.add(new QuestEntry(
                            questId,
                            List.of(),
                            false,
                            false,
                            true,
                            QuestApi.getCompletions(player, questId)
                    ));
                } else {
                    entries.add(QuestEntry.available(questId));
                }
            } else {
                entries.add(new QuestEntry(
                        questId,
                        progress.objectiveProgress(),
                        progress.completed(),
                        progress.claimed(),
                        false,
                        QuestApi.getCompletions(player, questId)
                ));
            }
        }
        return entries;
    }

    private static Predicate<ResourceLocation> unlockedFor(ServerPlayer player) {
        int reputation = ReputationApi.get(player);
        return questId -> {
            var def = EosDatapackIndex.quest(questId);
            if (def.isEmpty()) return false;
            if (def.get().reputationGate().isEmpty()) return true;

            int required = def.get().reputationGate().get().map(
                    value -> value,
                    tier -> EosDatapackIndex.reputationTierByName(tier)
                            .map(ReputationTiersDefinition.Tier::min)
                            .orElse(Integer.MAX_VALUE)
            );
            return reputation >= required;
        };
    }

    private static long questSeed(FriendlySurvivorEntity survivor) {
        UUID uuid = survivor.getUUID();
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
    }

    public record QuestEntry(
            ResourceLocation questId,
            List<Integer> objectiveProgress,
            boolean completed,
            boolean claimed,
            boolean maxReached,
            int completionCount
    ) {
        public static QuestEntry available(ResourceLocation questId) {
            return new QuestEntry(questId, List.of(), false, false, false, 0);
        }

        public boolean accepted() {
            return !objectiveProgress.isEmpty() || completed || claimed;
        }
    }

    protected static MenuState readMenuState(RegistryFriendlyByteBuf buf) {
        int survivorEntityId = buf.readVarInt();
        int playerReputation = buf.readVarInt();
        int size = buf.readVarInt();
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ids.add(buf.readResourceLocation());
        }
        List<QuestEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation questId = buf.readResourceLocation();
            int progressSize = buf.readVarInt();
            List<Integer> objectiveProgress = new ArrayList<>();
            for (int j = 0; j < progressSize; j++) {
                objectiveProgress.add(buf.readVarInt());
            }
            entries.add(new QuestEntry(
                    questId,
                    List.copyOf(objectiveProgress),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt()
            ));
        }
        return new MenuState(survivorEntityId, playerReputation, List.copyOf(ids), List.copyOf(entries));
    }

    protected static void writeMenuState(RegistryFriendlyByteBuf buf, MenuState state) {
        buf.writeVarInt(state.survivorEntityId());
        buf.writeVarInt(state.playerReputation());
        buf.writeVarInt(state.questIds().size());
        for (ResourceLocation questId : state.questIds()) {
            buf.writeResourceLocation(questId);
        }
        for (QuestEntry entry : state.questEntries()) {
            buf.writeResourceLocation(entry.questId());
            buf.writeVarInt(entry.objectiveProgress().size());
            for (int value : entry.objectiveProgress()) {
                buf.writeVarInt(value);
            }
            buf.writeBoolean(entry.completed());
            buf.writeBoolean(entry.claimed());
            buf.writeBoolean(entry.maxReached());
            buf.writeVarInt(entry.completionCount());
        }
    }

    protected record MenuState(
            int survivorEntityId,
            int playerReputation,
            List<ResourceLocation> questIds,
            List<QuestEntry> questEntries
    ) {
    }

    private enum Action {
        ACCEPT,
        SUBMIT,
        CLAIM
    }
}
