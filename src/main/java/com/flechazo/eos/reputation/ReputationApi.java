package com.flechazo.eos.reputation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ReputationApi {
    private ReputationApi() {
    }

    public static int get(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ReputationEventService.global(serverPlayer);
        }
        return player.getData(EosAttachments.PLAYER_REPUTATION.get());
    }

    public static void set(ServerPlayer player, int value) {
        ReputationEventService.setGlobal(player, value, "legacy_api_set");
    }

    public static void add(ServerPlayer player, int delta) {
        ReputationEventService.apply(player, "legacy_api_add", delta, null, 0, null, 0);
    }
}
