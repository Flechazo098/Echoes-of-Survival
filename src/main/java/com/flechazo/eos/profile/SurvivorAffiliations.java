package com.flechazo.eos.profile;

import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.resources.ResourceLocation;

public final class SurvivorAffiliations {
    public static final ResourceLocation PDC = id("pdc");
    public static final ResourceLocation INDEPENDENT = id("independent");
    public static final ResourceLocation RAIDER = id("raider");
    public static final ResourceLocation ABYSS_CHURCH = id("abyss_church");

    private SurvivorAffiliations() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, path);
    }
}
