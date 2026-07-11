package com.flechazo.eos.data.bubble;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@DataDriven(
        folder = "survivor_bubbles",
        syncToClient = false,
        validator = SurvivorBubbleDefinition.Validator.class
)
public record SurvivorBubbleDefinition(
        ResourceLocation id,
        SurvivorType survivorType,
        Map<String, BubbleEntry> combat,
        Map<String, BubbleEntry> environment,
        Map<String, BubbleEntry> interaction,
        Map<String, BubbleEntry> status
) {
    private static final Codec<Map<String, BubbleEntry>> EVENT_MAP_CODEC = Codec.unboundedMap(Codec.STRING, BubbleEntry.CODEC);

    public static final Codec<SurvivorBubbleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(SurvivorBubbleDefinition::id),
            SurvivorType.CODEC.fieldOf("survivor_type").forGetter(SurvivorBubbleDefinition::survivorType),
            EVENT_MAP_CODEC.optionalFieldOf("combat", Map.of()).forGetter(SurvivorBubbleDefinition::combat),
            EVENT_MAP_CODEC.optionalFieldOf("environment", Map.of()).forGetter(SurvivorBubbleDefinition::environment),
            EVENT_MAP_CODEC.optionalFieldOf("interaction", Map.of()).forGetter(SurvivorBubbleDefinition::interaction),
            EVENT_MAP_CODEC.optionalFieldOf("status", Map.of()).forGetter(SurvivorBubbleDefinition::status)
    ).apply(instance, SurvivorBubbleDefinition::new));


    public enum SurvivorType {
        FRIENDLY("friendly"), NEUTRAL("neutral"), HOSTILE("hostile");

        public static final Codec<SurvivorType> CODEC = Codec.STRING.comapFlatMap(
                value -> java.util.Arrays.stream(values())
                        .filter(type -> type.serializedName.equals(value))
                        .findFirst()
                        .map(com.mojang.serialization.DataResult::success)
                        .orElseGet(() -> com.mojang.serialization.DataResult.error(
                                () -> "Unknown survivor_type '" + value + "'")),
                SurvivorType::serializedName);

        private final String serializedName;

        SurvivorType(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }
    public record BubbleEntry(double chance, int cooldown, int duration, List<String> keys) {
        public static final Codec<BubbleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(BubbleEntry::chance),
                Codec.INT.optionalFieldOf("cooldown", 200).forGetter(BubbleEntry::cooldown),
                Codec.INT.optionalFieldOf("duration", 200).forGetter(BubbleEntry::duration),
                Codec.STRING.listOf().fieldOf("keys").forGetter(BubbleEntry::keys)
        ).apply(instance, BubbleEntry::new));
    }

    public static final class Validator implements DataValidator<SurvivorBubbleDefinition> {
        @Override
        public ValidationResult validate(SurvivorBubbleDefinition data, ResourceLocation source) {
            if (data == null) return ValidationResult.failure("survivor bubble definition is null");
            if (data.id == null) return ValidationResult.failure("id is required");
            if (data.survivorType == null) return ValidationResult.failure("survivor_type is required");
            if (data.survivorType != SurvivorType.FRIENDLY
                    && (!data.interaction.isEmpty() || !data.status.isEmpty())) {
                return ValidationResult.failure("interaction and status bubbles are only valid for friendly survivors");
            }
            for (Map.Entry<String, BubbleEntry> event : data.allEvents().entrySet()) {
                if (event.getKey() == null || event.getKey().isBlank()) return ValidationResult.failure("bubble event name is blank");
                BubbleEntry entry = event.getValue();
                if (entry == null) return ValidationResult.failure("bubble event '" + event.getKey() + "' is null");
                if (entry.cooldown() < 0) return ValidationResult.failure("bubble event '" + event.getKey() + "' cooldown must be >= 0");
                if (entry.duration() <= 0) return ValidationResult.failure("bubble event '" + event.getKey() + "' duration must be > 0");
                if (entry.keys() == null || entry.keys().isEmpty() || entry.keys().stream().anyMatch(key -> key == null || key.isBlank())) {
                    return ValidationResult.failure("bubble event '" + event.getKey() + "' must contain non-blank keys");
                }
            }
            return ValidationResult.success();
        }
    }

    private Map<String, BubbleEntry> allEvents() {
        java.util.HashMap<String, BubbleEntry> result = new java.util.HashMap<>();
        result.putAll(combat);
        result.putAll(environment);
        result.putAll(interaction);
        result.putAll(status);
        return result;
    }
}
