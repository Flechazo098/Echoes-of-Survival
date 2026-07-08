package com.flechazo.eos.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.menu.SurvivorPersonalMenu;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

@NetworkPacket(
        modId = EchoesofSurvival.MODID,
        id = "survivor_interact",
        side = Side.SERVER
)
public record SurvivorInteractPayload(int entityId, int action) implements INetworkPacket<SurvivorInteractPayload> {
    public static final int ACTION_TRADE = 0;
    public static final int ACTION_QUEST = 1;
    public static final int ACTION_OVERLAY_OPEN = 2;
    public static final int ACTION_OVERLAY_CLOSE = 3;
    public static final int ACTION_RECRUIT = 4;
    public static final int ACTION_PERSONAL = 5;
    public static final int ACTION_CYCLE_PATROL = 6;
    public static final int ACTION_CYCLE_ATTACK = 7;

    @Override
    public void handle(INetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.sender();
            if (player == null) return;

            Entity entity = player.level().getEntity(this.entityId);
            if (!(entity instanceof FriendlySurvivorEntity survivor)) return;
            if (!survivor.isAlive() || player.distanceToSqr(survivor) > 64.0) return;

            if (this.action == ACTION_TRADE) {
                survivor.endMenuInteraction(player);
                survivor.openTradeInterface(player);
            } else if (this.action == ACTION_QUEST) {
                SurvivorQuestMenu.open(player, survivor);
            } else if (this.action == ACTION_RECRUIT) {
                survivor.endMenuInteraction(player);
                if (survivor.isRecruitOwner(player)) {
                    survivor.dismissRecruitOwner(player);
                } else {
                    survivor.recruit(player);
                }
            } else if (this.action == ACTION_PERSONAL) {
                if (!survivor.isRecruitOwner(player)) {
                    player.displayClientMessage(Component.translatable("message.echoes_of_survival.mode.not_recruited"), true);
                    return;
                }
                survivor.endMenuInteraction(player);
                SurvivorPersonalMenu.open(player, survivor);
            } else if (this.action == ACTION_CYCLE_PATROL) {
                if (!survivor.isRecruitOwner(player)) {
                    player.displayClientMessage(Component.translatable("message.echoes_of_survival.mode.not_recruited"), true);
                    return;
                }
                survivor.cyclePatrolMode();
                String key = "gui.echoes_of_survival.mode.patrol." + survivor.getPatrolMode().name().toLowerCase();
                player.displayClientMessage(Component.translatable("message.echoes_of_survival.mode.patrol", Component.translatable(key)), true);
            } else if (this.action == ACTION_CYCLE_ATTACK) {
                if (!survivor.isRecruitOwner(player)) {
                    player.displayClientMessage(Component.translatable("message.echoes_of_survival.mode.not_recruited"), true);
                    return;
                }
                survivor.cycleAttackMode();
                String key = "gui.echoes_of_survival.mode.attack." + survivor.getAttackMode().name().toLowerCase();
                player.displayClientMessage(Component.translatable("message.echoes_of_survival.mode.attack", Component.translatable(key)), true);
            } else if (this.action == ACTION_OVERLAY_OPEN) {
                survivor.beginOverlayInteraction(player);
            } else if (this.action == ACTION_OVERLAY_CLOSE) {
                survivor.endMenuInteraction(player);
            }
        });
    }
}
