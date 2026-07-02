package com.flechazo.eos.client.screen;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class TexturedButton extends AbstractWidget {
    private static final int SCROLL_START_PAUSE_TICKS = 8;
    private static final int SCROLL_END_PAUSE_TICKS = 8;
    private static final int SCROLL_PIXELS_PER_TICK = 1;

    private final ResourceLocation texture;
    private final ResourceLocation pressedTexture;
    private final int sourceWidth;
    private final int sourceHeight;
    private final boolean scrollTextOnHover;
    private final Runnable onPress;
    protected boolean isPressedDown;
    protected boolean selected;
    private int textColor = 0xFF101010;
    private Font textFont;
    private long hoverScrollStartMillis = -1L;

    public TexturedButton(int x, int y, int width, int height, Component message, ResourceLocation texture, ResourceLocation pressedTexture, int sourceWidth, int sourceHeight, Runnable onPress) {
        this(x, y, width, height, message, texture, pressedTexture, sourceWidth, sourceHeight, false, null, onPress);
    }

    public TexturedButton(int x, int y, int width, int height, Component message, ResourceLocation texture, ResourceLocation pressedTexture, int sourceWidth, int sourceHeight, boolean scrollTextOnHover, Font font, Runnable onPress) {
        super(x, y, width, height, message);
        this.texture = texture;
        this.pressedTexture = pressedTexture;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.scrollTextOnHover = scrollTextOnHover;
        this.textFont = font;
        this.onPress = onPress;
    }

    public void setTextColor(int color) {
        this.textColor = color;
    }

    public void setTextFont(Font font) {
        this.textFont = font;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        resetHoverScroll();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.isPressedDown = true;
        if (this.active) {
            this.onPress.run();
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        this.isPressedDown = false;
    }

    private void resetHoverScroll() {
        this.hoverScrollStartMillis = -1L;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation tex = (this.selected || this.isPressedDown) && this.active ? this.pressedTexture : this.texture;
        float scale = (float) this.width / this.sourceWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(this.getX(), this.getY(), 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(tex, 0, 0, 0, 0, this.sourceWidth, this.sourceHeight, this.sourceWidth, this.sourceHeight);
        graphics.pose().popPose();

        Component msg = this.getMessage();
        if (!msg.getString().isEmpty() && this.textFont != null) {
            int color = this.active ? this.textColor : 0xFF303030;
            int padding = this.scrollTextOnHover ? (int)(5 * scale) : (int)(2 * scale);
            int textW = this.textFont.width(msg);
            int availW = this.width - padding * 2;
            if (this.scrollTextOnHover && this.isHovered() && textW > availW) {
                renderScrollingText(graphics, padding, color, textW, availW);
            } else {
                if (!this.isHovered()) resetHoverScroll();
                Component display = textW > availW ? trimToWidth(msg, availW) : msg;
                int tx = this.getX() + (this.width - this.textFont.width(display)) / 2;
                int ty = this.getY() + (this.height - 8) / 2;
                graphics.drawString(this.textFont, display, tx, ty, color, false);
            }
        }
    }

    private void renderScrollingText(GuiGraphics graphics, int padding, int color, int textWidth, int availableWidth) {
        long now = Util.getMillis();
        if (this.hoverScrollStartMillis < 0L) this.hoverScrollStartMillis = now;

        int overflow = Math.max(0, textWidth - availableWidth);
        long elapsed = Math.max(0L, (now - this.hoverScrollStartMillis) / 50L);
        long scrollTicks = Math.max(1L, (overflow + SCROLL_PIXELS_PER_TICK - 1L) / SCROLL_PIXELS_PER_TICK);

        int offset;
        if (elapsed < SCROLL_START_PAUSE_TICKS) {
            offset = 0;
        } else if (elapsed < scrollTicks + SCROLL_START_PAUSE_TICKS) {
            offset = Math.min(overflow, (int)((elapsed - SCROLL_START_PAUSE_TICKS) * SCROLL_PIXELS_PER_TICK));
        } else if (elapsed < scrollTicks + SCROLL_START_PAUSE_TICKS + SCROLL_END_PAUSE_TICKS) {
            offset = overflow;
        } else {
            this.hoverScrollStartMillis = now;
            offset = 0;
        }

        int minX = this.getX() + padding;
        int maxX = this.getX() + this.width - padding;
        int textY = this.getY() + (this.height - 8) / 2;
        graphics.enableScissor(minX, this.getY(), maxX, this.getY() + this.height);
        graphics.drawString(this.textFont, this.getMessage(), minX - offset, textY, color, false);
        graphics.disableScissor();
    }

    private Component trimToWidth(Component component, int width) {
        if (this.textFont == null || this.textFont.width(component) <= width) return component;
        return Component.literal(this.textFont.plainSubstrByWidth(component.getString(), Math.max(0, width - this.textFont.width("..."))) + "...");
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
