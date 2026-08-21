package com.flechazo.eos.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.client.SurvivorInteractOverlay;
import net.minecraft.client.Minecraft;

@NetworkPacket(
        modId = EchoesofSurvival.MODID,
        id = "survivor_screen_open_failed",
        side = Side.CLIENT
)
public record SurvivorScreenOpenFailedPayload(int survivorEntityId)
        implements INetworkPacket<SurvivorScreenOpenFailedPayload> {

    @Override
    public void handle(INetworkContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = context.client();
            if (minecraft == null || minecraft.player == null) return;
            SurvivorInteractOverlay.restoreMouseAfterFailedScreenOpen(minecraft);
        });
    }
}
