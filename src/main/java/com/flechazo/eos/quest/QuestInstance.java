package com.flechazo.eos.quest;

import com.flechazo.eos.data.quest.QuestDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A concrete accepted quest, independent from its reloadable datapack template. */
public record QuestInstance(
        UUID instanceUuid,
        ResourceLocation definitionId,
        Optional<UUID> giverUuid,
        long acceptedTime,
        long expireTime,
        Optional<ResourceLocation> targetDimension,
        Optional<QuestDefinition.PositionTarget> targetPosition,
        Optional<ResourceLocation> targetStructure,
        Optional<UUID> targetEntityUuid,
        CompoundTag contextData,
        List<ObjectiveInstance> objectives,
        Optional<String> ftbQuestId,
        Optional<String> ftbTaskId,
        boolean claimed
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<QuestInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("instance_uuid").forGetter(QuestInstance::instanceUuid),
            ResourceLocation.CODEC.fieldOf("definition_id").forGetter(QuestInstance::definitionId),
            UUID_CODEC.optionalFieldOf("giver_uuid").forGetter(QuestInstance::giverUuid),
            Codec.LONG.fieldOf("accepted_time").forGetter(QuestInstance::acceptedTime),
            Codec.LONG.optionalFieldOf("expire_time", -1L).forGetter(QuestInstance::expireTime),
            ResourceLocation.CODEC.optionalFieldOf("target_dimension").forGetter(QuestInstance::targetDimension),
            QuestDefinition.PositionTarget.CODEC.optionalFieldOf("target_position").forGetter(QuestInstance::targetPosition),
            ResourceLocation.CODEC.optionalFieldOf("target_structure").forGetter(QuestInstance::targetStructure),
            UUID_CODEC.optionalFieldOf("target_entity_uuid").forGetter(QuestInstance::targetEntityUuid),
            CompoundTag.CODEC.optionalFieldOf("context_data", new CompoundTag()).forGetter(QuestInstance::contextData),
            ObjectiveInstance.CODEC.listOf().fieldOf("objectives").forGetter(QuestInstance::objectives),
            Codec.STRING.optionalFieldOf("ftb_quest_id").forGetter(QuestInstance::ftbQuestId),
            Codec.STRING.optionalFieldOf("ftb_task_id").forGetter(QuestInstance::ftbTaskId),
            Codec.BOOL.optionalFieldOf("claimed", false).forGetter(QuestInstance::claimed)
    ).apply(instance, QuestInstance::new));

    public static QuestInstance create(
            QuestDefinition definition,
            long acceptedTime,
            @Nullable UUID giverUuid,
            @Nullable ResourceLocation giverFaction
    ) {
        List<ObjectiveInstance> objectives = new ArrayList<>();
        QuestDefinition.PositionTarget position = null;
        ResourceLocation structure = null;
        for (int i = 0; i < definition.objectives().size(); i++) {
            QuestDefinition.Objective template = definition.objectives().get(i);
            CompoundTag context = new CompoundTag();
            template.itemTarget().ifPresent(value -> context.putString("item", value.toString()));
            template.entityTarget().ifPresent(value -> context.putString("entity", value.toString()));
            template.positionTarget().ifPresent(value -> {
                context.putString("dimension", value.dimension().toString());
                context.putInt("x", value.x());
                context.putInt("y", value.y());
                context.putInt("z", value.z());
                context.putDouble("radius", value.radius());
            });
            template.structureTarget().ifPresent(value -> context.putString("structure", value.toString()));
            template.blockTarget().ifPresent(value -> context.putString("block", value.toString()));
            objectives.add(new ObjectiveInstance("objective_" + i, definition.type(), 0,
                    template.count(), false, context));
            if (position == null && template.positionTarget().isDefined()) position = template.positionTarget().get();
            if (structure == null && template.structureTarget().isDefined()) structure = template.structureTarget().get();
        }
        CompoundTag instanceContext = new CompoundTag();
        if (giverFaction != null) instanceContext.putString("giver_faction", giverFaction.toString());
        return new QuestInstance(
                UUID.randomUUID(), definition.questId(), Optional.ofNullable(giverUuid), acceptedTime, -1L,
                Optional.ofNullable(position == null ? null : position.dimension()),
                Optional.ofNullable(position), Optional.ofNullable(structure), Optional.empty(),
                instanceContext, List.copyOf(objectives), Optional.empty(), Optional.empty(), false
        );
    }

    public boolean completed() {
        return !objectives.isEmpty() && objectives.stream().allMatch(ObjectiveInstance::completed);
    }

    public QuestInstance withProgress(List<Integer> progress, boolean claimed) {
        List<ObjectiveInstance> next = new ArrayList<>(objectives.size());
        for (int i = 0; i < objectives.size(); i++) {
            int value = i < progress.size() ? progress.get(i) : 0;
            next.add(objectives.get(i).withProgress(value));
        }
        return new QuestInstance(instanceUuid, definitionId, giverUuid, acceptedTime, expireTime,
                targetDimension, targetPosition, targetStructure, targetEntityUuid, contextData.copy(),
                List.copyOf(next), ftbQuestId, ftbTaskId, claimed);
    }

    public QuestInstance withFtbBinding(@Nullable String questId, @Nullable String taskId) {
        return new QuestInstance(instanceUuid, definitionId, giverUuid, acceptedTime, expireTime,
                targetDimension, targetPosition, targetStructure, targetEntityUuid, contextData.copy(),
                objectives, Optional.ofNullable(questId), Optional.ofNullable(taskId), claimed);
    }
}
