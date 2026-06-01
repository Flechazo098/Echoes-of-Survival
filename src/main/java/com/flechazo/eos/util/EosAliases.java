package com.flechazo.eos.util;

import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.resources.ResourceLocation;

public final class EosAliases {
    private EosAliases() {
    }

    public static ResourceLocation normalizeToModNamespace(ResourceLocation id) {
        if (id == null) return null;
        if (EchoesofSurvival.DATAPACK_NAMESPACE.equals(id.getNamespace())) {
            return ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, id.getPath());
        }
        return id;
    }
}

