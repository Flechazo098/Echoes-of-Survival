package com.flechazo.eos.client.render;

import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public final class SurvivorSkins {
    private SurvivorSkins() {
    }

    public static final List<ResourceLocation> PRESET_POOL = List.of(
            ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "textures/entity/survivor/friendly_survivor.png"),
            ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "textures/entity/survivor/medic.png"),
            ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "textures/entity/survivor/scavenger.png"),
            ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "textures/entity/survivor/mechanic.png")
    );

    public static ResourceLocation pick(UUID uuid, List<ResourceLocation> pool, ResourceLocation fallback) {
        if (pool == null || pool.isEmpty() || uuid == null) return fallback;
        int idx = Math.floorMod(uuid.hashCode(), pool.size());
        return pool.get(idx);
    }
}
