package com.flechazo.eos.data.quest;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.flechazo.eos.data.common.ItemStackDef;
import com.flechazo.eos.data.common.TextKey;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

@DataDriven(
        folder = "survivor_quests",
        syncToClient = true,
        validator = QuestDefinition.Validator.class
)
public record QuestDefinition(
        ResourceLocation questId,
        TextKey title,
        TextKey description,
        ResourceLocation type,
        Optional<Either<Integer, String>> requireReputation,
        List<Objective> objectives,
        Rewards rewards
) {
    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest_id").forGetter(QuestDefinition::questId),
            TextKey.CODEC.fieldOf("title").forGetter(QuestDefinition::title),
            TextKey.CODEC.fieldOf("description").forGetter(QuestDefinition::description),
            ResourceLocation.CODEC.fieldOf("type").forGetter(QuestDefinition::type),
            Codec.either(Codec.INT, Codec.STRING).optionalFieldOf("require_reputation").forGetter(QuestDefinition::requireReputation),
            Objective.CODEC.listOf().fieldOf("objectives").forGetter(QuestDefinition::objectives),
            Rewards.CODEC.fieldOf("rewards").forGetter(QuestDefinition::rewards)
    ).apply(instance, QuestDefinition::new));

    public static final ResourceLocation TYPE_SUBMIT_ITEMS = ResourceLocation.fromNamespaceAndPath("echoes", "submit_items");
    public static final ResourceLocation TYPE_KILL_ENTITIES = ResourceLocation.fromNamespaceAndPath("echoes", "kill_entities");

    public record Objective(
            Optional<ResourceLocation> item,
            Optional<ResourceLocation> entity,
            int count
    ) {
        public static final Codec<Objective> CODEC = RecordCodecBuilder.create(
                (RecordCodecBuilder.Instance<Objective> instance) -> instance.group(
                        ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Objective::item),
                        ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(Objective::entity),
                        Codec.INT.optionalFieldOf("count", 1).forGetter(Objective::count)
                ).apply(instance, Objective::new)
        );
    }

    public record Rewards(
            List<ItemStackDef> items,
            int reputation
    ) {
        public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStackDef.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Rewards::items),
                Codec.INT.optionalFieldOf("reputation", 0).forGetter(Rewards::reputation)
        ).apply(instance, Rewards::new));
    }

    public static final class Validator implements DataValidator<QuestDefinition> {
        @Override
        public ValidationResult validate(QuestDefinition data, ResourceLocation source) {
            if (data == null) return ValidationResult.failure("quest is null");
            if (data.questId == null) return ValidationResult.failure("quest_id is required");
            if (data.type == null) return ValidationResult.failure("type is required");
            if (data.objectives == null || data.objectives.isEmpty()) {
                return ValidationResult.failure("objectives must not be empty");
            }

            boolean submit = data.type.equals(TYPE_SUBMIT_ITEMS);
            boolean kill = data.type.equals(TYPE_KILL_ENTITIES);
            if (!submit && !kill) {
                return ValidationResult.failure("unknown quest type: " + data.type);
            }

            for (Objective obj : data.objectives) {
                if (obj == null) return ValidationResult.failure("objective is null");
                boolean hasItem = obj.item().isPresent();
                boolean hasEntity = obj.entity().isPresent();
                if (hasItem == hasEntity) {
                    return ValidationResult.failure("objective must have exactly one of 'item' or 'entity'");
                }
                if (obj.count() <= 0) {
                    return ValidationResult.failure("objective 'count' must be > 0");
                }
                if (submit && obj.item().isEmpty()) return ValidationResult.failure("submit_items objective requires 'item'");
                if (kill && obj.entity().isEmpty()) return ValidationResult.failure("kill_entities objective requires 'entity'");
            }

            return ValidationResult.success();
        }
    }
}
