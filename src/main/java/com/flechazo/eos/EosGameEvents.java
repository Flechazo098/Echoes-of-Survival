package com.flechazo.eos;

import com.flechazo.eos.quest.QuestApi;
import com.flechazo.eos.reputation.EosAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class EosGameEvents {
    private EosGameEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EosGameEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(EosGameEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EosGameEvents::onPlayerLogin);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        EosCommands.register(event.getDispatcher());
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() == null) return;
        QuestApi.onKill(player, event.getEntity());
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.syncData(EosAttachments.PLAYER_REPUTATION.get());
        }
    }
}

