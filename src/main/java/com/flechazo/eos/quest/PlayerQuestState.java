package com.flechazo.eos.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record PlayerQuestState(Map<ResourceLocation, QuestProgress> active, Map<ResourceLocation, Integer> completions) {
    public static final Codec<PlayerQuestState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC)
                    .optionalFieldOf("active", Map.of())
                    .forGetter(PlayerQuestState::active),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .optionalFieldOf("completions", Map.of())
                    .forGetter(PlayerQuestState::completions)
    ).apply(instance, PlayerQuestState::new));

    public static PlayerQuestState empty() {
        return new PlayerQuestState(new HashMap<>(), new HashMap<>());
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
