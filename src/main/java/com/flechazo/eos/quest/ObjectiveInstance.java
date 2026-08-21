package com.flechazo.eos.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Mutable quest progress represented as an immutable persisted value. */
public record ObjectiveInstance(
        String objectiveId,
        ResourceLocation type,
        int progress,
        int required,
        boolean completed,
        CompoundTag contextData
) {
    public static final Codec<ObjectiveInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objective_id").forGetter(ObjectiveInstance::objectiveId),
            ResourceLocation.CODEC.fieldOf("type").forGetter(ObjectiveInstance::type),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(ObjectiveInstance::progress),
            Codec.INT.fieldOf("required").forGetter(ObjectiveInstance::required),
            Codec.BOOL.optionalFieldOf("completed", false).forGetter(ObjectiveInstance::completed),
            CompoundTag.CODEC.optionalFieldOf("context", new CompoundTag()).forGetter(ObjectiveInstance::contextData)
    ).apply(instance, ObjectiveInstance::new));

    public ObjectiveInstance withProgress(int value) {
        int normalized = Math.clamp(value, 0, required);
        return new ObjectiveInstance(objectiveId, type, normalized, required,
                normalized >= required, contextData.copy());
    }
}
