package com.flechazo.eos.data.quest;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@DataDriven(
        folder = "survivor_quest_pools",
        syncToClient = true,
        validator = QuestPoolDefinition.Validator.class
)
public record QuestPoolDefinition(int rolls, List<ResourceLocation> quests) {
    public static final Codec<QuestPoolDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rolls", 1).forGetter(QuestPoolDefinition::rolls),
            ResourceLocation.CODEC.listOf().fieldOf("quests").forGetter(QuestPoolDefinition::quests)
    ).apply(instance, QuestPoolDefinition::new));

    public static final class Validator implements DataValidator<QuestPoolDefinition> {
        @Override
        public ValidationResult validate(QuestPoolDefinition data, ResourceLocation source) {
            if (data == null || data.quests == null || data.quests.isEmpty()) {
                return ValidationResult.failure("'quests' must not be empty");
            }
            if (data.rolls <= 0) {
                return ValidationResult.failure("'rolls' must be > 0");
            }
            return ValidationResult.success();
        }
    }
}

