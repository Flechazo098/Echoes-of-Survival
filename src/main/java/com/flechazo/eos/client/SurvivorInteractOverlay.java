package com.flechazo.eos.client;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.network.SurvivorInteractPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

public final class SurvivorInteractOverlay {
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 16;
    private static final int BASE_OFFSET_Y = 72;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_U = 1;
    private static final int BUTTON_V = 4;
    private static final int BUTTON_SOURCE_WIDTH = 62;
    private static final int BUTTON_SOURCE_HEIGHT = 8;
    private static final float BUTTON_SCALE = 1.75F;
    private static final int BUTTON_RENDER_WIDTH = Math.round(BUTTON_SOURCE_WIDTH * BUTTON_SCALE);
    private static final int BUTTON_RENDER_HEIGHT = Math.round(BUTTON_SOURCE_HEIGHT * BUTTON_SCALE);
    private static final int BUTTON_OFFSET_X = BUTTON_RENDER_WIDTH;
    private static final int TEXT_X = 8;
    private static final int TEXT_SOURCE_WIDTH = 53;
    private static final int TEXT = 0xFFF6EBD7;
    private static final int TEXT_HOVER = 0xFFFFFFFF;
    private static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EchoesofSurvival.MODID,
            "textures/gui/task_icon.png"
    );
    private static final ResourceLocation BUTTON_TEXTURE_HOVER = ResourceLocation.fromNamespaceAndPath(
            EchoesofSurvival.MODID,
            "textures/gui/task_icon_hovered.png"
    );
    private static final Component TRADE_TEXT = Component.translatable("gui.echoes_of_survival.interact.trade");
    private static final Component QUEST_TEXT = Component.translatable("gui.echoes_of_survival.interact.quest");
    private static final Component PERSONAL_TEXT = Component.translatable("gui.echoes_of_survival.interact.personal");

    private static int entityId = -1;
    private static boolean pendingSelection = false;

    private SurvivorInteractOverlay() {
    }

    public static void show(Minecraft minecraft, int targetEntityId) {
        if (entityId != targetEntityId) {
            if (isActive()) {
                hide(minecraft, true, true);
            }
            new SurvivorInteractPayload(targetEntityId, SurvivorInteractPayload.ACTION_OVERLAY_OPEN).sendToServer();
        }
        entityId = targetEntityId;
        pendingSelection = false;
        if (minecraft != null && minecraft.screen == null) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    public static void hide() {
        entityId = -1;
        pendingSelection = false;
    }

    public static void hide(Minecraft minecraft, boolean restoreMouse, boolean notifyServer) {
        if (notifyServer && isActive()) {
            new SurvivorInteractPayload(entityId, SurvivorInteractPayload.ACTION_OVERLAY_CLOSE).sendToServer();
        }
        hide();
        if (restoreMouse && minecraft != null && minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    public static boolean isActive() {
        return entityId >= 0;
    }

    public static void restoreMouseAfterFailedScreenOpen(Minecraft minecraft) {
        pendingSelection = false;
        if (minecraft != null && minecraft.screen == null && !isActive()) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    public static void tick(Minecraft minecraft) {
        if (!isActive()) return;
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            hide(minecraft, false, !pendingSelection);
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof FriendlySurvivorEntity survivor) || !survivor.isAlive() || minecraft.player.distanceToSqr(survivor) > 64.0) {
            hide(minecraft, true, !pendingSelection);
        }
    }

    public static void render(Minecraft minecraft, GuiGraphics graphics) {
        if (!isRenderable(minecraft)) return;

        Bounds bounds = bounds(minecraft);
        double mouseX = scaledMouseX(minecraft);
        double mouseY = scaledMouseY(minecraft);
        boolean tradeHovered = bounds.trade().contains(mouseX, mouseY);
        boolean questHovered = bounds.quest().contains(mouseX, mouseY);
        boolean personalHovered = bounds.recruit().contains(mouseX, mouseY);

        drawTab(graphics, minecraft, bounds.trade(), tradeHovered, TRADE_TEXT);
        drawTab(graphics, minecraft, bounds.quest(), questHovered, QUEST_TEXT);
        drawTab(graphics, minecraft, bounds.recruit(), personalHovered, PERSONAL_TEXT);
    }

    public static boolean handleMouseClick(Minecraft minecraft, int button, int action) {
        if (!isRenderable(minecraft) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) {
            return false;
        }

        Bounds bounds = bounds(minecraft);
        double mouseX = scaledMouseX(minecraft);
        double mouseY = scaledMouseY(minecraft);
        if (bounds.quest().contains(mouseX, mouseY)) {
            pendingSelection = true;
            new SurvivorInteractPayload(entityId, SurvivorInteractPayload.ACTION_QUEST).sendToServer();
            hide(minecraft, false, false);
            return true;
        }
        if (bounds.recruit().contains(mouseX, mouseY)) {
            pendingSelection = true;
            new SurvivorInteractPayload(entityId, SurvivorInteractPayload.ACTION_PERSONAL).sendToServer();
            hide(minecraft, false, false);
            return true;
        }
        if (bounds.trade().contains(mouseX, mouseY)) {
            pendingSelection = true;
            new SurvivorInteractPayload(entityId, SurvivorInteractPayload.ACTION_TRADE).sendToServer();
            hide(minecraft, false, false);
            return true;
        }

        hide(minecraft, true, true);
        return false;
    }

    private static boolean isRenderable(Minecraft minecraft) {
        return isActive() && minecraft != null && minecraft.player != null && minecraft.level != null && minecraft.screen == null;
    }

    private static void drawTab(GuiGraphics graphics, Minecraft minecraft, Rect rect, boolean hovered, Component text) {
        graphics.pose().pushPose();
        graphics.pose().translate(rect.x(), rect.y(), 0);
        graphics.pose().scale(BUTTON_SCALE, BUTTON_SCALE, 1.0F);
        graphics.blit(
                hovered ? BUTTON_TEXTURE_HOVER : BUTTON_TEXTURE,
                0,
                0,
                BUTTON_U,
                BUTTON_V,
                BUTTON_SOURCE_WIDTH,
                BUTTON_SOURCE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
        graphics.pose().popPose();

        int textAreaWidth = Math.round(TEXT_SOURCE_WIDTH * BUTTON_SCALE);
        Component trimmed = trimToWidth(minecraft, text, textAreaWidth);
        int textWidth = minecraft.font.width(trimmed);
        int textX = rect.x() + Math.round(TEXT_X * BUTTON_SCALE) + Math.max(0, (textAreaWidth - textWidth) / 2);
        int textY = rect.y() + Math.max(0, (BUTTON_RENDER_HEIGHT - minecraft.font.lineHeight) / 2);
        graphics.drawString(minecraft.font, trimmed, textX, textY, hovered ? TEXT_HOVER : TEXT, false);
    }

    private static Bounds bounds(Minecraft minecraft) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int tradeX = screenWidth / 2 - BUTTON_RENDER_WIDTH / 2 + BUTTON_OFFSET_X;
        int totalHeight = BUTTON_RENDER_HEIGHT * 3 + BUTTON_GAP * 2;
        int tradeY = screenHeight - BASE_OFFSET_Y - totalHeight;
        Rect trade = new Rect(tradeX, tradeY, BUTTON_RENDER_WIDTH, BUTTON_RENDER_HEIGHT);
        Rect quest = new Rect(tradeX, tradeY + BUTTON_RENDER_HEIGHT + BUTTON_GAP, BUTTON_RENDER_WIDTH, BUTTON_RENDER_HEIGHT);
        Rect recruit = new Rect(tradeX, quest.y() + BUTTON_RENDER_HEIGHT + BUTTON_GAP, BUTTON_RENDER_WIDTH, BUTTON_RENDER_HEIGHT);
        return new Bounds(trade, quest, recruit);
    }

    private static double scaledMouseX(Minecraft minecraft) {
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double scaledMouseY(Minecraft minecraft) {
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }

    private static Component trimToWidth(Minecraft minecraft, Component component, int width) {
        if (minecraft.font.width(component) <= width) {
            return component;
        }
        return Component.literal(minecraft.font.plainSubstrByWidth(component.getString(), Math.max(0, width - minecraft.font.width("..."))) + "...");
    }

    private record Rect(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record Bounds(Rect trade, Rect quest, Rect recruit) {
    }
}
