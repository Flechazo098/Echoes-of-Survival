package com.flechazo.eos.client;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.client.render.FriendlySurvivorRenderer;
import com.flechazo.eos.client.render.HostileSurvivorRenderer;
import com.flechazo.eos.client.render.NeutralSurvivorRenderer;
import com.flechazo.eos.client.screen.SurvivorPersonalScreen;
import com.flechazo.eos.client.screen.SurvivorQuestScreen;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.entity.EosEntityTypes;
import com.flechazo.eos.menu.EosMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = EchoesofSurvival.MODID, value = Dist.CLIENT)
public final class EosClientEvents {
    private EosClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(EosMenus.SURVIVOR_QUEST.get(), SurvivorQuestScreen::new);
        event.register(EosMenus.SURVIVOR_PERSONAL.get(), SurvivorPersonalScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EosEntityTypes.FRIENDLY_SURVIVOR.get(), FriendlySurvivorRenderer::new);
        event.registerEntityRenderer(EosEntityTypes.HOSTILE_SURVIVOR.get(), HostileSurvivorRenderer::new);
        event.registerEntityRenderer(EosEntityTypes.NEUTRAL_SURVIVOR.get(), NeutralSurvivorRenderer::new);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof FriendlySurvivorEntity survivor)) return;

        SurvivorInteractOverlay.show(Minecraft.getInstance(), survivor.getId());
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        SurvivorInteractOverlay.render(Minecraft.getInstance(), event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (SurvivorInteractOverlay.handleMouseClick(Minecraft.getInstance(), event.getButton(), event.getAction())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SurvivorInteractOverlay.tick(Minecraft.getInstance());
    }
}
