package com.flechazo.eos.data.common;

import cc.sighs.oelib.data.api.DataDriven;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

@DataDriven(folder = "healing_potions", syncToClient = true)
public record HealingPotionList(List<String> values) {
    public static final Codec<HealingPotionList> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("values").forGetter(HealingPotionList::values)
    ).apply(instance, HealingPotionList::new));
}
