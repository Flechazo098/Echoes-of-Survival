package com.flechazo.eos;

import com.flechazo.eos.config.EosConfigs;
import com.flechazo.eos.data.EosDataTypes;
import com.flechazo.eos.entity.EosEntityTypes;
import com.flechazo.eos.menu.EosMenus;
import com.flechazo.eos.reputation.EosAttachments;
import com.mojang.logging.LogUtils;
import cc.sighs.oelib.network.api.NetworkManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(EchoesofSurvival.MODID)
public class EchoesofSurvival {
    public static final String MODID = "echoes_of_survival";
    public static final String DATAPACK_NAMESPACE = "echoes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EchoesofSurvival(IEventBus bus, ModContainer container) {
        EosConfigs.register();
        EosDataTypes.register();

        EosEntityTypes.register(bus);
        EosMenus.register(bus);
        EosAttachments.register(bus);
        NetworkManager.registerPacketScanPackage("com.flechazo.eos.network");

        EosGameEvents.register();
    }
}
