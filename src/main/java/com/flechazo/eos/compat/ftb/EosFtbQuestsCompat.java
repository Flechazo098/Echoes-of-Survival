package com.flechazo.eos.compat.ftb;

import com.flechazo.eos.quest.QuestApi;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * Dependency-free integration boundary for FTB Quests and More Quest Types.
 * A dedicated integration mod or reflective event adapter may call these
 * methods without making FTB classes a hard dependency of EoS.
 */
public final class EosFtbQuestsCompat {
    private EosFtbQuestsCompat() {
    }

    public static boolean isFtbQuestsLoaded() {
        return ModList.get().isLoaded("ftbquests");
    }

    public static boolean bind(
            ServerPlayer player,
            UUID questInstanceUuid,
            String ftbQuestId,
            String ftbTaskId
    ) {
        return QuestApi.bindFtbQuest(player, questInstanceUuid, ftbQuestId, ftbTaskId);
    }

    public static boolean onTaskProgress(
            ServerPlayer player,
            UUID questInstanceUuid,
            String objectiveId,
            int amount
    ) {
        return QuestApi.advanceObjective(player, questInstanceUuid, objectiveId, amount);
    }
}
