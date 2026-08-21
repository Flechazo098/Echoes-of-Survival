package com.flechazo.eos.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlayerQuestState(
        Map<ResourceLocation, QuestProgress> active,
        Map<ResourceLocation, Integer> completions,
        List<QuestInstance> instances
) {
    public static final Codec<PlayerQuestState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC)
                    .optionalFieldOf("active", Map.of())
                    .forGetter(PlayerQuestState::active),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .optionalFieldOf("completions", Map.of())
                    .forGetter(PlayerQuestState::completions),
            QuestInstance.CODEC.listOf().optionalFieldOf("instances", List.of())
                    .forGetter(PlayerQuestState::instances)
    ).apply(instance, PlayerQuestState::new));

    public PlayerQuestState {
        active = Map.copyOf(active);
        completions = Map.copyOf(completions);
        instances = List.copyOf(instances);
    }

    public static PlayerQuestState empty() {
        return new PlayerQuestState(new HashMap<>(), new HashMap<>(), List.of());
    }

    public PlayerQuestState withActive(Map<ResourceLocation, QuestProgress> nextActive) {
        return withActiveAndCompletions(nextActive, completions);
    }

    public PlayerQuestState withActiveAndCompletions(
            Map<ResourceLocation, QuestProgress> nextActive,
        Map<ResourceLocation, Integer> nextCompletions
    ) {
        List<QuestInstance> nextInstances = instances.stream().map(instance -> {
            if (instance.claimed()) return instance;
            QuestProgress progress = nextActive.get(instance.definitionId());
            if (progress == null) {
                QuestProgress previous = active.get(instance.definitionId());
                return previous == null ? instance : instance.withProgress(previous.objectiveProgress(), true);
            }
            return instance.withProgress(progress.objectiveProgress(), progress.claimed());
        }).toList();
        return new PlayerQuestState(nextActive, nextCompletions, nextInstances);
    }

    public PlayerQuestState addInstance(QuestInstance instance, QuestProgress progress) {
        Map<ResourceLocation, QuestProgress> nextActive = new HashMap<>(active);
        nextActive.put(instance.definitionId(), progress);
        List<QuestInstance> nextInstances = new java.util.ArrayList<>(instances);
        nextInstances.add(instance);
        return new PlayerQuestState(nextActive, completions, nextInstances);
    }

    public java.util.Optional<QuestInstance> activeInstance(ResourceLocation definitionId) {
        return instances.stream()
                .filter(instance -> instance.definitionId().equals(definitionId) && !instance.claimed())
                .findFirst();
    }

    public java.util.Optional<QuestInstance> instance(UUID instanceUuid) {
        return instances.stream().filter(instance -> instance.instanceUuid().equals(instanceUuid)).findFirst();
    }

    public PlayerQuestState replaceInstance(QuestInstance replacement) {
        List<QuestInstance> next = instances.stream()
                .map(instance -> instance.instanceUuid().equals(replacement.instanceUuid()) ? replacement : instance)
                .toList();
        return new PlayerQuestState(active, completions, next);
    }

    public record QuestProgress(
            ResourceLocation questId,
            List<Integer> objectiveProgress,
            boolean completed,
            boolean claimed
    ) {
        public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("questId").forGetter(QuestProgress::questId),
                Codec.INT.listOf().fieldOf("objectiveProgress").forGetter(QuestProgress::objectiveProgress),
                Codec.BOOL.optionalFieldOf("completed", false).forGetter(QuestProgress::completed),
                Codec.BOOL.optionalFieldOf("claimed", false).forGetter(QuestProgress::claimed)
        ).apply(instance, QuestProgress::new));
    }
}
