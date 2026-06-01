package com.flechazo.eos.reputation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ReputationApi {
    private ReputationApi() {
    }

    public static int get(Player player) {
        return player.getData(EosAttachments.PLAYER_REPUTATION.get());
    }

    public static void set(ServerPlayer player, int value) {
        player.setData(EosAttachments.PLAYER_REPUTATION.get(), value);
        player.syncData(EosAttachments.PLAYER_REPUTATION.get());
    }

    public static void add(ServerPlayer player, int delta) {
        int next = get(player) + delta;
        set(player, next);
    }
}

