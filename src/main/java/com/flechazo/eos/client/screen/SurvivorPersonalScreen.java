package com.flechazo.eos.client.screen;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.menu.SurvivorPersonalMenu;
import com.flechazo.eos.network.SurvivorInteractPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class SurvivorPersonalScreen extends AbstractContainerScreen<SurvivorPersonalMenu> {
    private static final int BASE_SCREEN_WIDTH = 284;
    private static final int BASE_SCREEN_HEIGHT = 166;
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    private static final float SCREEN_SCALE = 1.5F;
    private static final int SCREEN_WIDTH = scaled(BASE_SCREEN_WIDTH);
    private static final int SCREEN_HEIGHT = scaled(BASE_SCREEN_HEIGHT);
    private static final ResourceLocation SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EchoesofSurvival.MODID,
            "textures/gui/npc_screen.png"
    );
    private static final ResourceLocation TAB_BUTTON = ResourceLocation.fromNamespaceAndPath(
            EchoesofSurvival.MODID,
            "textures/gui/npc_screen_button.png"
    );
    private static final ResourceLocation TAB_BUTTON_PRESSED = ResourceLocation.fromNamespaceAndPath(
            EchoesofSurvival.MODID,
            "textures/gui/npc_screen_button_pressed.png"
    );
    private static final ResourceLocation RECRUIT_ICON = ResourceLocation.fromNamespaceAndPath(
            EchoesofSurvival.MODID,
            "textures/gui/recruit_icon.png"
    );
    private static final ResourceLocation DISMISS_ICON = ResourceLocation.fromNamespaceAndPath(
            EchoesofSurvival.MODID,
            "textures/gui/dismiss_icon.png"
    );
    private static final int ENTITY_X1 = 32;
    private static final int ENTITY_Y1 = 36;
    private static final int ENTITY_X2 = 82;
    private static final int ENTITY_Y2 = 108;

    private float xMouse;
    private float yMouse;

    public SurvivorPersonalScreen(SurvivorPersonalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = SCREEN_WIDTH;
        this.imageHeight = SCREEN_HEIGHT;
    }

    static int scaled(int value) {
        return Math.round(value * SCREEN_SCALE);
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - SCREEN_WIDTH) / 2;
        this.topPos = (this.height - SCREEN_HEIGHT) / 2;
        this.inventoryLabelY = this.imageHeight + 10;

        int bw = scaled(49);
        int bh = scaled(16);
        int gap = scaled(3);
        int bx = this.leftPos + scaled(119);
        int by = this.topPos + scaled(58);
        addRenderableWidget(new TexturedButton(bx, by, bw, bh, Component.empty(), TAB_BUTTON, TAB_BUTTON_PRESSED, 49, 16, () -> {}));
        addRenderableWidget(new TexturedButton(bx + bw + gap, by, bw, bh, Component.empty(), TAB_BUTTON, TAB_BUTTON_PRESSED, 49, 16, () -> {}));
        int recruitId = this.menu.survivorEntityId();
        int iw = scaled(16);
        int ih = scaled(16);
        int thirdX = bx + 2 * (bw + gap);
        int thirdY = by;
        Runnable recruitAction = () -> new SurvivorInteractPayload(recruitId, SurvivorInteractPayload.ACTION_RECRUIT).sendToServer();
        addRenderableWidget(new TexturedButton(thirdX, thirdY, bw, bh, Component.empty(), TAB_BUTTON, TAB_BUTTON_PRESSED, 49, 16, recruitAction) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                ResourceLocation bg = (this.selected || this.isPressedDown) && this.active ? TAB_BUTTON_PRESSED : TAB_BUTTON;
                float bs = (float) this.width / 49;
                g.pose().pushPose();
                g.pose().translate(this.getX(), this.getY(), 0);
                g.pose().scale(bs, bs, 1.0F);
                g.blit(bg, 0, 0, 0, 0, 49, 16, 49, 16);
                g.pose().popPose();
                var mc = SurvivorPersonalScreen.this.minecraft;
                Entity e = mc != null && mc.level != null ? mc.level.getEntity(recruitId) : null;
                boolean recruited = e instanceof FriendlySurvivorEntity s && s.isRecruited();
                float is = (float) iw / 16;
                int ix = this.getX() + (this.width - iw) / 2 - 24;
                int iy = this.getY() + (this.height - ih) / 2;
                g.pose().pushPose();
                g.pose().translate(ix, iy, 0);
                g.pose().scale(is, is, 1.0F);
                g.blit(recruited ? DISMISS_ICON : RECRUIT_ICON, 0, 0, 0, 0, 16, 16, 16, 16);
                g.pose().popPose();
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.xMouse = (float) mouseX;
        this.yMouse = (float) mouseY;
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return super.isHovering(x, y, scaled(width), scaled(height), mouseX, mouseY);
    }

    @Override
    protected void renderFloatingItem(GuiGraphics graphics, ItemStack stack, int x, int y, String text) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 8, y + 8, 232.0F);
        pose.scale(SCREEN_SCALE, SCREEN_SCALE, 1.0F);
        graphics.renderItem(stack, -8, -8);
        var font = IClientItemExtensions.of(stack).getFont(stack, IClientItemExtensions.FontContext.ITEM_COUNT);
        graphics.renderItemDecorations(font != null ? font : this.font, stack, -8, -8, text);
        pose.popPose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos, this.topPos, 0);
        graphics.pose().scale(SCREEN_SCALE, SCREEN_SCALE, 1.0F);
        graphics.blit(SCREEN_TEXTURE, 0, 0, 0, 0, BASE_SCREEN_WIDTH, BASE_SCREEN_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.pose().popPose();

        Entity entity = this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getEntity(this.menu.survivorEntityId()) : null;
        if (entity instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    this.leftPos + scaled(ENTITY_X1),
                    this.topPos + scaled(ENTITY_Y1),
                    this.leftPos + scaled(ENTITY_X2),
                    this.topPos + scaled(ENTITY_Y2),
                    36, 0.0625F, this.xMouse, this.yMouse, living
            );
        }
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(slot.x, slot.y, 0);
        graphics.pose().scale(SCREEN_SCALE, SCREEN_SCALE, 1.0F);
        graphics.renderItem(stack, 0, 0, 0);
        graphics.renderItemDecorations(this.font, stack, 0, 0, null);
        graphics.pose().popPose();
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (slot.isHighlightable()) {
            int size = scaled(16);
            graphics.fillGradient(RenderType.guiOverlay(), slot.x, slot.y, slot.x + size, slot.y + size, getSlotColor(slot.index), getSlotColor(slot.index), 0);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Entity entity = this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getEntity(this.menu.survivorEntityId()) : null;
        if (entity != null) {
            Component name = entity.getDisplayName();
            int areaX = scaled(4);
            int areaY = scaled(17);
            int areaWidth = scaled(105);
            int textWidth = this.font.width(name);
            if (textWidth > areaWidth) {
                name = Component.literal(this.font.plainSubstrByWidth(name.getString(), areaWidth - this.font.width("...")) + "...");
                textWidth = this.font.width(name);
            }
            graphics.drawString(this.font, name, areaX + (areaWidth - textWidth) / 2, areaY + (scaled(15) - this.font.lineHeight) / 2, 0xFF404040, false);
        }

        if (!(entity instanceof LivingEntity living)) return;

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(scaled(158), scaled(21), 0);
        pose.scale(SCREEN_SCALE, SCREEN_SCALE, 1.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();

        int sep = 10;
        int x = 4;
        int y = 2;
        int health = (int) living.getHealth();
        int heartCount = Mth.ceil(living.getMaxHealth() / 2.0F);
        for (int i = 0; i < heartCount; i++) {
            int px = x + i * sep;
            boolean half = i * 2 + 1 == health;
            boolean full = i * 2 + 1 < health;
            graphics.blitSprite(Gui.HeartType.CONTAINER.getSprite(false, false, false), px, y, 9, 9);
            if (full) {
                graphics.blitSprite(Gui.HeartType.NORMAL.getSprite(false, false, false), px, y, 9, 9);
            } else if (half) {
                graphics.blitSprite(Gui.HeartType.NORMAL.getSprite(false, false, true), px, y, 9, 9);
            }
        }

        y += 11;
        int food = entity instanceof FriendlySurvivorEntity s ? s.getSurvivorFood().getFoodLevel() : 20;
        for (int i = 0; i < 10; i++) {
            int px = x + i * sep;
            boolean half = i * 2 + 1 == food;
            boolean full = i * 2 + 1 < food;
            graphics.blitSprite(ResourceLocation.withDefaultNamespace("hud/food_empty"), px, y, 9, 9);
            if (full) {
                graphics.blitSprite(ResourceLocation.withDefaultNamespace("hud/food_full"), px, y, 9, 9);
            } else if (half) {
                graphics.blitSprite(ResourceLocation.withDefaultNamespace("hud/food_half"), px, y, 9, 9);
            }
        }

        y += 11;
        int armor = living.getArmorValue();
        for (int i = 0; i < 10; i++) {
            int px = x + i * sep;
            boolean half = i * 2 + 1 == armor;
            boolean full = i * 2 + 1 < armor;
            if (full) {
                graphics.blitSprite(ResourceLocation.withDefaultNamespace("hud/armor_full"), px, y, 9, 9);
            } else if (half) {
                graphics.blitSprite(ResourceLocation.withDefaultNamespace("hud/armor_half"), px, y, 9, 9);
            } else {
                graphics.blitSprite(ResourceLocation.withDefaultNamespace("hud/armor_empty"), px, y, 9, 9);
            }
        }

        RenderSystem.disableBlend();
        pose.popPose();
    }
}
