package com.flechazo.eos.client.screen;

import com.flechazo.eos.data.quest.QuestScreenLayoutDefinition;
import com.flechazo.hkt.Maybe;

final class QuestScreenLayout {
    static final int DEFAULT_DETAIL_X = 132;
    static final int DEFAULT_DETAIL_TOP = 53;
    static final int DEFAULT_DETAIL_WIDTH = 276;
    static final int DEFAULT_CONTENT_TOP = 78;
    static final int DEFAULT_CONTENT_BOTTOM = 153;
    static final int DEFAULT_REWARD_TITLE_Y = 161;

    final int detailX;
    final int detailWidth;
    final int contentTop;
    final int contentBottom;
    final int scrollStep;
    final Text title;
    final Text description;
    final Objectives objectives;
    final Text reputationRequirement;
    final Rewards rewards;

    private QuestScreenLayout(
            int detailX,
            int detailWidth,
            int contentTop,
            int contentBottom,
            int scrollStep,
            Text title,
            Text description,
            Objectives objectives,
            Text reputationRequirement,
            Rewards rewards
    ) {
        this.detailX = detailX;
        this.detailWidth = detailWidth;
        this.contentTop = contentTop;
        this.contentBottom = contentBottom;
        this.scrollStep = scrollStep;
        this.title = title;
        this.description = description;
        this.objectives = objectives;
        this.reputationRequirement = reputationRequirement;
        this.rewards = rewards;
    }

    static QuestScreenLayout resolve(Maybe<QuestScreenLayoutDefinition> maybeDefinition) {
        QuestScreenLayoutDefinition definition = nullable(maybeDefinition);
        QuestScreenLayoutDefinition.ContentLayout content = definition == null ? null : nullable(definition.content());
        int detailX = DEFAULT_DETAIL_X + integer(content == null ? null : content.xOffset(), 0);
        int detailWidth = integer(content == null ? null : content.width(), DEFAULT_DETAIL_WIDTH);
        int contentTop = DEFAULT_CONTENT_TOP + integer(content == null ? null : content.topOffset(), 0);
        int contentBottom = DEFAULT_CONTENT_BOTTOM + integer(content == null ? null : content.bottomOffset(), 0);
        int scrollStep = integer(content == null ? null : content.scrollStep(), 12);

        Text title = text(
                definition == null ? null : definition.title(),
                0, DEFAULT_DETAIL_TOP, detailWidth, 1.0F, 10, 0xFF1A1A1A, 0
        );
        Text description = text(
                definition == null ? null : definition.description(),
                0, 0, detailWidth, 1.0F, 10, 0xFF303030, 6
        );
        Text reputation = text(
                definition == null ? null : definition.reputationRequirement(),
                0, 0, detailWidth, 1.0F, 10, 0xFF303030, 0
        );

        QuestScreenLayoutDefinition.ObjectiveLayout objectiveDefinition =
                definition == null ? null : nullable(definition.objectives());
        Text objectiveTitle = text(
                objectiveDefinition == null ? null : objectiveDefinition.title(),
                0, 0, detailWidth, 1.0F, 10, 0xFF101010, 8
        );
        QuestScreenLayoutDefinition.ObjectiveTextLayout objectiveText =
                objectiveDefinition == null ? null : nullable(objectiveDefinition.text());
        int textX = integer(objectiveText == null ? null : objectiveText.xOffset(), 32);
        Objectives objectives = new Objectives(
                objectiveTitle,
                integer(objectiveDefinition == null ? null : objectiveDefinition.iconXOffset(), 15),
                integer(objectiveDefinition == null ? null : objectiveDefinition.iconYOffset(), -4),
                integer(objectiveDefinition == null ? null : objectiveDefinition.iconSize(), 16),
                decimal(objectiveDefinition == null ? null : objectiveDefinition.itemScale(), 0.875F),
                integer(objectiveDefinition == null ? null : objectiveDefinition.entityScale(), 10),
                integer(objectiveDefinition == null ? null : objectiveDefinition.entityClipPadding(), 2),
                decimal(objectiveDefinition == null ? null : objectiveDefinition.entityAngleX(), 0.15F),
                decimal(objectiveDefinition == null ? null : objectiveDefinition.entityAngleY(), 0.0F),
                textX,
                integer(objectiveText == null ? null : objectiveText.yOffset(), 0),
                integer(objectiveText == null ? null : objectiveText.width(), detailWidth - textX),
                decimal(objectiveText == null ? null : objectiveText.scale(), 1.0F),
                integer(objectiveText == null ? null : objectiveText.lineSpacing(), 10),
                integer(objectiveText == null ? null : objectiveText.activeColor(), 0xFF303030),
                integer(objectiveText == null ? null : objectiveText.completedColor(), 0xFF1F6E2F),
                integer(objectiveDefinition == null ? null : objectiveDefinition.rowGap(), 2),
                integer(objectiveDefinition == null ? null : objectiveDefinition.bottomGap(), 17)
        );

        QuestScreenLayoutDefinition.RewardLayout rewardDefinition =
                definition == null ? null : nullable(definition.rewards());
        Text rewardTitle = text(
                rewardDefinition == null ? null : rewardDefinition.title(),
                0, DEFAULT_REWARD_TITLE_Y, detailWidth, 1.0F, 10, 0xFF101010, 0
        );
        Text rewardReputation = text(
                rewardDefinition == null ? null : rewardDefinition.reputation(),
                52, 22, detailWidth - 52, 1.0F, 10, 0xFF1A1A1A, 0
        );
        Rewards rewards = new Rewards(
                rewardTitle,
                integer(rewardDefinition == null ? null : rewardDefinition.itemXOffset(), 5),
                rewardTitle.y() + integer(rewardDefinition == null ? null : rewardDefinition.itemYOffset(), 19),
                decimal(rewardDefinition == null ? null : rewardDefinition.itemScale(), 1.0F),
                integer(rewardDefinition == null ? null : rewardDefinition.itemSpacing(), 20),
                integer(rewardDefinition == null ? null : rewardDefinition.slotSize(), 18),
                integer(rewardDefinition == null ? null : rewardDefinition.maxItems(), 2),
                rewardReputation
        );

        return new QuestScreenLayout(
                detailX,
                detailWidth,
                contentTop,
                contentBottom,
                scrollStep,
                title,
                description,
                objectives,
                reputation,
                rewards
        );
    }

