package com.flechazo.eos.quest;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.reputation.EosAttachments;
import com.flechazo.eos.reputation.ReputationApi;
import com.flechazo.eos.util.EosAliases;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class QuestApi {
    private QuestApi() {
    }

    public static PlayerQuestState getState(ServerPlayer player) {
        return player.getData(EosAttachments.PLAYER_QUESTS.get());
    }

    private static void setState(ServerPlayer player, PlayerQuestState state) {
        player.setData(EosAttachments.PLAYER_QUESTS.get(), state);
    }

    public static boolean accept(ServerPlayer player, ResourceLocation questId) {
        QuestDefinition def = EosDatapackIndex.quest(questId).orElse(null);
        if (def == null) return false;

        if (def.repeatable() && def.maxRepeats() > 0) {
            int completions = getCompletions(player, questId);
            if (completions >= def.maxRepeats()) return false;
        }

        if (def.reputationGate().isDefined()) {
            int rep = ReputationApi.get(player);
            int required = def.reputationGate().get().map(
                    v -> v,
                    tierName -> EosDatapackIndex.reputationTierByName(tierName)
                            .map(ReputationTiersDefinition.Tier::min)
                            .orElse(Integer.MAX_VALUE)
            );
            if (rep < required) return false;
        }

        PlayerQuestState state = getState(player);
        Map<ResourceLocation, PlayerQuestState.QuestProgress> next = new HashMap<>(state.active());
        if (next.containsKey(questId)) return false;

        List<Integer> progress = new ArrayList<>(Collections.nCopies(def.objectives().size(), 0));
        next.put(questId, new PlayerQuestState.QuestProgress(questId, List.copyOf(progress), false, false));
        setState(player, new PlayerQuestState(next, state.completions()));
        return true;
    }

    public static boolean claim(ServerPlayer player, ResourceLocation questId) {
        QuestDefinition def = EosDatapackIndex.quest(questId).orElse(null);
        if (def == null) return false;

        PlayerQuestState state = getState(player);
        PlayerQuestState.QuestProgress progress = state.active().get(questId);
        if (progress == null || !progress.completed() || progress.claimed()) return false;

        for (ItemStack reward : def.rewards().items()) {
            ItemStack stack = reward.copy();
            if (stack.isEmpty()) continue;
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        if (def.rewards().reputation() != 0) {
            ReputationApi.add(player, def.rewards().reputation());
        }

        Map<ResourceLocation, PlayerQuestState.QuestProgress> next = new HashMap<>(state.active());
        Map<ResourceLocation, Integer> nextCompletions = new HashMap<>(state.completions());

        if (def.repeatable()) {
            next.remove(questId);
            if (def.maxRepeats() > 0) {
                nextCompletions.merge(questId, 1, Integer::sum);
            }
        } else {
            next.put(questId, new PlayerQuestState.QuestProgress(
                    progress.questId(),
                    progress.objectiveProgress(),
                    true,
                    true
            ));
        }

        setState(player, new PlayerQuestState(next, Map.copyOf(nextCompletions)));
        return true;
    }

    public static int getCompletions(ServerPlayer player, ResourceLocation questId) {
        return getState(player).completions().getOrDefault(questId, 0);
    }

    public static void onKill(ServerPlayer player, LivingEntity killed) {
        if (player == null || killed == null) return;

        ResourceLocation killedType = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());

        PlayerQuestState state = getState(player);
        if (state.active().isEmpty()) return;

        boolean changed = false;
        Map<ResourceLocation, PlayerQuestState.QuestProgress> next = new HashMap<>(state.active());

        for (Map.Entry<ResourceLocation, PlayerQuestState.QuestProgress> entry : state.active().entrySet()) {
            ResourceLocation questId = entry.getKey();
            PlayerQuestState.QuestProgress prog = entry.getValue();
            if (prog == null || prog.completed()) continue;

            QuestDefinition def = EosDatapackIndex.quest(questId).orElse(null);
            if (def == null || !def.type().equals(QuestDefinition.TYPE_KILL_ENTITIES)) continue;

            List<Integer> objectiveProgress = new ArrayList<>(prog.objectiveProgress());
            boolean progressChanged = false;
            for (int i = 0; i < def.objectives().size(); i++) {
                QuestDefinition.Objective obj = def.objectives().get(i);
                if (obj == null || obj.entityTarget().isEmpty()) continue;
                ResourceLocation required = EosAliases.normalizeToModNamespace(obj.entityTarget().get());
                if (!required.equals(killedType)) continue;

                int current = objectiveProgress.get(i);
                int nextValue = Math.min(obj.count(), current + 1);
                if (nextValue != current) {
                    objectiveProgress.set(i, nextValue);
                    progressChanged = true;
                }
            }

            if (!progressChanged) continue;

            boolean completed = isCompleted(def, objectiveProgress);
            next.put(questId, new PlayerQuestState.QuestProgress(
                    questId,
                    List.copyOf(objectiveProgress),
                    completed,
                    prog.claimed()
            ));
            changed = true;
        }

        if (changed) {
            setState(player, new PlayerQuestState(next, state.completions()));
        }
    }

    public static boolean submitItems(ServerPlayer player, ResourceLocation questId) {
        QuestDefinition def = EosDatapackIndex.quest(questId).orElse(null);
        if (def == null || !def.type().equals(QuestDefinition.TYPE_SUBMIT_ITEMS)) return false;

        PlayerQuestState state = getState(player);
        PlayerQuestState.QuestProgress prog = state.active().get(questId);
        if (prog == null || prog.completed()) return false;

        Inventory inv = player.getInventory();

        for (QuestDefinition.Objective obj : def.objectives()) {
            if (obj == null || obj.itemTarget().isEmpty()) continue;
            ResourceLocation itemId = obj.itemTarget().get();
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null) return false;
            if (countItem(inv, item) < obj.count()) return false;
        }

        for (QuestDefinition.Objective obj : def.objectives()) {
            if (obj == null || obj.itemTarget().isEmpty()) continue;
            ResourceLocation itemId = obj.itemTarget().get();
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null) continue;
            removeItem(inv, item, obj.count());
        }

        List<Integer> objectiveProgress = new ArrayList<>(Collections.nCopies(def.objectives().size(), 0));
        for (int i = 0; i < def.objectives().size(); i++) {
            QuestDefinition.Objective obj = def.objectives().get(i);
            if (obj != null && obj.itemTarget().isDefined()) {
                objectiveProgress.set(i, obj.count());
            }
        }

        Map<ResourceLocation, PlayerQuestState.QuestProgress> next = new HashMap<>(state.active());
        next.put(questId, new PlayerQuestState.QuestProgress(
                questId,
                List.copyOf(objectiveProgress),
                true,
                prog.claimed()
        ));
        setState(player, new PlayerQuestState(next, state.completions()));
        return true;
    }

    private static boolean isCompleted(QuestDefinition def, List<Integer> progress) {
        if (progress.size() != def.objectives().size()) return false;
        for (int i = 0; i < def.objectives().size(); i++) {
            QuestDefinition.Objective obj = def.objectives().get(i);
            if (obj == null) return false;
            if (progress.get(i) < obj.count()) return false;
        }
        return true;
    }

    private static int countItem(Inventory inv, Item item) {
        int count = 0;
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeItem(Inventory inv, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        inv.setChanged();
    }
}
