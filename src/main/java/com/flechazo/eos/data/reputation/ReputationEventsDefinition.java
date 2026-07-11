package com.flechazo.eos.data.reputation;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.flechazo.eos.util.IntValueOrRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@DataDriven(
        folder = "survivor_reputation_events",
        syncToClient = true,
        validator = ReputationEventsDefinition.Validator.class
)
public record ReputationEventsDefinition(List<ReputationEvent> events) {
    public static final Codec<ReputationEventsDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ReputationEvent.CODEC.listOf().fieldOf("events").forGetter(ReputationEventsDefinition::events)
    ).apply(instance, ReputationEventsDefinition::new));

    public record ReputationEvent(String id, IntValueOrRange change) {
        public static final Codec<ReputationEvent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(ReputationEvent::id),
                IntValueOrRange.CODEC.fieldOf("change").forGetter(ReputationEvent::change)
        ).apply(instance, ReputationEvent::new));
    }

    public static final class Validator implements DataValidator<ReputationEventsDefinition> {
        @Override
        public ValidationResult validate(ReputationEventsDefinition data, ResourceLocation source) {
            if (data == null || data.events == null || data.events.isEmpty()) {
                return ValidationResult.failure("'events' must not be empty");
            }
            for (ReputationEvent event : data.events) {
                if (event == null || event.id == null || event.id.isBlank()) {
                    return ValidationResult.failure("event id must not be blank");
                }
            }
            return ValidationResult.success();
        }
    }
}