    int contentHeight() {
        return Math.max(1, this.contentBottom - this.contentTop);
    }

    private static Text text(
            Maybe<QuestScreenLayoutDefinition.TextLayout> maybe,
            int defaultX,
            int defaultY,
            int defaultWidth,
            float defaultScale,
            int defaultLineSpacing,
            int defaultColor,
            int defaultBottomGap
    ) {
        QuestScreenLayoutDefinition.TextLayout value = nullable(maybe);
        return new Text(
                defaultX + integer(value == null ? null : value.xOffset(), 0),
                defaultY + integer(value == null ? null : value.yOffset(), 0),
                integer(value == null ? null : value.width(), defaultWidth),
                decimal(value == null ? null : value.scale(), defaultScale),
                integer(value == null ? null : value.lineSpacing(), defaultLineSpacing),
                integer(value == null ? null : value.color(), defaultColor),
                integer(value == null ? null : value.bottomGap(), defaultBottomGap)
        );
    }

    private static int integer(Maybe<Integer> value, int fallback) {
        return value == null ? fallback : value.orElse(fallback);
    }

    private static float decimal(Maybe<Float> value, float fallback) {
        return value == null ? fallback : value.orElse(fallback);
    }

    private static <A> A nullable(Maybe<A> value) {
        return value == null ? null : value.fold(() -> null, present -> present);
    }

    record Text(int x, int y, int width, float scale, int lineSpacing, int color, int bottomGap) {
    }

    record Objectives(
            Text title,
            int iconX,
            int iconY,
            int iconSize,
            float itemScale,
            int entityScale,
            int entityClipPadding,
            float entityAngleX,
            float entityAngleY,
            int textX,
            int textY,
            int textWidth,
            float textScale,
            int textLineSpacing,
            int activeTextColor,
            int completedTextColor,
            int rowGap,
            int bottomGap
    ) {
    }

    record Rewards(
            Text title,
            int itemX,
            int itemY,
            float itemScale,
            int itemSpacing,
            int slotSize,
            int maxItems,
            Text reputation
    ) {
    }
}
