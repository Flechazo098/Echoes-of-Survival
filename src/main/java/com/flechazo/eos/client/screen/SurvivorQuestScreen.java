package com.flechazo.eos.client.screen;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.common.ItemStackDef;
import com.flechazo.eos.data.common.TextKey;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private static final int ROW_HEIGHT = 24;
    private static final int PANEL_COLOR = 0xE01B1B1B;
    private static final int LINE_COLOR = 0xFF5D5D5D;
    private static final int SELECTED_COLOR = 0xFF3D4F38;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int GOOD = 0xFF79D36B;
    private static final int GOLD = 0xFFFFD36A;

    private final List<Button> questButtons = new ArrayList<>();
    private final Inventory playerInventory;
    private Button acceptButton;
    private Button submitButton;
    private Button claimButton;
    private int selectedQuest = 0;

    public SurvivorQuestScreen(SurvivorQuestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.playerInventory = inventory;
        this.imageWidth = 364;
        this.imageHeight = 220;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.questButtons.clear();

        int listX = this.leftPos + 8;
        int listY = this.topPos + 24;
        int rows = Math.min(LIST_ROWS, this.menu.questIds().size());

        for (int i = 0; i < rows; i++) {
            int idx = i;
            Button row = Button.builder(Component.empty(), btn -> {
                selectedQuest = idx;
                updateActionButtons();
            }).bounds(listX, listY + (i * ROW_HEIGHT), 112, 22).build();
            this.questButtons.add(row);
            addRenderableWidget(row);
        }

        int buttonY = this.topPos + this.imageHeight - 28;
        this.acceptButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.echoes_of_survival.quest.accept"),
                btn -> {
                    sendButton(selectedQuest);
                    optimisticAccept();
                }
        ).bounds(this.leftPos + 128, buttonY, 58, 20).build());
        this.submitButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.echoes_of_survival.quest.submit"),
                btn -> {
                    sendButton(1000 + selectedQuest);
                    optimisticSubmit();
                }
        ).bounds(this.leftPos + 190, buttonY, 58, 20).build());
        this.claimButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.echoes_of_survival.quest.claim"),
                btn -> {
                    sendButton(2000 + selectedQuest);
                    optimisticClaim();
                }
        ).bounds(this.leftPos + 252, buttonY, 58, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.echoes_of_survival.quest.close"),
                btn -> onClose()
        ).bounds(this.leftPos + 314, buttonY, 48, 20).build());

        updateActionButtons();
    }

    private void updateActionButtons() {
        QuestDefinition quest = selectedQuest();
        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        boolean hasQuest = quest != null;
        boolean accepted = entry.accepted();
        boolean completed = entry.completed();
        boolean claimed = entry.claimed();

        this.acceptButton.active = hasQuest && !accepted && meetsReputationRequirement(quest);
        this.submitButton.active = hasQuest && accepted && !completed && quest.type().equals(QuestDefinition.TYPE_SUBMIT_ITEMS) && hasSubmissionItems(quest);
        this.claimButton.active = hasQuest && completed && !claimed;
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
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, PANEL_COLOR);
        graphics.fill(this.leftPos + 124, this.topPos + 20, this.leftPos + 125, this.topPos + this.imageHeight - 36, LINE_COLOR);
        graphics.fill(this.leftPos + 8, this.topPos + 20, this.leftPos + 120, this.topPos + 21, LINE_COLOR);
        graphics.fill(this.leftPos + 128, this.topPos + 20, this.leftPos + this.imageWidth - 8, this.topPos + 21, LINE_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, TEXT, false);

        List<ResourceLocation> quests = this.menu.questIds();
        int rows = Math.min(LIST_ROWS, quests.size());
        for (int i = 0; i < rows; i++) {
            int y = 24 + i * ROW_HEIGHT;
            if (i == selectedQuest) {
                graphics.fill(8, y, 120, y + 22, SELECTED_COLOR);
            }

            QuestDefinition quest = EosDatapackIndex.quest(quests.get(i)).orElse(null);
            Component name = quest != null ? stableText(quest.title()) : Component.literal(quests.get(i).toString());
            Component trimmed = this.font.width(name) > 100 ? Component.literal(this.font.plainSubstrByWidth(name.getString(), 97) + "...") : name;
            int color = this.menu.questEntry(i).claimed() ? MUTED : TEXT;
            graphics.drawString(this.font, trimmed, 12, y + 7, color, false);
        }

        renderQuestDetails(graphics);
    }

    private void renderQuestDetails(GuiGraphics graphics) {
        QuestDefinition quest = selectedQuest();
        if (quest == null) {
            graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.empty"), 132, 32, MUTED, false);
            return;
        }

        SurvivorQuestMenu.QuestEntry entry = this.menu.questEntry(selectedQuest);
        int x = 132;
        int y = 28;

        graphics.drawString(this.font, stableText(quest.title()), x, y, GOLD, false);
        y += 16;
        y = drawWrapped(graphics, stableText(quest.description()), x, y, 218, MUTED);
        y += 8;

        graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.objectives"), x, y, TEXT, false);
        y += 12;
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDefinition.Objective objective = quest.objectives().get(i);
            int progress = i < entry.objectiveProgress().size() ? entry.objectiveProgress().get(i) : 0;
            graphics.drawString(this.font, objectiveText(objective, progress), x + 8, y, objectiveDone(objective, progress) ? GOOD : MUTED, false);
            y += 11;
        }

        y += 6;
        graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.rewards"), x, y, TEXT, false);
        y += 12;
        for (ItemStackDef reward : quest.rewards().items()) {
            graphics.drawString(this.font, rewardText(reward), x + 8, y, GOLD, false);
            y += 11;
        }
        if (quest.rewards().reputation() != 0) {
            graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.reputation_reward", quest.rewards().reputation()), x + 8, y, GOLD, false);
            y += 11;
        }
        if (quest.requireReputation().isPresent()) {
            graphics.drawString(this.font, requiredReputationText(quest.requireReputation().get()), x + 8, y, MUTED, false);
            y += 11;
        }
        graphics.drawString(this.font, Component.translatable("gui.echoes_of_survival.quest.current_reputation", this.menu.playerReputation()), x + 8, y, MUTED, false);
        y += 11;

        y += 6;
        graphics.drawString(this.font, statusText(quest, entry), x, y, entry.claimed() || entry.completed() ? GOOD : MUTED, false);
    }

    private int drawWrapped(GuiGraphics graphics, Component component, int x, int y, int width, int color) {
        for (var line : this.font.split(component, width)) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
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

    private Component rewardText(ItemStackDef reward) {
        return Component.translatable("gui.echoes_of_survival.quest.item_reward", reward.count(), itemName(reward.item()));
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
