package com.flechazo.eos.client.screen;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.common.TextKey;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import com.flechazo.hkt.Maybe;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SurvivorQuestScreen extends AbstractContainerScreen<SurvivorQuestMenu> {
    private static final float SCREEN_SCALE = 1.5F;
    private static final int BASE_ROW_HEIGHT = 18;
    private static final int BASE_SCREEN_WIDTH = 282;
    private static final int BASE_SCREEN_HEIGHT = 165;
    private static final int BASE_TASK_BUTTON_WIDTH = 59;
    private static final int BASE_TASK_BUTTON_HEIGHT = 18;
    private static final int BASE_RECEIVE_BUTTON_WIDTH = 194;
    private static final int BASE_RECEIVE_BUTTON_HEIGHT = 12;
    private static final int ROW_HEIGHT = scaled(BASE_ROW_HEIGHT);
    private static final int SCREEN_WIDTH = scaled(BASE_SCREEN_WIDTH);
    private static final int SCREEN_HEIGHT = scaled(BASE_SCREEN_HEIGHT);
    private static final int TASK_BUTTON_WIDTH = scaled(BASE_TASK_BUTTON_WIDTH);
    private static final int TASK_BUTTON_HEIGHT = scaled(BASE_TASK_BUTTON_HEIGHT);
    private static final int RECEIVE_BUTTON_WIDTH = scaled(BASE_RECEIVE_BUTTON_WIDTH);
    private static final int RECEIVE_BUTTON_HEIGHT = scaled(BASE_RECEIVE_BUTTON_HEIGHT);
    private static final int TEXT = 0xFF101010;
    private static final int MUTED = 0xFF303030;
    private static final ResourceLocation SCREEN_TEXTURE = guiTexture("request_screen");
    private static final ResourceLocation TASK_BUTTON = guiTexture("task_button");
    private static final ResourceLocation TASK_BUTTON_PRESSED = guiTexture("task_button_pressed");
    private static final ResourceLocation RECEIVE_BUTTON = guiTexture("receive_button");
    private static final ResourceLocation RECEIVE_BUTTON_PRESSED = guiTexture("receive_button_pressed");
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");

    private final List<TexturedButton> questButtons = new ArrayList<>();
    private final List<HoverTarget> hoverTargets = new ArrayList<>();
    private final Map<QuestDefinition.Objective, LivingEntity> objectiveEntities = new HashMap<>();
    private final Set<QuestDefinition.Objective> unavailableObjectiveEntities = new HashSet<>();
    private final Inventory playerInventory;
    private final long textSeed;
    private final boolean journalMode;
    private TexturedButton actionButton;
    private Action action = Action.NONE;
    private int selectedQuest = 0;
    private int scrollOffset;
    private int detailScrollOffset;

    public SurvivorQuestScreen(SurvivorQuestMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    protected SurvivorQuestScreen(
            SurvivorQuestMenu menu,
            Inventory inventory,
            Component title,
            boolean journalMode
    ) {
        super(menu, inventory, title);
        this.playerInventory = inventory;
        this.textSeed = RandomSource.create().nextLong();
        this.journalMode = journalMode;
        this.imageWidth = SCREEN_WIDTH;
        this.imageHeight = SCREEN_HEIGHT;
        this.inventoryLabelY = this.imageHeight - scaled(94);
    }

    private static ResourceLocation guiTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "textures/gui/" + name + ".png");
    }

    private static int scaled(int value) {
        return Math.round(value * SCREEN_SCALE);
    }

    static int screenWidth() {
        return SCREEN_WIDTH;
    }

    static int screenHeight() {
        return SCREEN_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        for (TexturedButton btn : this.questButtons) {
            btn.visible = false;
        }
        this.questButtons.clear();

        int listX = this.leftPos + scaled(6);
        int listY = this.topPos + scaled(18);
        int totalQuests = this.menu.questIds().size();

        for (int i = 0; i < totalQuests; i++) {
            int idx = i;
            TexturedButton row = new TexturedButton(listX, listY + (i * ROW_HEIGHT), TASK_BUTTON_WIDTH, TASK_BUTTON_HEIGHT, Component.empty(), TASK_BUTTON, TASK_BUTTON_PRESSED, BASE_TASK_BUTTON_WIDTH, BASE_TASK_BUTTON_HEIGHT, true, SurvivorQuestScreen.this.font, () -> {
                selectedQuest = idx;
                detailScrollOffset = 0;
                updateQuestButtonSelection();
                updateActionButtons();
            });
            this.questButtons.add(row);
            addRenderableWidget(row);
        }
        updateQuestButtonMessages();

        int receiveX = this.leftPos + scaled(78);
        int receiveY = this.topPos + this.imageHeight - scaled(20);
        this.actionButton = addRenderableWidget(new TexturedButton(
                receiveX,
                receiveY,
                RECEIVE_BUTTON_WIDTH,
                RECEIVE_BUTTON_HEIGHT,
                Component.empty(),
                RECEIVE_BUTTON,
                RECEIVE_BUTTON_PRESSED,
                BASE_RECEIVE_BUTTON_WIDTH,
                BASE_RECEIVE_BUTTON_HEIGHT,
                false, SurvivorQuestScreen.this.font,
                () -> {
                    switch (this.action) {
                        case ACCEPT -> sendButton(selectedQuest);
                        case SUBMIT -> sendButton(1000 + selectedQuest);
                        case CLAIM -> sendButton(2000 + selectedQuest);
                        default -> {
                        }
                    }
                }
        ));

        this.scrollOffset = 0;
        this.detailScrollOffset = 0;
        updateScrollVisibility();
        updateQuestButtonSelection();
        updateActionButtons();
    }

    private void updateQuestButtonMessages() {
        List<ResourceLocation> quests = this.menu.questIds();
        for (int i = 0; i < this.questButtons.size(); i++) {
            TexturedButton button = this.questButtons.get(i);
            Component name;
            if (i < quests.size()) {
                ResourceLocation questId = quests.get(i);
                name = EosDatapackIndex.quest(questId)
                        .map(quest -> randomizedText(quest.title(), quest.questId(), "title"))
                        .orElse(Component.literal(questId.toString()));
            } else {
                name = Component.empty();
            }
            if (this.journalMode && i < quests.size()) {
                name = Component.translatable(
                        "gui.echoes_of_survival.quest.journal.entry",
                        journalStatusText(this.menu.questEntry(i)),
                        name
                );
            }
            button.setMessage(name);
            button.setTextColor(this.menu.questEntry(i).claimed() ? MUTED : TEXT);
        }
    }

    private void updateQuestButtonSelection() {
        for (int i = 0; i < this.questButtons.size(); i++) {
            this.questButtons.get(i).setSelected(i == this.selectedQuest);
        }
    }

    private void updateActionButtons() {
        Maybe<QuestDefinition> questOpt = selectedQuest();
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        boolean accepted = entry.accepted();
        boolean completed = entry.completed();
        boolean claimed = entry.claimed();

        this.action = Action.NONE;
        Component message;
        boolean active = false;
        if (this.journalMode && questOpt.isEmpty()) {
            message = Component.translatable("gui.echoes_of_survival.quest.journal.empty");
        } else if (this.journalMode) {
            message = journalStatusText(entry);
        } else if (questOpt.isEmpty()) {
            message = Component.translatable("gui.echoes_of_survival.quest.empty");
        } else if (entry.maxReached()) {
            message = Component.translatable("gui.echoes_of_survival.quest.status.max_reached");
        } else if (!meetsReputationRequirement(questOpt.get())) {
            message = Component.translatable("gui.echoes_of_survival.quest.status.locked");
        } else if (!accepted) {
            this.action = Action.ACCEPT;
            message = Component.translatable("gui.echoes_of_survival.quest.accept");
            active = true;
        } else if (completed && !claimed) {
            this.action = Action.CLAIM;
            message = Component.translatable("gui.echoes_of_survival.quest.claim");
            active = true;
        } else if (claimed) {
            message = Component.translatable("gui.echoes_of_survival.quest.status.claimed");
        } else if (questOpt.get().type().equals(QuestDefinition.TYPE_SUBMIT_ITEMS)) {
            this.action = Action.SUBMIT;
            message = Component.translatable("gui.echoes_of_survival.quest.submit");
            active = hasSubmissionItems(questOpt.get());
        } else {
            message = Component.translatable("gui.echoes_of_survival.quest.status.active");
        }
        this.actionButton.setMessage(message);
        this.actionButton.active = active;
    }

    private Component journalStatusText(SurvivorQuestMenu.QuestEntry entry) {
        if (entry.claimed()) {
            if (entry.completionCount() > 1) {
                return Component.translatable(
                        "gui.echoes_of_survival.quest.journal.status.completed_count",
                        entry.completionCount()
                );
            }
            return Component.translatable("gui.echoes_of_survival.quest.journal.status.completed");
        }
        if (entry.completed()) {
            return Component.translatable("gui.echoes_of_survival.quest.journal.status.ready_to_claim");
        }
        return Component.translatable("gui.echoes_of_survival.quest.status.active");
    }

    private void updateScrollVisibility() {
        int totalQuests = this.menu.questIds().size();
        int availableHeight = this.imageHeight - scaled(38);
        int maxVisible = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, totalQuests - maxVisible);
        this.scrollOffset = Math.clamp(this.scrollOffset, 0, maxScroll);

        int listY = this.topPos + scaled(18);
        for (int i = 0; i < this.questButtons.size(); i++) {
            TexturedButton btn = this.questButtons.get(i);
            int visualIndex = i - this.scrollOffset;
            boolean visible = visualIndex >= 0 && visualIndex < maxVisible;
            btn.visible = visible;
            if (visible) {
                btn.setY(listY + visualIndex * ROW_HEIGHT);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && isMouseOverDetail(mouseX, mouseY)) {
            int oldOffset = this.detailScrollOffset;
            this.detailScrollOffset -= (int) Math.signum(deltaY) * selectedLayout().scrollStep;
            clampDetailScroll();
            if (this.detailScrollOffset != oldOffset) return true;
        }
        if (deltaY != 0) {
            int oldOffset = this.scrollOffset;
            this.scrollOffset -= (int) Math.signum(deltaY);
            updateScrollVisibility();
            if (this.scrollOffset != oldOffset) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void sendButton(int buttonId) {
        if (this.minecraft == null || this.minecraft.gameMode == null) return;
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
    }

    public void onQuestStateUpdated() {
        int questCount = this.menu.questIds().size();
        this.selectedQuest = Math.clamp(this.selectedQuest, 0, Math.max(0, questCount - 1));
        if (this.questButtons.size() != questCount && this.minecraft != null) {
            this.init(this.minecraft, this.width, this.height);
            return;
        }
        clampDetailScroll();
        updateScrollVisibility();
        updateQuestButtonMessages();
        updateQuestButtonSelection();
        updateActionButtons();
    }

    private Maybe<QuestDefinition> selectedQuest() {
        if (selectedQuest < 0 || selectedQuest >= this.menu.questIds().size()) return Maybe.none();
        return EosDatapackIndex.quest(this.menu.questIds().get(selectedQuest));
    }

    private QuestScreenLayout selectedLayout() {
        return selectedQuest().map(this::layoutFor).orElse(QuestScreenLayout.resolve(Maybe.none()));
    }

    private QuestScreenLayout layoutFor(QuestDefinition quest) {
        return QuestScreenLayout.resolve(EosDatapackIndex.questScreenLayout(quest.questId()));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos, this.topPos, 0);
        graphics.pose().scale(SCREEN_SCALE, SCREEN_SCALE, 1.0F);
        graphics.blit(SCREEN_TEXTURE, 0, 0, 0, 0, BASE_SCREEN_WIDTH, BASE_SCREEN_HEIGHT, 512, 256);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        this.hoverTargets.clear();
        Component screenTitle = trimToWidth(this.title, scaled(184));
        int titleX = (this.imageWidth - this.font.width(screenTitle)) / 2;
        graphics.drawString(this.font, screenTitle, titleX, scaled(6), TEXT, false);

        renderQuestDetails(graphics);
    }

    private void renderQuestDetails(GuiGraphics graphics) {
        Maybe<QuestDefinition> questOpt = selectedQuest();
        if (questOpt.isEmpty()) {
            graphics.drawString(
                    this.font,
                    Component.translatable("gui.echoes_of_survival.quest.empty"),
                    QuestScreenLayout.DEFAULT_DETAIL_X,
                    QuestScreenLayout.DEFAULT_DETAIL_TOP,
                    MUTED,
                    false
            );
            return;
        }
        QuestDefinition quest = questOpt.get();
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        QuestScreenLayout layout = layoutFor(quest);
        int x = layout.detailX;

        drawScaledString(
                graphics,
                trimToWidth(randomizedText(quest.title(), quest.questId(), "title"), layout.title.width(), layout.title.scale()),
                x + layout.title.x(),
                layout.title.y(),
                layout.title.scale(),
                layout.title.color()
        );

        clampDetailScroll();
        graphics.enableScissor(
                this.leftPos + x,
                this.topPos + layout.contentTop,
                this.leftPos + x + layout.detailWidth,
                this.topPos + layout.contentBottom
        );
        int y = layout.contentTop - this.detailScrollOffset;
        y = drawScaledWrapped(
                graphics,
                randomizedText(quest.description(), quest.questId(), "description"),
                x + layout.description.x(),
                y + layout.description.y(),
                layout.description.width(),
                layout.description.scale(),
                layout.description.lineSpacing(),
                layout.description.color()
        ) + layout.description.bottomGap();

        QuestScreenLayout.Text objectiveTitle = layout.objectives.title();
        drawScaledString(
                graphics,
                trimToWidth(
                        Component.translatable("gui.echoes_of_survival.quest.objectives"),
                        objectiveTitle.width(),
                        objectiveTitle.scale()
                ),
                x + objectiveTitle.x(),
                y + objectiveTitle.y(),
                objectiveTitle.scale(),
                objectiveTitle.color()
        );
        y += objectiveTitle.y() + objectiveTitle.lineSpacing() + objectiveTitle.bottomGap();
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDefinition.Objective objective = quest.objectives().get(i);
            int progress = i < entry.objectiveProgress().size() ? entry.objectiveProgress().get(i) : 0;
            int objectiveTop = y;
            renderObjectiveIcon(
                    graphics,
                    objective,
                    x + layout.objectives.iconX(),
                    objectiveTop + layout.objectives.iconY(),
                    layout
            );
            int textBottom = drawScaledWrapped(
                    graphics,
                    objectiveText(objective, progress),
                    x + layout.objectives.textX(),
                    y + layout.objectives.textY(),
                    layout.objectives.textWidth(),
                    layout.objectives.textScale(),
                    layout.objectives.textLineSpacing(),
                    objectiveDone(objective, progress)
                            ? layout.objectives.completedTextColor()
                            : layout.objectives.activeTextColor()
            );
            y = Math.max(textBottom, objectiveTop + layout.objectives.iconSize());
            y += layout.objectives.rowGap();
        }

        y += layout.objectives.bottomGap();
        if (!meetsReputationRequirement(quest)) {
            QuestScreenLayout.Text reputation = layout.reputationRequirement;
            drawScaledWrapped(
                    graphics,
                    Component.translatable(
                            "gui.echoes_of_survival.quest.reputation_required",
                            reputationRequirementValue(quest)
                    ),
                    x + reputation.x(),
                    y + reputation.y(),
                    reputation.width(),
                    reputation.scale(),
                    reputation.lineSpacing(),
                    reputation.color()
            );
            graphics.disableScissor();
            return;
        }
        graphics.disableScissor();

        QuestScreenLayout.Text rewardTitle = layout.rewards.title();
        drawScaledString(
                graphics,
                trimToWidth(
                        Component.translatable("gui.echoes_of_survival.quest.rewards"),
                        rewardTitle.width(),
                        rewardTitle.scale()
                ),
                x + rewardTitle.x(),
                rewardTitle.y(),
                rewardTitle.scale(),
                rewardTitle.color()
        );
        int rewardX = x + layout.rewards.itemX();
        int rewardY = layout.rewards.itemY();
        int rewardCount = Math.min(layout.rewards.maxItems(), quest.rewards().items().size());
        for (int i = 0; i < rewardCount; i++) {
            ItemStack reward = quest.rewards().items().get(i);
            renderRewardIcon(
                    graphics,
                    reward,
                    rewardX + i * layout.rewards.itemSpacing(),
                    rewardY,
                    layout.rewards.itemScale(),
                    layout.rewards.slotSize()
            );
        }
        if (quest.rewards().reputation() != 0) {
            QuestScreenLayout.Text rewardReputation = layout.rewards.reputation();
            drawScaledString(
                    graphics,
                    trimToWidth(
                            Component.translatable(
                                    "gui.echoes_of_survival.quest.reputation_reward",
                                    quest.rewards().reputation()
                            ),
                            rewardReputation.width(),
                            rewardReputation.scale()
                    ),
                    x + rewardReputation.x(),
                    rewardTitle.y() + rewardReputation.y(),
                    rewardReputation.scale(),
                    rewardReputation.color()
            );
        }
    }

    private boolean isMouseOverDetail(double mouseX, double mouseY) {
        QuestScreenLayout layout = selectedLayout();
        int x = this.leftPos + layout.detailX;
        int y = this.topPos + layout.contentTop;
        return mouseX >= x && mouseX < x + layout.detailWidth
                && mouseY >= y && mouseY < y + layout.contentHeight();
    }

    private void clampDetailScroll() {
        this.detailScrollOffset = Math.clamp(this.detailScrollOffset, 0, maxDetailScroll());
    }

    private int maxDetailScroll() {
        Maybe<QuestDefinition> questOpt = selectedQuest();
        if (questOpt.isEmpty()) return 0;
        QuestDefinition quest = questOpt.get();
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        QuestScreenLayout layout = layoutFor(quest);
        return Math.max(0, detailContentHeight(quest, entry, layout) - layout.contentHeight());
    }

    private int detailContentHeight(
            QuestDefinition quest,
            SurvivorQuestMenu.QuestEntry entry,
            QuestScreenLayout layout
    ) {
        int height = 0;
        height += wrappedHeight(
                randomizedText(quest.description(), quest.questId(), "description"),
                layout.description.width(),
                layout.description.scale(),
                layout.description.lineSpacing()
        );
        height += layout.description.y() + layout.description.bottomGap();
        height += layout.objectives.title().y()
                + layout.objectives.title().lineSpacing()
                + layout.objectives.title().bottomGap();
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDefinition.Objective objective = quest.objectives().get(i);
            int progress = i < entry.objectiveProgress().size() ? entry.objectiveProgress().get(i) : 0;
            int textHeight = layout.objectives.textY() + wrappedHeight(
                    objectiveText(objective, progress),
                    layout.objectives.textWidth(),
                    layout.objectives.textScale(),
                    layout.objectives.textLineSpacing()
            );
            height += Math.max(layout.objectives.iconSize(), textHeight);
            height += layout.objectives.rowGap();
        }
        height += layout.objectives.bottomGap();
        if (!meetsReputationRequirement(quest)) {
            height += layout.reputationRequirement.y() + wrappedHeight(
                    Component.translatable(
                            "gui.echoes_of_survival.quest.reputation_required",
                            reputationRequirementValue(quest)
                    ),
                    layout.reputationRequirement.width(),
                    layout.reputationRequirement.scale(),
                    layout.reputationRequirement.lineSpacing()
            );
        }
        return height;
    }

    private int drawScaledWrapped(
            GuiGraphics graphics,
            Component component,
            int x,
            int y,
            int width,
            float scale,
            int lineSpacing,
            int color
    ) {
        int logicalWidth = logicalWidth(width, scale);
        for (var line : this.font.split(component, logicalWidth)) {
            drawScaledString(graphics, line, x, y, scale, color);
            y += lineSpacing;
        }
        return y;
    }

    private void drawScaledString(
            GuiGraphics graphics,
            Component component,
            int x,
            int y,
            float scale,
            int color
    ) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, component, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledString(
            GuiGraphics graphics,
            FormattedCharSequence line,
            int x,
            int y,
            float scale,
            int color
    ) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, line, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private int wrappedHeight(Component component, int width, float scale, int lineSpacing) {
        return this.font.split(component, logicalWidth(width, scale)).size() * lineSpacing;
    }

    private static int logicalWidth(int width, float scale) {
        return Math.max(1, (int) Math.floor(width / scale));
    }

    private Component trimToWidth(Component component, int width, float scale) {
        int logicalWidth = logicalWidth(width, scale);
        if (this.font.width(component) <= logicalWidth) {
            return component;
        }
        return Component.literal(this.font.plainSubstrByWidth(
                component.getString(),
                Math.max(0, logicalWidth - this.font.width("..."))
        ) + "...");
    }

    private Component trimToWidth(Component component, int width) {
        return trimToWidth(component, width, 1.0F);
    }

    private void renderRewardIcon(
            GuiGraphics graphics,
            ItemStack reward,
            int x,
            int y,
            float itemScale,
            int slotSize
    ) {
        if (reward == null || reward.isEmpty()) {
            return;
        }
        ItemStack stack = reward.copy();
        graphics.blitSprite(SLOT, x, y, slotSize, slotSize);
        float itemSize = 16.0F * itemScale;
        float offset = (slotSize - itemSize) / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x + offset, y + offset, 0.0F);
        graphics.pose().scale(itemScale, itemScale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.renderItemDecorations(this.font, stack, 0, 0);
        graphics.pose().popPose();
        addItemHoverTarget(x, y, slotSize, slotSize, stack, false, null);
    }

    private void renderObjectiveIcon(
            GuiGraphics graphics,
            QuestDefinition.Objective objective,
            int x,
            int y,
            QuestScreenLayout layout
    ) {
        if (!isObjectiveIconVisible(y, layout)) return;

        if (objective.itemTarget().isDefined()) {
            BuiltInRegistries.ITEM.getOptional(objective.itemTarget().get()).ifPresent(item -> {
                ItemStack stack = item.getDefaultInstance();
                float itemSize = 16.0F * layout.objectives.itemScale();
                float offset = (layout.objectives.iconSize() - itemSize) / 2.0F;
                graphics.pose().pushPose();
                graphics.pose().translate(x + offset, y + offset, 0.0F);
                graphics.pose().scale(
                        layout.objectives.itemScale(),
                        layout.objectives.itemScale(),
                        1.0F
                );
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
                addItemHoverTarget(
                        x,
                        y,
                        layout.objectives.iconSize(),
                        layout.objectives.iconSize(),
                        stack,
                        true,
                        layout
                );
            });
            return;
        }
        if (objective.entityTarget().isEmpty()) return;

        ResourceLocation entityId = objective.entityTarget().get();
        LivingEntity entity = objectiveEntity(objective);
        if (entity == null) return;

        graphics.pose().pushPose();
        graphics.pose().translate(-this.leftPos, -this.topPos, 0.0F);
        int screenX = this.leftPos + x;
        int screenY = this.topPos + y;
        InventoryScreen.renderEntityInInventoryFollowsAngle(
                graphics,
                screenX - layout.objectives.entityClipPadding(),
                screenY - layout.objectives.entityClipPadding(),
                screenX + layout.objectives.iconSize() + layout.objectives.entityClipPadding(),
                screenY + layout.objectives.iconSize() + layout.objectives.entityClipPadding(),
                layout.objectives.entityScale(),
                0.0F,
                layout.objectives.entityAngleX(),
                layout.objectives.entityAngleY(),
                entity
        );
        graphics.pose().popPose();
        addTextHoverTarget(
                x,
                y,
                layout.objectives.iconSize(),
                layout.objectives.iconSize(),
                Component.translatable(entity.getType().getDescriptionId()),
                true,
                layout
        );
    }

    private LivingEntity objectiveEntity(QuestDefinition.Objective objective) {
        LivingEntity cached = this.objectiveEntities.get(objective);
        if (cached != null) return cached;
        if (this.unavailableObjectiveEntities.contains(objective)
                || objective.entityTarget().isEmpty()
                || this.minecraft == null || this.minecraft.level == null) return null;

        ResourceLocation id = objective.entityTarget().get();
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        Entity entity = type.map(value -> value.create(this.minecraft.level)).orElse(null);
        if (entity instanceof LivingEntity living) {
            if (objective.entityNbt().isDefined()) {
                CompoundTag configuredNbt = objective.entityNbt().get();
                CompoundTag completeNbt = living.saveWithoutId(new CompoundTag());
                completeNbt.merge(configuredNbt.copy());
                living.load(completeNbt);
            }
            this.objectiveEntities.put(objective, living);
            return living;
        }
        this.unavailableObjectiveEntities.add(objective);
        return null;
    }

    private boolean isObjectiveIconVisible(int y, QuestScreenLayout layout) {
        return y + layout.objectives.iconSize() > layout.contentTop && y < layout.contentBottom;
    }

    private void addItemHoverTarget(
            int x,
            int y,
            int width,
            int height,
            ItemStack stack,
            boolean clipToDetail,
            QuestScreenLayout layout
    ) {
        addHoverTarget(x, y, width, height, stack, Component.empty(), clipToDetail, layout);
    }

    private void addTextHoverTarget(
            int x,
            int y,
            int width,
            int height,
            Component text,
            boolean clipToDetail,
            QuestScreenLayout layout
    ) {
        addHoverTarget(x, y, width, height, ItemStack.EMPTY, text, clipToDetail, layout);
    }

    private void addHoverTarget(
            int x,
            int y,
            int width,
            int height,
            ItemStack stack,
            Component text,
            boolean clipToDetail,
            QuestScreenLayout layout
    ) {
        int contentTop = layout == null ? y : layout.contentTop;
        int contentBottom = layout == null ? y + height : layout.contentBottom;
        int top = clipToDetail ? Math.max(y, contentTop) : y;
        int bottom = clipToDetail ? Math.min(y + height, contentBottom) : y + height;
        if (bottom <= top) return;
        this.hoverTargets.add(new HoverTarget(this.leftPos + x, this.topPos + top, width, bottom - top, stack, text));
    }

    private void renderCustomTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = this.hoverTargets.size() - 1; i >= 0; i--) {
            HoverTarget target = this.hoverTargets.get(i);
            if (!target.contains(mouseX, mouseY)) continue;
            if (!target.stack().isEmpty()) {
                graphics.renderTooltip(this.font, target.stack(), mouseX, mouseY);
            } else if (!target.text().getString().isEmpty()) {
                graphics.renderTooltip(this.font, target.text(), mouseX, mouseY);
            }
            return;
        }
    }

    private Component objectiveText(QuestDefinition.Objective objective, int progress) {
        Component target;
        if (objective.itemTarget().isDefined()) {
            target = Component.literal(itemName(objective.itemTarget().get()));
        } else if (objective.entityTarget().isDefined()) {
            target = Component.literal(entityName(objective.entityTarget().get()));
        } else if (objective.positionTarget().isDefined()) {
            QuestDefinition.PositionTarget position = objective.positionTarget().get();
            target = Component.translatable(
                    "gui.echoes_of_survival.quest.objective.position",
                    position.dimension(),
                    position.x(),
                    position.y(),
                    position.z(),
                    formatRadius(position.radius())
            );
        } else if (objective.structureTarget().isDefined()) {
            target = Component.translatable(
                    "gui.echoes_of_survival.quest.objective.structure",
                    objective.structureTarget().get()
            );
        } else {
            target = Component.literal("?");
        }
        return Component.translatable("gui.echoes_of_survival.quest.objective_line", target, Math.min(progress, objective.count()), objective.count());
    }

    private static String formatRadius(double radius) {
        if (radius == Math.rint(radius)) return Long.toString(Math.round(radius));
        return Double.toString(radius);
    }

    private boolean objectiveDone(QuestDefinition.Objective objective, int progress) {
        return progress >= objective.count();
    }

    private Component requiredReputationText(Either<Integer, String> requirement) {
        return requirement.map(
                value -> Component.translatable("gui.echoes_of_survival.quest.reputation_required", value),
                tier -> Component.translatable("gui.echoes_of_survival.quest.reputation_tier_required", tier)
        );
    }

    private Component statusText(QuestDefinition quest, SurvivorQuestMenu.QuestEntry entry) {
        if (entry.claimed()) return Component.translatable("gui.echoes_of_survival.quest.status.claimed");
        if (entry.completed()) return Component.translatable("gui.echoes_of_survival.quest.status.completed");
        if (entry.accepted()) return Component.translatable("gui.echoes_of_survival.quest.status.active");
        if (!meetsReputationRequirement(quest)) return Component.translatable("gui.echoes_of_survival.quest.status.locked");
        return Component.translatable("gui.echoes_of_survival.quest.status.available");
    }

    private Component randomizedText(TextKey key, ResourceLocation questId, String field) {
        if (key.keys() == null || key.keys().isEmpty()) {
            return Component.empty();
        }
        long seed = this.textSeed;
        if (questId != null) {
            seed ^= questId.hashCode();
        }
        seed ^= field.hashCode();
        return key.toComponent(RandomSource.create(seed));
    }

    private int reputationRequirementValue(QuestDefinition quest) {
        if (quest.reputationGate().isEmpty()) return 0;
        return quest.reputationGate().get().map(
                value -> value,
                tier -> EosDatapackIndex.reputationTierByName(tier)
                        .map(ReputationTiersDefinition.Tier::min)
                        .orElse(0)
        );
    }

    private boolean meetsReputationRequirement(QuestDefinition quest) {
        if (quest == null || quest.reputationGate().isEmpty()) {
            return true;
        }
        int required = quest.reputationGate().get().map(
                value -> value,
                tier -> EosDatapackIndex.reputationTierByName(tier)
                        .map(ReputationTiersDefinition.Tier::min)
                        .orElse(Integer.MAX_VALUE)
        );
        return this.menu.playerReputation() >= required;
    }

    private boolean hasSubmissionItems(QuestDefinition quest) {
        for (QuestDefinition.Objective objective : quest.objectives()) {
            if (objective.itemTarget().isEmpty()) continue;
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(objective.itemTarget().get());
            if (item.isEmpty()) return false;
            if (countItem(item.get()) < objective.count()) return false;
        }
        return true;
    }

    private int countItem(Item item) {
        int count = 0;
        for (ItemStack stack : this.playerInventory.items) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private String itemName(ResourceLocation id) {
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        return item.map(value -> value.getDefaultInstance().getHoverName().getString()).orElse(id.toString());
    }

    private String entityName(ResourceLocation id) {
        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        return entityType.map(value -> Component.translatable(value.getDescriptionId()).getString()).orElse(id.toString());
    }

    private enum Action {
        NONE,
        ACCEPT,
        SUBMIT,
        CLAIM
    }

    private record HoverTarget(int x, int y, int width, int height, ItemStack stack, Component text) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }


    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.renderCustomTooltip(graphics, mouseX, mouseY);
    }
}
