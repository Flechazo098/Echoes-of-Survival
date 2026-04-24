package com.flechazo.eos.util;

import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.resources.ResourceLocation;

public final class EosAliases {
    private EosAliases() {
    }

    /**
     * Datapack examples use {@code echoes:*}. Game registries for this mod use {@link EchoesofSurvival#MODID}.
     * <p>
     * For "mod-owned registry entries" (entity types, menus, etc.), normalize {@code echoes} to {@code echoes_of_survival}.
     * </p>
     */
    public static ResourceLocation normalizeToModNamespace(ResourceLocation id) {
        if (id == null) return null;
        if (EchoesofSurvival.DATAPACK_NAMESPACE.equals(id.getNamespace())) {
            return ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, id.getPath());
        }
        return id;
    }
}

