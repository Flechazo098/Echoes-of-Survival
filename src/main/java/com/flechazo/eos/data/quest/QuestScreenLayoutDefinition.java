package com.flechazo.eos.data.quest;

import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.flechazo.eos.util.CodecUtil;
import com.flechazo.hkt.Maybe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

@DataDriven(
        folder = "survivor_quest_layouts",
        syncToClient = true,
        validator = QuestScreenLayoutDefinition.Validator.class
)
public record QuestScreenLayoutDefinition(
        ResourceLocation questId,
        Maybe<ContentLayout> content,
        Maybe<TextLayout> title,
        Maybe<TextLayout> description,
        Maybe<ObjectiveLayout> objectives,
        Maybe<TextLayout> reputationRequirement,
        Maybe<RewardLayout> rewards
) {
    public static final Codec<QuestScreenLayoutDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest_id").forGetter(QuestScreenLayoutDefinition::questId),
            CodecUtil.maybeFieldCodec("content", ContentLayout.CODEC).forGetter(QuestScreenLayoutDefinition::content),
            CodecUtil.maybeFieldCodec("title", TextLayout.CODEC).forGetter(QuestScreenLayoutDefinition::title),
            CodecUtil.maybeFieldCodec("description", TextLayout.CODEC).forGetter(QuestScreenLayoutDefinition::description),
            CodecUtil.maybeFieldCodec("objectives", ObjectiveLayout.CODEC).forGetter(QuestScreenLayoutDefinition::objectives),
            CodecUtil.maybeFieldCodec("reputation_requirement", TextLayout.CODEC).forGetter(QuestScreenLayoutDefinition::reputationRequirement),
            CodecUtil.maybeFieldCodec("rewards", RewardLayout.CODEC).forGetter(QuestScreenLayoutDefinition::rewards)
    ).apply(instance, QuestScreenLayoutDefinition::new));

    public record ContentLayout(
            Maybe<Integer> xOffset,
            Maybe<Integer> width,
            Maybe<Integer> topOffset,
            Maybe<Integer> bottomOffset,
            Maybe<Integer> scrollStep
    ) {
        public static final Codec<ContentLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CodecUtil.maybeFieldCodec("x_offset", Codec.INT).forGetter(ContentLayout::xOffset),
                CodecUtil.maybeFieldCodec("width", Codec.INT).forGetter(ContentLayout::width),
                CodecUtil.maybeFieldCodec("top_offset", Codec.INT).forGetter(ContentLayout::topOffset),
                CodecUtil.maybeFieldCodec("bottom_offset", Codec.INT).forGetter(ContentLayout::bottomOffset),
                CodecUtil.maybeFieldCodec("scroll_step", Codec.INT).forGetter(ContentLayout::scrollStep)
        ).apply(instance, ContentLayout::new));
    }

    public record TextLayout(
            Maybe<Integer> xOffset,
            Maybe<Integer> yOffset,
            Maybe<Integer> width,
            Maybe<Float> scale,
            Maybe<Integer> lineSpacing,
            Maybe<Integer> color,
            Maybe<Integer> bottomGap
    ) {
        public static final Codec<TextLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CodecUtil.maybeFieldCodec("x_offset", Codec.INT).forGetter(TextLayout::xOffset),
                CodecUtil.maybeFieldCodec("y_offset", Codec.INT).forGetter(TextLayout::yOffset),
                CodecUtil.maybeFieldCodec("width", Codec.INT).forGetter(TextLayout::width),
                CodecUtil.maybeFieldCodec("scale", Codec.FLOAT).forGetter(TextLayout::scale),
                CodecUtil.maybeFieldCodec("line_spacing", Codec.INT).forGetter(TextLayout::lineSpacing),
                CodecUtil.maybeFieldCodec("color", Codec.INT).forGetter(TextLayout::color),
                CodecUtil.maybeFieldCodec("bottom_gap", Codec.INT).forGetter(TextLayout::bottomGap)
        ).apply(instance, TextLayout::new));
    }

    public record ObjectiveLayout(
            Maybe<TextLayout> title,
            Maybe<ObjectiveTextLayout> text,
            Maybe<Integer> iconXOffset,
            Maybe<Integer> iconYOffset,
            Maybe<Integer> iconSize,
            Maybe<Float> itemScale,
            Maybe<Integer> entityScale,
            Maybe<Integer> entityClipPadding,
            Maybe<Float> entityAngleX,
            Maybe<Float> entityAngleY,
            Maybe<Integer> rowGap,
            Maybe<Integer> bottomGap
    ) {
        public static final Codec<ObjectiveLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CodecUtil.maybeFieldCodec("title", TextLayout.CODEC).forGetter(ObjectiveLayout::title),
                CodecUtil.maybeFieldCodec("text", ObjectiveTextLayout.CODEC).forGetter(ObjectiveLayout::text),
                CodecUtil.maybeFieldCodec("icon_x_offset", Codec.INT).forGetter(ObjectiveLayout::iconXOffset),
                CodecUtil.maybeFieldCodec("icon_y_offset", Codec.INT).forGetter(ObjectiveLayout::iconYOffset),
                CodecUtil.maybeFieldCodec("icon_size", Codec.INT).forGetter(ObjectiveLayout::iconSize),
                CodecUtil.maybeFieldCodec("item_scale", Codec.FLOAT).forGetter(ObjectiveLayout::itemScale),
                CodecUtil.maybeFieldCodec("entity_scale", Codec.INT).forGetter(ObjectiveLayout::entityScale),
                CodecUtil.maybeFieldCodec("entity_clip_padding", Codec.INT).forGetter(ObjectiveLayout::entityClipPadding),
                CodecUtil.maybeFieldCodec("entity_angle_x", Codec.FLOAT).forGetter(ObjectiveLayout::entityAngleX),
                CodecUtil.maybeFieldCodec("entity_angle_y", Codec.FLOAT).forGetter(ObjectiveLayout::entityAngleY),
                CodecUtil.maybeFieldCodec("row_gap", Codec.INT).forGetter(ObjectiveLayout::rowGap),
                CodecUtil.maybeFieldCodec("bottom_gap", Codec.INT).forGetter(ObjectiveLayout::bottomGap)
        ).apply(instance, ObjectiveLayout::new));
    }

    public record ObjectiveTextLayout(
            Maybe<Integer> xOffset,
            Maybe<Integer> yOffset,
            Maybe<Integer> width,
            Maybe<Float> scale,
            Maybe<Integer> lineSpacing,
            Maybe<Integer> activeColor,
            Maybe<Integer> completedColor
    ) {
        public static final Codec<ObjectiveTextLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CodecUtil.maybeFieldCodec("x_offset", Codec.INT).forGetter(ObjectiveTextLayout::xOffset),
                CodecUtil.maybeFieldCodec("y_offset", Codec.INT).forGetter(ObjectiveTextLayout::yOffset),
                CodecUtil.maybeFieldCodec("width", Codec.INT).forGetter(ObjectiveTextLayout::width),
                CodecUtil.maybeFieldCodec("scale", Codec.FLOAT).forGetter(ObjectiveTextLayout::scale),
                CodecUtil.maybeFieldCodec("line_spacing", Codec.INT).forGetter(ObjectiveTextLayout::lineSpacing),
                CodecUtil.maybeFieldCodec("active_color", Codec.INT).forGetter(ObjectiveTextLayout::activeColor),
                CodecUtil.maybeFieldCodec("completed_color", Codec.INT).forGetter(ObjectiveTextLayout::completedColor)
        ).apply(instance, ObjectiveTextLayout::new));
    }

    public record RewardLayout(
            Maybe<TextLayout> title,
            Maybe<Integer> itemXOffset,
            Maybe<Integer> itemYOffset,
            Maybe<Float> itemScale,
            Maybe<Integer> itemSpacing,
            Maybe<Integer> slotSize,
            Maybe<Integer> maxItems,
            Maybe<TextLayout> reputation
    ) {
        public static final Codec<RewardLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CodecUtil.maybeFieldCodec("title", TextLayout.CODEC).forGetter(RewardLayout::title),
                CodecUtil.maybeFieldCodec("item_x_offset", Codec.INT).forGetter(RewardLayout::itemXOffset),
                CodecUtil.maybeFieldCodec("item_y_offset", Codec.INT).forGetter(RewardLayout::itemYOffset),
                CodecUtil.maybeFieldCodec("item_scale", Codec.FLOAT).forGetter(RewardLayout::itemScale),
                CodecUtil.maybeFieldCodec("item_spacing", Codec.INT).forGetter(RewardLayout::itemSpacing),
                CodecUtil.maybeFieldCodec("slot_size", Codec.INT).forGetter(RewardLayout::slotSize),
                CodecUtil.maybeFieldCodec("max_items", Codec.INT).forGetter(RewardLayout::maxItems),
                CodecUtil.maybeFieldCodec("reputation", TextLayout.CODEC).forGetter(RewardLayout::reputation)
        ).apply(instance, RewardLayout::new));
    }

    public static final class Validator implements DataValidator<QuestScreenLayoutDefinition> {
        @Override
        public ValidationResult validate(QuestScreenLayoutDefinition data, ResourceLocation source) {
            if (data == null) return ValidationResult.failure("quest layout is null");
            if (data.questId == null) return ValidationResult.failure("quest_id is required");
            if (data.content.isDefined()) {
                ContentLayout content = data.content.get();
                if (content.width().isDefined() && content.width().get() <= 0)
                    return ValidationResult.failure("content.width must be > 0");
                if (content.scrollStep().isDefined() && content.scrollStep().get() <= 0)
                    return ValidationResult.failure("content.scroll_step must be > 0");
                int contentTop = 78 + content.topOffset().orElse(0);
                int contentBottom = 153 + content.bottomOffset().orElse(0);
                if (contentBottom <= contentTop)
                    return ValidationResult.failure("content bottom must be below content top");
            }
            ValidationResult result = validateText(data.title, "title");
            if (!result.valid()) return result;
            result = validateText(data.description, "description");
            if (!result.valid()) return result;
            result = validateText(data.reputationRequirement, "reputation_requirement");
            if (!result.valid()) return result;
            if (data.objectives.isDefined()) {
                ObjectiveLayout objectives = data.objectives.get();
                result = validateText(objectives.title(), "objectives.title");
                if (!result.valid()) return result;
                if (objectives.iconSize().isDefined() && objectives.iconSize().get() <= 0)
                    return ValidationResult.failure("objectives.icon_size must be > 0");
                if (objectives.itemScale().isDefined() && !positiveFinite(objectives.itemScale().get()))
                    return ValidationResult.failure("objectives.item_scale must be finite and > 0");
                if (objectives.entityScale().isDefined() && objectives.entityScale().get() <= 0)
                    return ValidationResult.failure("objectives.entity_scale must be > 0");
                if (objectives.entityClipPadding().isDefined() && objectives.entityClipPadding().get() < 0)
                    return ValidationResult.failure("objectives.entity_clip_padding must be >= 0");
                if (objectives.entityAngleX().isDefined() && !Float.isFinite(objectives.entityAngleX().get()))
                    return ValidationResult.failure("objectives.entity_angle_x must be finite");
                if (objectives.entityAngleY().isDefined() && !Float.isFinite(objectives.entityAngleY().get()))
                    return ValidationResult.failure("objectives.entity_angle_y must be finite");
                if (objectives.text().isDefined()) {
                    ObjectiveTextLayout text = objectives.text().get();
                    if (text.width().isDefined() && text.width().get() <= 0)
                        return ValidationResult.failure("objectives.text.width must be > 0");
                    if (text.scale().isDefined() && !positiveFinite(text.scale().get()))
                        return ValidationResult.failure("objectives.text.scale must be finite and > 0");
                    if (text.lineSpacing().isDefined() && text.lineSpacing().get() <= 0)
                        return ValidationResult.failure("objectives.text.line_spacing must be > 0");
                }
            }
            if (data.rewards.isDefined()) {
                RewardLayout rewards = data.rewards.get();
                result = validateText(rewards.title(), "rewards.title");
                if (!result.valid()) return result;
                result = validateText(rewards.reputation(), "rewards.reputation");
                if (!result.valid()) return result;
                if (rewards.itemScale().isDefined() && !positiveFinite(rewards.itemScale().get()))
                    return ValidationResult.failure("rewards.item_scale must be finite and > 0");
                if (rewards.slotSize().isDefined() && rewards.slotSize().get() <= 0)
                    return ValidationResult.failure("rewards.slot_size must be > 0");
                if (rewards.maxItems().isDefined() && rewards.maxItems().get() <= 0)
                    return ValidationResult.failure("rewards.max_items must be > 0");
            }
            return ValidationResult.success();
        }

        private static ValidationResult validateText(Maybe<TextLayout> maybe, String path) {
            if (maybe.isEmpty()) return ValidationResult.success();
            TextLayout text = maybe.get();
            if (text.width().isDefined() && text.width().get() <= 0)
                return ValidationResult.failure(path + ".width must be > 0");
            if (text.scale().isDefined() && !positiveFinite(text.scale().get()))
                return ValidationResult.failure(path + ".scale must be finite and > 0");
            if (text.lineSpacing().isDefined() && text.lineSpacing().get() <= 0)
                return ValidationResult.failure(path + ".line_spacing must be > 0");
            return ValidationResult.success();
        }

        private static boolean positiveFinite(float value) {
            return Float.isFinite(value) && value > 0.0F;
        }
    }
}
