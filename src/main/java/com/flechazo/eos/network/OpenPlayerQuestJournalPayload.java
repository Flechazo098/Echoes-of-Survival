package com.flechazo.eos.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.menu.PlayerQuestJournalMenu;
import net.minecraft.server.level.ServerPlayer;

@NetworkPacket(
        modId = EchoesofSurvival.MODID,
        id = "open_player_quest_journal",
        side = Side.SERVER
)
public record OpenPlayerQuestJournalPayload() implements INetworkPacket<OpenPlayerQuestJournalPayload> {
    @Override
    public void handle(INetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.sender();
            if (player != null && player.isAlive() && player.containerMenu.getCarried().isEmpty()) {
                PlayerQuestJournalMenu.open(player);
            }
        });
    }
}
