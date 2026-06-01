package com.flechazo.eos.entity;

import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class EosEntityTypes {
    private EosEntityTypes() {
    }

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EchoesofSurvival.MODID);

    public static final Supplier<EntityType<FriendlySurvivorEntity>> FRIENDLY_SURVIVOR = ENTITIES.register(
            "friendly_survivor",
            () -> EntityType.Builder.of(FriendlySurvivorEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build("friendly_survivor")
    );

    public static final Supplier<EntityType<HostileSurvivorEntity>> HOSTILE_SURVIVOR = ENTITIES.register(
            "hostile_survivor",
            () -> EntityType.Builder.of(HostileSurvivorEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build("hostile_survivor")
    );

    public static final Supplier<EntityType<NeutralSurvivorEntity>> NEUTRAL_SURVIVOR = ENTITIES.register(
            "neutral_survivor",
            () -> EntityType.Builder.of(NeutralSurvivorEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build("neutral_survivor")
    );

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    @EventBusSubscriber(modid = EchoesofSurvival.MODID)
    public static final class Events {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(FRIENDLY_SURVIVOR.get(), FriendlySurvivorEntity.createAttributes().build());
            event.put(HOSTILE_SURVIVOR.get(), HostileSurvivorEntity.createAttributes().build());
            event.put(NEUTRAL_SURVIVOR.get(), NeutralSurvivorEntity.createAttributes().build());
        }
    }
}

