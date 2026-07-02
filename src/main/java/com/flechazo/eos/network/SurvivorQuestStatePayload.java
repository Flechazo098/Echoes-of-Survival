package com.flechazo.eos.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.client.screen.SurvivorQuestScreen;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@NetworkPacket(
        modId = EchoesofSurvival.MODID,
        id = "survivor_quest_state",
        side = Side.CLIENT
)
public record SurvivorQuestStatePayload(
        int survivorEntityId,
        int playerReputation,
        List<ResourceLocation> questIds,
        List<QuestEntryData> entries
) implements INetworkPacket<SurvivorQuestStatePayload> {

    public static SurvivorQuestStatePayload create(ServerPlayer player, FriendlySurvivorEntity survivor) {
        List<ResourceLocation> questIds = SurvivorQuestMenu.currentQuestIdsFor(player, survivor);
        List<QuestEntryData> entries = SurvivorQuestMenu.currentQuestEntriesFor(player, questIds).stream()
                .map(entry -> new QuestEntryData(
                        entry.questId(),
                        entry.objectiveProgress(),
                        entry.completed(),
                        entry.claimed(),
                        entry.maxReached()
                ))
                .toList();
        return new SurvivorQuestStatePayload(survivor.getId(), com.flechazo.eos.reputation.ReputationApi.get(player), questIds, entries);
    }

    @Override
    public void handle(INetworkContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = context.client();
            if (minecraft == null || minecraft.player == null) return;
            if (!(minecraft.player.containerMenu instanceof SurvivorQuestMenu menu)) return;
            if (menu.survivorEntityId() != this.survivorEntityId) return;

            List<SurvivorQuestMenu.QuestEntry> questEntries = this.entries.stream()
                    .map(entry -> new SurvivorQuestMenu.QuestEntry(
                            entry.questId(),
                            entry.objectiveProgress(),
                            entry.completed(),
                            entry.claimed(),
                            entry.maxReached()
                    ))
                    .toList();
            menu.replaceQuestState(this.questIds, questEntries, this.playerReputation);

            if (minecraft.screen instanceof SurvivorQuestScreen screen && screen.getMenu() == menu) {
                screen.onQuestStateUpdated();
            }
        });
    }

    public record QuestEntryData(
            @Nullable ResourceLocation questId,
            List<Integer> objectiveProgress,
            boolean completed,
            boolean claimed,
            boolean maxReached
    ) {
    }
}
