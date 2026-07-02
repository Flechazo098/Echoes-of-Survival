package com.flechazo.eos.entity;

import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

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

        @SubscribeEvent
        public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
            event.register(
                    HOSTILE_SURVIVOR.get(),
                    SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    EosEntityTypes.Events::checkHostileSurvivorSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE
            );
            event.register(
                    NEUTRAL_SURVIVOR.get(),
                    SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    EosEntityTypes.Events::checkNeutralSurvivorSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE
            );
        }

        private static boolean checkHostileSurvivorSpawnRules(EntityType<HostileSurvivorEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
            return level.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
        }

        private static boolean checkNeutralSurvivorSpawnRules(EntityType<NeutralSurvivorEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
            return level.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
        }
    }
}
