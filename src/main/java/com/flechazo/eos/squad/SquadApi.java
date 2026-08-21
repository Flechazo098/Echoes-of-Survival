package com.flechazo.eos.squad;

import com.flechazo.eos.config.EosConfigs;
import com.flechazo.eos.reputation.EosAttachments;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Public squad-slot API. External rank systems may wrap this policy later. */
public final class SquadApi {
    private SquadApi() {
    }

    public static int maxFollowingSurvivors(ServerPlayer player) {
        return EosConfigs.SURVIVOR.get().maxFollowingSurvivors();
    }

    public static int usedSlots(ServerPlayer player) {
        return player.getData(EosAttachments.PLAYER_SQUAD.get()).members().size();
    }

    public static boolean hasAvailableSlot(ServerPlayer player) {
        return usedSlots(player) < maxFollowingSurvivors(player);
    }

    public static void addMember(ServerPlayer player, UUID survivorUuid) {
        Set<UUID> next = new LinkedHashSet<>(player.getData(EosAttachments.PLAYER_SQUAD.get()).members());
        next.add(survivorUuid);
        player.setData(EosAttachments.PLAYER_SQUAD.get(), new SquadState(next));
    }

    public static void removeMember(ServerPlayer player, UUID survivorUuid) {
        Set<UUID> next = new LinkedHashSet<>(player.getData(EosAttachments.PLAYER_SQUAD.get()).members());
        next.remove(survivorUuid);
        player.setData(EosAttachments.PLAYER_SQUAD.get(), new SquadState(next));
    }
}
