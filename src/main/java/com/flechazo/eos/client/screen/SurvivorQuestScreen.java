package com.flechazo.eos.client.screen;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.common.ItemStackDef;
import com.flechazo.eos.data.common.TextKey;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private static final int DETAIL_X = scaled(88);
    private static final int DETAIL_TOP = scaled(35);
    private static final int DETAIL_WIDTH = SCREEN_WIDTH - DETAIL_X - scaled(10);
    private static final int DETAIL_CONTENT_TOP = scaled(52);
    private static final int DETAIL_CONTENT_BOTTOM = SCREEN_HEIGHT - scaled(28);
    private static final int DETAIL_CONTENT_HEIGHT = DETAIL_CONTENT_BOTTOM - DETAIL_CONTENT_TOP;
    private static final int DETAIL_SCROLL_STEP = 12;
    private static final int SCROLL_START_PAUSE_TICKS = 8;
    private static final int SCROLL_END_PAUSE_TICKS = 8;
    private static final int SCROLL_PIXELS_PER_TICK = 1;
    private static final int TEXT = 0xFF101010;
    private static final int MUTED = 0xFF303030;
    private static final int GOOD = 0xFF1F6E2F;
    private static final int GOLD = 0xFF1A1A1A;
    private static final ResourceLocation SCREEN_TEXTURE = guiTexture("request_screen");
    private static final ResourceLocation TASK_BUTTON = guiTexture("task_button");
    private static final ResourceLocation TASK_BUTTON_PRESSED = guiTexture("task_button_pressed");
    private static final ResourceLocation RECEIVE_BUTTON = guiTexture("receive_button");
    private static final ResourceLocation RECEIVE_BUTTON_PRESSED = guiTexture("receive_button_pressed");
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");

    private final List<TexturedButton> questButtons = new ArrayList<>();
    private final Inventory playerInventory;
    private final long textSeed;
    private TexturedButton actionButton;
    private Action action = Action.NONE;
    private int selectedQuest = 0;
    private int scrollOffset;
    private int detailScrollOffset;

    public SurvivorQuestScreen(SurvivorQuestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.playerInventory = inventory;
        this.textSeed = RandomSource.create().nextLong();
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
                QuestDefinition quest = EosDatapackIndex.quest(quests.get(i)).orElse(null);
                name = quest != null ? randomizedText(quest.title(), quest.questId(), "title") : Component.literal(quests.get(i).toString());
            } else {
                name = Component.empty();
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
        QuestDefinition quest = selectedQuest();
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        boolean hasQuest = quest != null;
        boolean accepted = entry.accepted();
        boolean completed = entry.completed();
        boolean claimed = entry.claimed();

        this.action = Action.NONE;
        Component message;
        boolean active = false;
        if (!hasQuest) {
            message = Component.translatable("gui.echoes_of_survival.quest.empty");
        } else if (entry.maxReached()) {
            message = Component.translatable("gui.echoes_of_survival.quest.status.max_reached");
        } else if (!meetsReputationRequirement(quest)) {
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
        } else if (quest.type().equals(QuestDefinition.TYPE_SUBMIT_ITEMS)) {
            this.action = Action.SUBMIT;
            message = Component.translatable("gui.echoes_of_survival.quest.submit");
            active = hasSubmissionItems(quest);
        } else {
            message = Component.translatable("gui.echoes_of_survival.quest.status.active");
        }
        this.actionButton.setMessage(message);
        this.actionButton.active = active;
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
            this.detailScrollOffset -= (int) Math.signum(deltaY) * DETAIL_SCROLL_STEP;
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

    private QuestDefinition selectedQuest() {
        if (selectedQuest < 0 || selectedQuest >= this.menu.questIds().size()) return null;
        return EosDatapackIndex.quest(this.menu.questIds().get(selectedQuest)).orElse(null);
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
        Component screenTitle = trimToWidth(this.title, scaled(184));
        int titleX = (this.imageWidth - this.font.width(screenTitle)) / 2;
        graphics.drawString(this.font, screenTitle, titleX, scaled(6), TEXT, false);

        renderQuestDetails(graphics);
    }

    private void renderQuestDetails(GuiGraphics graphics) {
        QuestDefinition quest = selectedQuest();
        if (quest == null) {
            graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.empty"), DETAIL_X, DETAIL_TOP, MUTED, false);
            return;
        }

        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        int x = DETAIL_X;

        graphics.drawString(this.font, trimToWidth(randomizedText(quest.title(), quest.questId(), "title"), DETAIL_WIDTH), x, DETAIL_TOP, GOLD, false);

        clampDetailScroll();
        graphics.enableScissor(this.leftPos + x, this.topPos + DETAIL_CONTENT_TOP, this.leftPos + x + DETAIL_WIDTH, this.topPos + DETAIL_CONTENT_BOTTOM);
        int y = DETAIL_CONTENT_TOP - this.detailScrollOffset;
        y = drawWrapped(graphics, randomizedText(quest.description(), quest.questId(), "description"), x, y, DETAIL_WIDTH, MUTED);
        y += 6;

        graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.objectives"), x, y, TEXT, false);
        y += 12;
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDefinition.Objective objective = quest.objectives().get(i);
            int progress = i < entry.objectiveProgress().size() ? entry.objectiveProgress().get(i) : 0;
            y = drawWrapped(graphics, objectiveText(objective, progress), x + scaled(8), y, DETAIL_WIDTH - scaled(8), objectiveDone(objective, progress) ? GOOD : MUTED);
            y += 2;
        }

        y += 14;
        if (!meetsReputationRequirement(quest)) {
            drawWrapped(graphics, Component.translatable("gui.echoes_of_survival.quest.reputation_required", reputationRequirementValue(quest)), x, y, DETAIL_WIDTH, MUTED);
            graphics.disableScissor();
            return;
        }
        graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.rewards"), x, y, TEXT, false);
        y += 27;
        int rewardX = x + scaled(4);
        int rewardY = y;
        for (int i = 0; i < quest.rewards().items().size(); i++) {
            ItemStackDef reward = quest.rewards().items().get(i);
            renderRewardIcon(graphics, reward, rewardX + i * 20, rewardY);
            if (i >= 1) break;
        }
        if (quest.rewards().reputation() != 0) {
            graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.reputation_reward", quest.rewards().reputation()), x + 52, rewardY + 2, GOLD, false);
        }
        graphics.disableScissor();
    }

    private boolean isMouseOverDetail(double mouseX, double mouseY) {
        int x = this.leftPos + DETAIL_X;
        int y = this.topPos + DETAIL_CONTENT_TOP;
        return mouseX >= x && mouseX < x + DETAIL_WIDTH && mouseY >= y && mouseY < y + DETAIL_CONTENT_HEIGHT;
    }

    private void clampDetailScroll() {
        this.detailScrollOffset = Math.clamp(this.detailScrollOffset, 0, maxDetailScroll());
    }

    private int maxDetailScroll() {
        QuestDefinition quest = selectedQuest();
        if (quest == null) return 0;
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        return Math.max(0, detailContentHeight(quest, entry) - DETAIL_CONTENT_HEIGHT);
    }

    private int detailContentHeight(QuestDefinition quest, SurvivorQuestMenu.QuestEntry entry) {
        int height = 0;
        height += this.font.split(randomizedText(quest.description(), quest.questId(), "description"), DETAIL_WIDTH).size() * 10;
        height += 6;
        height += 12;
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDefinition.Objective objective = quest.objectives().get(i);
            int progress = i < entry.objectiveProgress().size() ? entry.objectiveProgress().get(i) : 0;
            height += this.font.split(objectiveText(objective, progress), DETAIL_WIDTH - scaled(8)).size() * 10;
            height += 2;
        }
        height += 14;
        if (!meetsReputationRequirement(quest)) {
            height += this.font.split(Component.translatable("gui.echoes_of_survival.quest.reputation_required", reputationRequirementValue(quest)), DETAIL_WIDTH).size() * 10;
        } else {
            height += 49;
        }
        return height;
    }

    private int drawWrapped(GuiGraphics graphics, Component component, int x, int y, int width, int color) {
        for (var line : this.font.split(component, width)) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private int drawWrapped(GuiGraphics graphics, Component component, int x, int y, int width, int color, int maxLines) {
        int lines = 0;
        for (var line : this.font.split(component, width)) {
            if (lines >= maxLines) break;
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
            lines++;
        }
        return y;
    }

    private Component trimToWidth(Component component, int width) {
        if (this.font.width(component) <= width) {
            return component;
        }
        return Component.literal(this.font.plainSubstrByWidth(component.getString(), Math.max(0, width - this.font.width("..."))) + "...");
    }

    private void renderRewardIcon(GuiGraphics graphics, ItemStackDef reward, int x, int y) {
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(reward.item());
        if (item.isEmpty()) {
            return;
        }
        ItemStack stack = new ItemStack(item.get(), Math.max(1, reward.count()));
        graphics.blitSprite(SLOT, x - 1, y - 1, 18, 18);
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(this.font, stack, x, y);
    }

    private Component objectiveText(QuestDefinition.Objective objective, int progress) {
        String target = objective.item()
                .map(this::itemName)
                .orElseGet(() -> objective.entity().map(this::entityName).orElse("?"));
        return Component.translatable("gui.echoes_of_survival.quest.objective_line", target, Math.min(progress, objective.count()), objective.count());
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
        if (quest.requireReputation().isEmpty()) return 0;
        return quest.requireReputation().get().map(
                value -> value,
                tier -> EosDatapackIndex.reputationTierByName(tier)
                        .map(ReputationTiersDefinition.Tier::min)
                        .orElse(0)
        );
    }

    private boolean meetsReputationRequirement(QuestDefinition quest) {
        if (quest == null || quest.requireReputation().isEmpty()) {
            return true;
        }
        int required = quest.requireReputation().get().map(
                value -> value,
                tier -> EosDatapackIndex.reputationTierByName(tier)
                        .map(ReputationTiersDefinition.Tier::min)
                        .orElse(Integer.MAX_VALUE)
        );
        return this.menu.playerReputation() >= required;
    }

    private boolean hasSubmissionItems(QuestDefinition quest) {
        for (QuestDefinition.Objective objective : quest.objectives()) {
            if (objective.item().isEmpty()) continue;
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(objective.item().get());
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


    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
