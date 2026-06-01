package com.flechazo.eos.menu;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.trade.ProfessionDefinition;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.quest.QuestApi;
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

public class SurvivorQuestMenu extends AbstractContainerMenu {
    private final int survivorEntityId;
    private final List<ResourceLocation> questIds;

    private static final int SUBMIT_OFFSET = 1000;
    private static final int CLAIM_OFFSET = 2000;

    public SurvivorQuestMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(EosMenus.SURVIVOR_QUEST.get(), containerId);
        this.survivorEntityId = buf.readVarInt();
        int size = buf.readVarInt();
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ids.add(buf.readResourceLocation());
        }
        this.questIds = List.copyOf(ids);
    }

    public SurvivorQuestMenu(int containerId, Inventory inventory, FriendlySurvivorEntity survivor, List<ResourceLocation> questIds) {
        super(EosMenus.SURVIVOR_QUEST.get(), containerId);
        this.survivorEntityId = survivor.getId();
        this.questIds = List.copyOf(questIds);
    }

    public int survivorEntityId() {
        return survivorEntityId;
    }

    public List<ResourceLocation> questIds() {
        return questIds;
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

        List<ResourceLocation> quests = EosDatapackIndex.questIdsFromPools(profession.logic().questPools());

        player.openMenu(
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("gui.echoes_of_survival.survivor.quest");
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
                        return new SurvivorQuestMenu(containerId, inv, survivor, quests);
                    }
                },
                buf -> {
                    buf.writeVarInt(survivor.getId());
                    buf.writeVarInt(quests.size());
                    for (ResourceLocation q : quests) {
                        buf.writeResourceLocation(q);
                    }
                }
        );
    }

    private enum Action {
        ACCEPT,
        SUBMIT,
        CLAIM
    }
}
