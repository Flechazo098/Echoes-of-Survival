package com.flechazo.eos.client;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.client.render.FriendlySurvivorRenderer;
import com.flechazo.eos.client.render.HostileSurvivorRenderer;
import com.flechazo.eos.client.render.NeutralSurvivorRenderer;
import com.flechazo.eos.client.screen.SurvivorQuestScreen;
import com.flechazo.eos.entity.EosEntityTypes;
import com.flechazo.eos.menu.EosMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = EchoesofSurvival.MODID, value = Dist.CLIENT)
public final class EosClientEvents {
    private EosClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(EosMenus.SURVIVOR_QUEST.get(), SurvivorQuestScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EosEntityTypes.FRIENDLY_SURVIVOR.get(), FriendlySurvivorRenderer::new);
        event.registerEntityRenderer(EosEntityTypes.HOSTILE_SURVIVOR.get(), HostileSurvivorRenderer::new);
        event.registerEntityRenderer(EosEntityTypes.NEUTRAL_SURVIVOR.get(), NeutralSurvivorRenderer::new);
    }
}
