package com.flechazo.eos.client.screen;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.common.ItemStackDef;
import com.flechazo.eos.data.common.TextKey;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SurvivorQuestScreen extends AbstractContainerScreen<SurvivorQuestMenu> {
    private static final int LIST_ROWS = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int SCREEN_WIDTH = 282;
    private static final int SCREEN_HEIGHT = 165;
    private static final int TASK_BUTTON_WIDTH = 59;
    private static final int TASK_BUTTON_HEIGHT = 18;
    private static final int RECEIVE_BUTTON_WIDTH = 194;
    private static final int RECEIVE_BUTTON_HEIGHT = 12;
    private static final int TEXT = 0xFF101010;
    private static final int MUTED = 0xFF303030;
    private static final int GOOD = 0xFF1F6E2F;
    private static final int GOLD = 0xFF1A1A1A;
    private static final ResourceLocation SCREEN_TEXTURE = guiTexture("npc_screen");
    private static final ResourceLocation TASK_BUTTON = guiTexture("task_button");
    private static final ResourceLocation TASK_BUTTON_PRESSED = guiTexture("task_button_pressed");
    private static final ResourceLocation RECEIVE_BUTTON = guiTexture("receive_button");
    private static final ResourceLocation RECEIVE_BUTTON_PRESSED = guiTexture("receive_button_pressed");

    private final List<TexturedButton> questButtons = new ArrayList<>();
    private final Inventory playerInventory;
    private TexturedButton actionButton;
    private Action action = Action.NONE;
    private int selectedQuest = 0;

    public SurvivorQuestScreen(SurvivorQuestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.playerInventory = inventory;
        this.imageWidth = SCREEN_WIDTH;
        this.imageHeight = SCREEN_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private static ResourceLocation guiTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "textures/gui/" + name + ".png");
    }

    @Override
    protected void init() {
        super.init();
        this.questButtons.clear();

        int listX = this.leftPos + 6;
        int listY = this.topPos + 18;
        int rows = Math.min(LIST_ROWS, this.menu.questIds().size());

        for (int i = 0; i < rows; i++) {
            int idx = i;
            TexturedButton row = new TexturedButton(listX, listY + (i * ROW_HEIGHT), TASK_BUTTON_WIDTH, TASK_BUTTON_HEIGHT, Component.empty(), TASK_BUTTON, TASK_BUTTON_PRESSED, () -> {
                selectedQuest = idx;
                updateActionButtons();
            });
            this.questButtons.add(row);
            addRenderableWidget(row);
        }

        int receiveX = this.leftPos + 74;
        int receiveY = this.topPos + this.imageHeight - 20;
        this.actionButton = addRenderableWidget(new TexturedButton(
                receiveX,
                receiveY,
                RECEIVE_BUTTON_WIDTH,
                RECEIVE_BUTTON_HEIGHT,
                Component.empty(),
                RECEIVE_BUTTON,
                RECEIVE_BUTTON_PRESSED,
                () -> {
                    switch (this.action) {
                        case ACCEPT -> {
                            sendButton(selectedQuest);
                            optimisticAccept();
                        }
                        case SUBMIT -> {
                            sendButton(1000 + selectedQuest);
                            optimisticSubmit();
                        }
                        case CLAIM -> {
                            sendButton(2000 + selectedQuest);
                            optimisticClaim();
                        }
                        default -> {
                        }
                    }
                }
        ));

        updateActionButtons();
    }

    private void updateActionButtons() {
        QuestDefinition quest = selectedQuest();
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        boolean hasQuest = quest != null;
        boolean accepted = entry.accepted();
        boolean completed = entry.completed();
        boolean claimed = entry.claimed();

        this.action = Action.NONE;
        Component message = Component.translatable("gui.echoes_of_survival.quest.status.available");
        boolean active = false;
        if (!hasQuest) {
            message = Component.translatable("gui.echoes_of_survival.quest.empty");
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

    private void sendButton(int buttonId) {
        if (this.minecraft == null || this.minecraft.gameMode == null) return;
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
    }

    private void optimisticAccept() {
        QuestDefinition quest = selectedQuest();
        if (quest == null || !meetsReputationRequirement(quest)) return;
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        if (entry.accepted()) return;

        this.menu.updateQuestEntry(selectedQuest, new SurvivorQuestMenu.QuestEntry(
                quest.questId(),
                zeroProgress(quest),
                false,
                false
        ));
        updateActionButtons();
    }

    private void optimisticSubmit() {
        QuestDefinition quest = selectedQuest();
        if (quest == null || !quest.type().equals(QuestDefinition.TYPE_SUBMIT_ITEMS) || !hasSubmissionItems(quest)) return;
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        if (!entry.accepted() || entry.completed()) return;

        this.menu.updateQuestEntry(selectedQuest, new SurvivorQuestMenu.QuestEntry(
                quest.questId(),
                completedProgress(quest),
                true,
                entry.claimed()
        ));
        updateActionButtons();
    }

    private void optimisticClaim() {
        QuestDefinition quest = selectedQuest();
        if (quest == null) return;
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        if (!entry.completed() || entry.claimed()) return;

        this.menu.updateQuestEntry(selectedQuest, new SurvivorQuestMenu.QuestEntry(
                quest.questId(),
                entry.objectiveProgress(),
                true,
                true
        ));
        this.menu.addPlayerReputation(quest.rewards().reputation());
        updateActionButtons();
    }

    private List<Integer> zeroProgress(QuestDefinition quest) {
        return quest.objectives().stream().map(objective -> 0).toList();
    }

    private List<Integer> completedProgress(QuestDefinition quest) {
        return quest.objectives().stream().map(QuestDefinition.Objective::count).toList();
    }

    private QuestDefinition selectedQuest() {
        if (selectedQuest < 0 || selectedQuest >= this.menu.questIds().size()) return null;
        return EosDatapackIndex.quest(this.menu.questIds().get(selectedQuest)).orElse(null);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(SCREEN_TEXTURE, this.leftPos, this.topPos, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, 512, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ResourceLocation> quests = this.menu.questIds();
        int rows = Math.min(LIST_ROWS, quests.size());
        for (int i = 0; i < rows; i++) {
            int y = 18 + i * ROW_HEIGHT;

            QuestDefinition quest = EosDatapackIndex.quest(quests.get(i)).orElse(null);
            Component name = quest != null ? stableText(quest.title()) : Component.literal(quests.get(i).toString());
            Component trimmed = trimToWidth(name, 52);
            int color = this.menu.questEntry(i).claimed() ? MUTED : TEXT;
            graphics.drawString(this.font, trimmed, 9, y + 5, color, false);
        }

        renderQuestDetails(graphics);
    }

    private void renderQuestDetails(GuiGraphics graphics) {
        QuestDefinition quest = selectedQuest();
        if (quest == null) {
            graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.empty"), 88, 34, MUTED, false);
            return;
        }

        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        int x = 88;

        graphics.drawString(this.font, trimToWidth(stableText(quest.title()), 170), x, 35, GOLD, false);

        int y = 62;
        y = drawWrapped(graphics, stableText(quest.description()), x, y, 184, MUTED, 3);
        y += 4;

        graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.objectives"), x, y, TEXT, false);
        y += 12;
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDefinition.Objective objective = quest.objectives().get(i);
            int progress = i < entry.objectiveProgress().size() ? entry.objectiveProgress().get(i) : 0;
            graphics.drawString(this.font, objectiveText(objective, progress), x + 8, y, objectiveDone(objective, progress) ? GOOD : MUTED, false);
            y += 11;
            if (y > 100) break;
        }

        if (!meetsReputationRequirement(quest)) {
            graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.reputation_required", reputationRequirementValue(quest)), x, 117, MUTED, false);
            return;
        }
        y = 117;
        graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.rewards"), x, y, TEXT, false);
        int rewardX = x;
        int rewardY = 130;
        for (int i = 0; i < quest.rewards().items().size(); i++) {
            ItemStackDef reward = quest.rewards().items().get(i);
            renderRewardIcon(graphics, reward, rewardX + i * 20, rewardY);
            if (i >= 1) break;
        }
        if (quest.rewards().reputation() != 0) {
            graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.reputation_reward", quest.rewards().reputation()), x + 52, 132, GOLD, false);
        }
        graphics.drawString(this.font, statusText(quest, entry), 205, 117, entry.claimed() || entry.completed() ? GOOD : MUTED, false);
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

    private Component stableText(TextKey key) {
        if (key.keys() == null || key.keys().isEmpty()) {
            return Component.empty();
        }
        return Component.translatable(key.keys().getFirst());
    }

    private int reputationRequirementValue(QuestDefinition quest) {
        if (quest.requireReputation().isEmpty()) return 0;
        return quest.requireReputation().get().map(
                value -> value,
                tier -> EosDatapackIndex.reputationTierByName(tier)
                        .map(repTier -> repTier.min())
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
                        .map(repTier -> repTier.min())
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

    private class TexturedButton extends AbstractWidget {
        private final ResourceLocation texture;
        private final ResourceLocation hoverTexture;
        private final Runnable onPress;

        private TexturedButton(int x, int y, int width, int height, Component message, ResourceLocation texture, ResourceLocation hoverTexture, Runnable onPress) {
            super(x, y, width, height, message);
            this.texture = texture;
            this.hoverTexture = hoverTexture;
            this.onPress = onPress;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (this.active) {
                this.onPress.run();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ResourceLocation current = this.isHoveredOrFocused() && this.active ? this.hoverTexture : this.texture;
            int textureWidth = current == RECEIVE_BUTTON || current == RECEIVE_BUTTON_PRESSED ? RECEIVE_BUTTON_WIDTH : this.width;
            int textureHeight = current == RECEIVE_BUTTON || current == RECEIVE_BUTTON_PRESSED ? RECEIVE_BUTTON_HEIGHT : this.height;
            graphics.blit(current, this.getX(), this.getY(), 0, 0, this.width, this.height, textureWidth, textureHeight);
            if (!this.getMessage().getString().isEmpty()) {
                int color = this.active ? TEXT : MUTED;
                int textX = this.getX() + (this.width - SurvivorQuestScreen.this.font.width(this.getMessage())) / 2;
                int textY = this.getY() + (this.height - 8) / 2;
                graphics.drawString(SurvivorQuestScreen.this.font, this.getMessage(), textX, textY, color, false);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
