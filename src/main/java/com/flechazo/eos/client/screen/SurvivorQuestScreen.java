package com.flechazo.eos.client.screen;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.menu.SurvivorQuestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class SurvivorQuestScreen extends AbstractContainerScreen<SurvivorQuestMenu> {
    private static final int MAX_ROWS = 6;
    private static final int ROW_HEIGHT = 22;

    private final RandomSource random = RandomSource.create();

    public SurvivorQuestScreen(SurvivorQuestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 300;
        this.imageHeight = 32 + (MAX_ROWS * ROW_HEIGHT) + 20;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        int startX = this.leftPos + 8;
        int startY = this.topPos + 18;
        List<ResourceLocation> quests = this.menu.questIds();
        int rows = Math.min(MAX_ROWS, quests.size());

        for (int i = 0; i < rows; i++) {
            int y = startY + i * ROW_HEIGHT;
            int idx = i;

            addRenderableWidget(Button.builder(Component.literal("Accept"), btn -> sendButton(idx))
                    .bounds(startX + 170, y, 40, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Submit"), btn -> sendButton(1000 + idx))
                    .bounds(startX + 212, y, 40, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Claim"), btn -> sendButton(2000 + idx))
                    .bounds(startX + 254, y, 40, 20)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(this.leftPos + 8, this.topPos + this.imageHeight - 28, 60, 20)
                .build());
    }

    private void sendButton(int buttonId) {
        if (this.minecraft == null || this.minecraft.gameMode == null) return;
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xAA000000);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, 0xFFFFFF);

        List<ResourceLocation> quests = this.menu.questIds();
        int rows = Math.min(MAX_ROWS, quests.size());
        for (int i = 0; i < rows; i++) {
            ResourceLocation id = quests.get(i);
            QuestDefinition quest = EosDatapackIndex.quest(id).orElse(null);
            Component name = quest != null ? quest.title().toComponent(random) : Component.literal(id.toString());
            graphics.drawString(this.font, name, 8, 18 + i * ROW_HEIGHT + 6, 0xE0E0E0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
