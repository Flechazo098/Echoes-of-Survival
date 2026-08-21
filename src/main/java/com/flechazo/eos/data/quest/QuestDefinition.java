package com.flechazo.eos.data.quest;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.data.common.TextKey;
import com.flechazo.eos.util.CodecUtil;
import com.flechazo.hkt.Maybe;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

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
        Maybe<Either<Integer, String>> reputationGate,
        List<Objective> objectives,
        Rewards rewards,
        boolean repeatable,
        int maxRepeats
) {
    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest_id").forGetter(QuestDefinition::questId),
            TextKey.CODEC.fieldOf("title").forGetter(QuestDefinition::title),
            TextKey.CODEC.fieldOf("description").forGetter(QuestDefinition::description),
            ResourceLocation.CODEC.fieldOf("type").forGetter(QuestDefinition::type),
            CodecUtil.maybeFieldCodec("require_reputation", Codec.either(Codec.INT, Codec.STRING)).forGetter(QuestDefinition::reputationGate),
            Objective.CODEC.listOf().fieldOf("objectives").forGetter(QuestDefinition::objectives),
            Rewards.CODEC.fieldOf("rewards").forGetter(QuestDefinition::rewards),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(QuestDefinition::repeatable),
            Codec.INT.optionalFieldOf("max_repeats", 0).forGetter(QuestDefinition::maxRepeats)
    ).apply(instance, QuestDefinition::new));

    public static final ResourceLocation TYPE_SUBMIT_ITEMS = ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "submit_items");
    public static final ResourceLocation TYPE_KILL_ENTITIES = ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "kill_entities");
    public static final ResourceLocation TYPE_REACH_POSITION = ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "reach_position");
    public static final ResourceLocation TYPE_EXPLORE_STRUCTURE = ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "explore_structure");

    public record Objective(
            Maybe<ResourceLocation> itemTarget,
            Maybe<ResourceLocation> entityTarget,
            Maybe<CompoundTag> entityNbt,
            Maybe<PositionTarget> positionTarget,
            Maybe<ResourceLocation> structureTarget,
            int count
    ) {
        public static final Codec<Objective> CODEC = RecordCodecBuilder.create(
                (RecordCodecBuilder.Instance<Objective> instance) -> instance.group(
                        CodecUtil.maybeFieldCodec("item", ResourceLocation.CODEC).forGetter(Objective::itemTarget),
                        CodecUtil.maybeFieldCodec("entity", ResourceLocation.CODEC).forGetter(Objective::entityTarget),
                        CodecUtil.maybeFieldCodec("entity_nbt", CompoundTag.CODEC).forGetter(Objective::entityNbt),
                        CodecUtil.maybeFieldCodec("position", PositionTarget.CODEC).forGetter(Objective::positionTarget),
                        CodecUtil.maybeFieldCodec("structure", ResourceLocation.CODEC).forGetter(Objective::structureTarget),
                        Codec.INT.optionalFieldOf("count", 1).forGetter(Objective::count)
                ).apply(instance, Objective::new)
        );
    }

    public record PositionTarget(
            ResourceLocation dimension,
            int x,
            int y,
            int z,
            double radius
    ) {
        public static final Codec<PositionTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(PositionTarget::dimension),
                Codec.INT.fieldOf("x").forGetter(PositionTarget::x),
                Codec.INT.fieldOf("y").forGetter(PositionTarget::y),
                Codec.INT.fieldOf("z").forGetter(PositionTarget::z),
                Codec.DOUBLE.optionalFieldOf("radius", 3.0D).forGetter(PositionTarget::radius)
        ).apply(instance, PositionTarget::new));
    }

    public record Rewards(
            List<ItemStack> items,
            int reputation
    ) {
        public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Rewards::items),
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
            boolean reachPosition = data.type.equals(TYPE_REACH_POSITION);
            boolean exploreStructure = data.type.equals(TYPE_EXPLORE_STRUCTURE);
            if (!submit && !kill && !reachPosition && !exploreStructure) {
                return ValidationResult.failure("unknown quest type: " + data.type);
            }

            for (Objective obj : data.objectives) {
                if (obj == null) return ValidationResult.failure("objective is null");
                boolean hasItem = obj.itemTarget().isDefined();
                boolean hasEntity = obj.entityTarget().isDefined();
                boolean hasPosition = obj.positionTarget().isDefined();
                boolean hasStructure = obj.structureTarget().isDefined();
                int targetCount = (hasItem ? 1 : 0) + (hasEntity ? 1 : 0)
                        + (hasPosition ? 1 : 0) + (hasStructure ? 1 : 0);
                if (targetCount != 1) {
                    return ValidationResult.failure(
                            "objective must have exactly one of 'item', 'entity', 'position', or 'structure'"
                    );
                }
                if (obj.entityNbt().isDefined() && !hasEntity) {
                    return ValidationResult.failure("objective 'entity_nbt' requires 'entity'");
                }
                if (obj.count() <= 0) {
                    return ValidationResult.failure("objective 'count' must be > 0");
                }
                if (hasPosition) {
                    PositionTarget position = obj.positionTarget().get();
                    if (position.dimension() == null) {
                        return ValidationResult.failure("position target requires 'dimension'");
                    }
                    if (!Double.isFinite(position.radius()) || position.radius() <= 0.0D) {
                        return ValidationResult.failure("position target 'radius' must be finite and > 0");
                    }
                }
                if (submit && obj.itemTarget().isEmpty())
                    return ValidationResult.failure("submit_items objective requires 'item'");
                if (kill && obj.entityTarget().isEmpty())
                    return ValidationResult.failure("kill_entities objective requires 'entity'");
                if (reachPosition && obj.positionTarget().isEmpty())
                    return ValidationResult.failure("reach_position objective requires 'position'");
                if (exploreStructure && obj.structureTarget().isEmpty())
                    return ValidationResult.failure("explore_structure objective requires 'structure'");
                if ((reachPosition || exploreStructure) && obj.count() != 1)
                    return ValidationResult.failure("reach_position and explore_structure objective 'count' must be 1");
            }

            return ValidationResult.success();
        }
    }
}
