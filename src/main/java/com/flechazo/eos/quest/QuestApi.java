package com.flechazo.eos.quest;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.entity.AbstractSurvivorEntity;
import com.flechazo.eos.reputation.EosAttachments;
import com.flechazo.eos.reputation.ReputationEventService;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class QuestApi {
    private QuestApi() {
    }

    public static PlayerQuestState getState(ServerPlayer player) {
        PlayerQuestState state = player.getData(EosAttachments.PLAYER_QUESTS.get());
        if (state.instances().isEmpty() && !state.active().isEmpty()) {
            List<QuestInstance> migrated = new ArrayList<>();
            state.active().forEach((definitionId, progress) -> EosDatapackIndex.quest(definitionId).ifPresent(definition -> {
                QuestInstance instance = QuestInstance.create(definition, player.level().getGameTime(), null, null)
                        .withProgress(progress.objectiveProgress(), progress.claimed());
                migrated.add(instance);
            }));
            if (!migrated.isEmpty()) {
                state = new PlayerQuestState(state.active(), state.completions(), migrated);
                setState(player, state);
            }
        }
        return state;
    }

    private static void setState(ServerPlayer player, PlayerQuestState state) {
        player.setData(EosAttachments.PLAYER_QUESTS.get(), state);
    }

    public static boolean accept(ServerPlayer player, ResourceLocation questId) {
        return accept(player, questId, null);
    }

    public static boolean accept(ServerPlayer player, ResourceLocation questId, AbstractSurvivorEntity giver) {
        if (giver != null) ReputationEventService.ensureInitialTrust(player, giver);
        var defOpt = EosDatapackIndex.quest(questId);
        if (defOpt.isEmpty()) return false;
        QuestDefinition def = defOpt.get();

        if (def.repeatable() && def.maxRepeats() > 0) {
            int completions = getCompletions(player, questId);
            if (completions >= def.maxRepeats()) return false;
        }

        if (def.reputationGate().isDefined()) {
            int rep = ReputationEventService.global(player);
            int required = def.reputationGate().get().map(
                    v -> v,
                    tierName -> EosDatapackIndex.reputationTierByName(tierName)
                            .map(ReputationTiersDefinition.Tier::min)
                            .orElse(Integer.MAX_VALUE)
            );
            if (rep < required) return false;
        }

        PlayerQuestState state = getState(player);
        if (state.active().containsKey(questId)) return false;

        List<Integer> progress = new ArrayList<>(Collections.nCopies(def.objectives().size(), 0));
        PlayerQuestState.QuestProgress questProgress = new PlayerQuestState.QuestProgress(
                questId, List.copyOf(progress), false, false);
        QuestInstance questInstance = QuestInstance.create(
                def,
                player.level().getGameTime(),
                giver == null ? null : giver.getUUID(),
                giver == null ? null : giver.getAffiliationId()
        );
        setState(player, state.addInstance(questInstance, questProgress));
        updateWorldObjectives(player);
        return true;
    }

    public static boolean claim(ServerPlayer player, ResourceLocation questId) {
        var defOpt = EosDatapackIndex.quest(questId);
        if (defOpt.isEmpty()) return false;
        QuestDefinition def = defOpt.get();

        PlayerQuestState state = getState(player);
        PlayerQuestState.QuestProgress progress = state.active().get(questId);
        if (progress == null || !progress.completed() || progress.claimed()) return false;
        Optional<QuestInstance> questInstance = state.activeInstance(questId);

        for (ItemStack reward : def.rewards().items()) {
            ItemStack stack = reward.copy();
            if (stack.isEmpty()) continue;
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        ResourceLocation factionId = questInstance
                .map(instance -> ResourceLocation.tryParse(instance.contextData().getString("giver_faction")))
                .orElse(null);
        UUID giverUuid = questInstance.flatMap(QuestInstance::giverUuid).orElse(null);
        int reputationReward = def.rewards().reputation();
        ReputationEventService.apply(player, "complete_friendly_quest", reputationReward,
                factionId, reputationReward, giverUuid,
                giverUuid == null ? 0 : Math.clamp(Math.abs(reputationReward) / 2, 5, 20));

        Map<ResourceLocation, PlayerQuestState.QuestProgress> next = new HashMap<>(state.active());
        Map<ResourceLocation, Integer> nextCompletions = new HashMap<>(state.completions());
        nextCompletions.merge(questId, 1, Integer::sum);

        if (def.repeatable()) {
            next.remove(questId);
        } else {
            next.put(questId, new PlayerQuestState.QuestProgress(
                    progress.questId(),
                    progress.objectiveProgress(),
                    true,
                    true
            ));
        }

        setState(player, state.withActiveAndCompletions(next, Map.copyOf(nextCompletions)));
        return true;
    }

    public static int getCompletions(ServerPlayer player, ResourceLocation questId) {
        return getState(player).completions().getOrDefault(questId, 0);
    }

    /** Generic hook for EoS-specific objectives and optional quest-mod compatibility layers. */
    public static boolean advanceObjective(
            ServerPlayer player,
            UUID instanceUuid,
            String objectiveId,
            int amount
    ) {
        if (player == null || instanceUuid == null || objectiveId == null || amount <= 0) return false;
        PlayerQuestState state = getState(player);
        Optional<QuestInstance> instanceOptional = state.instance(instanceUuid);
        if (instanceOptional.isEmpty() || instanceOptional.get().claimed()) return false;
        QuestInstance instance = instanceOptional.get();
        PlayerQuestState.QuestProgress progress = state.active().get(instance.definitionId());
        if (progress == null || progress.completed()) return false;

        int objectiveIndex = -1;
        for (int i = 0; i < instance.objectives().size(); i++) {
            if (instance.objectives().get(i).objectiveId().equals(objectiveId)) {
                objectiveIndex = i;
                break;
            }
        }
        if (objectiveIndex < 0) return false;

        List<Integer> nextProgress = new ArrayList<>(progress.objectiveProgress());
        while (nextProgress.size() < instance.objectives().size()) nextProgress.add(0);
        ObjectiveInstance objective = instance.objectives().get(objectiveIndex);
        nextProgress.set(objectiveIndex, Math.min(objective.required(), nextProgress.get(objectiveIndex) + amount));
        boolean completed = true;
        for (int i = 0; i < instance.objectives().size(); i++) {
            if (nextProgress.get(i) < instance.objectives().get(i).required()) {
                completed = false;
                break;
            }
        }
        Map<ResourceLocation, PlayerQuestState.QuestProgress> nextActive = new HashMap<>(state.active());
        nextActive.put(instance.definitionId(), new PlayerQuestState.QuestProgress(
                instance.definitionId(), List.copyOf(nextProgress), completed, false));
        setState(player, state.withActive(nextActive));
        return true;
    }

    public static boolean bindFtbQuest(
            ServerPlayer player,
            UUID instanceUuid,
            String ftbQuestId,
            String ftbTaskId
    ) {
        PlayerQuestState state = getState(player);
        Optional<QuestInstance> instance = state.instance(instanceUuid);
        if (instance.isEmpty()) return false;
        setState(player, state.replaceInstance(instance.get().withFtbBinding(ftbQuestId, ftbTaskId)));
        return true;
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

            var defOpt = EosDatapackIndex.quest(questId);
            if (defOpt.isEmpty() || !defOpt.get().type().equals(QuestDefinition.TYPE_KILL_ENTITIES)) continue;
            QuestDefinition def = defOpt.get();

            List<Integer> objectiveProgress = new ArrayList<>(prog.objectiveProgress());
            boolean progressChanged = false;
            for (int i = 0; i < def.objectives().size(); i++) {
                QuestDefinition.Objective obj = def.objectives().get(i);
                if (obj == null || obj.entityTarget().isEmpty()) continue;
                ResourceLocation required = obj.entityTarget().get();
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
            setState(player, state.withActive(next));
        }
    }

    public static void updateWorldObjectives(ServerPlayer player) {
        if (player == null) return;

        PlayerQuestState state = getState(player);
        if (state.active().isEmpty()) return;

        boolean changed = false;
        Map<ResourceLocation, PlayerQuestState.QuestProgress> next = new HashMap<>(state.active());

        for (Map.Entry<ResourceLocation, PlayerQuestState.QuestProgress> entry : state.active().entrySet()) {
            ResourceLocation questId = entry.getKey();
            PlayerQuestState.QuestProgress progress = entry.getValue();
            if (progress == null || progress.completed()) continue;

            var definition = EosDatapackIndex.quest(questId);
            if (definition.isEmpty()) continue;
            QuestDefinition quest = definition.get();
            boolean reachesPosition = quest.type().equals(QuestDefinition.TYPE_REACH_POSITION)
                    || quest.type().equals(QuestDefinition.TYPE_VISIT_LOCATION);
            boolean exploresStructure = quest.type().equals(QuestDefinition.TYPE_EXPLORE_STRUCTURE)
                    || quest.type().equals(QuestDefinition.TYPE_VISIT_STRUCTURE);
            if (!reachesPosition && !exploresStructure) continue;

            List<Integer> objectiveProgress = normalizedProgress(progress, quest);
            boolean progressChanged = false;
            for (int i = 0; i < quest.objectives().size(); i++) {
                QuestDefinition.Objective objective = quest.objectives().get(i);
                if (objective == null || objectiveProgress.get(i) >= objective.count()) continue;

                boolean reached = reachesPosition
                        ? objective.positionTarget().map(target -> isAtPosition(player, target)).orElse(false)
                        : objective.structureTarget().map(target -> isInsideStructure(player, target)).orElse(false);
                if (!reached) continue;

                objectiveProgress.set(i, objective.count());
                progressChanged = true;
            }

            if (!progressChanged) continue;
            next.put(questId, new PlayerQuestState.QuestProgress(
                    questId,
                    List.copyOf(objectiveProgress),
                    isCompleted(quest, objectiveProgress),
                    progress.claimed()
            ));
            changed = true;
        }

        if (changed) {
            setState(player, state.withActive(next));
        }
    }

    private static boolean isAtPosition(ServerPlayer player, QuestDefinition.PositionTarget target) {
        if (!player.level().dimension().location().equals(target.dimension())) return false;
        Vec3 center = new Vec3(target.x() + 0.5D, target.y() + 0.5D, target.z() + 0.5D);
        return player.position().distanceToSqr(center) <= target.radius() * target.radius();
    }

    private static boolean isInsideStructure(ServerPlayer player, ResourceLocation structureId) {
        Registry<Structure> structures = player.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return structures.getHolder(structureId)
                .map(structure -> LocationPredicate.Builder.inStructure(structure)
                        .build()
                        .matches(player.serverLevel(), player.getX(), player.getY(), player.getZ()))
                .orElse(false);
    }

    private static List<Integer> normalizedProgress(
            PlayerQuestState.QuestProgress progress,
            QuestDefinition definition
    ) {
        List<Integer> result = new ArrayList<>(Collections.nCopies(definition.objectives().size(), 0));
        int copyCount = Math.min(result.size(), progress.objectiveProgress().size());
        for (int i = 0; i < copyCount; i++) {
            result.set(i, Math.max(0, progress.objectiveProgress().get(i)));
        }
        return result;
    }

    public static boolean submitItems(ServerPlayer player, ResourceLocation questId) {
        var defOpt = EosDatapackIndex.quest(questId);
        if (defOpt.isEmpty()) return false;
        QuestDefinition def = defOpt.get();
        if (!def.type().equals(QuestDefinition.TYPE_SUBMIT_ITEMS)) return false;

        PlayerQuestState state = getState(player);
        PlayerQuestState.QuestProgress prog = state.active().get(questId);
        if (prog == null || prog.completed()) return false;

        Inventory inv = player.getInventory();

        for (QuestDefinition.Objective obj : def.objectives()) {
            if (obj == null || obj.itemTarget().isEmpty()) continue;
            ResourceLocation itemId = obj.itemTarget().get();
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
            if (item.isEmpty()) return false;
            if (countItem(inv, item.get()) < obj.count()) return false;
        }

        for (QuestDefinition.Objective obj : def.objectives()) {
            if (obj == null || obj.itemTarget().isEmpty()) continue;
            ResourceLocation itemId = obj.itemTarget().get();
            BuiltInRegistries.ITEM.getOptional(itemId)
                    .ifPresent(item -> removeItem(inv, item, obj.count()));
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
        setState(player, state.withActive(next));
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
