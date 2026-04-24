package com.flechazo.eos.data.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record ItemStackDef(ResourceLocation item, int count) {
    public static final Codec<ItemStackDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(ItemStackDef::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ItemStackDef::count)
    ).apply(instance, ItemStackDef::new));

    public Optional<Item> resolveItem() {
        return BuiltInRegistries.ITEM.getOptional(item);
    }

    public ItemStack toStack() {
        Item resolved = resolveItem().orElse(null);
        if (resolved == null) {
            return ItemStack.EMPTY;
        }
        int safeCount = Math.max(1, count);
        return new ItemStack(resolved, safeCount);
    }
}

