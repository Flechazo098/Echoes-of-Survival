package com.flechazo.eos.encounter;

import com.flechazo.eos.config.EosConfigs;
import com.flechazo.eos.entity.AbstractSurvivorEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Transitional encounter gate. Existing biome modifiers provide candidate
 * locations, while this manager owns regional cooldown and density decisions.
 * Structure, command, event, and camp-driven spawns bypass the natural gate.
 */
public final class SurvivorEncounterManager {
    private static final Map<ServerLevel, Map<Long, Long>> LAST_ENCOUNTER_TICK = new WeakHashMap<>();

    private SurvivorEncounterManager() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SurvivorEncounterManager::onFinalizeSpawn);
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof AbstractSurvivorEntity)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL
                && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;

        var config = EosConfigs.SURVIVOR.get();
        if (!config.allowBiomeSpawnSupplement() || config.maxActiveSurvivorsPerRegion() <= 0) {
            event.setSpawnCancelled(true);
            return;
        }

        int regionChunks = Math.max(1, config.encounterRegionSizeChunks());
        int chunkX = ((int) Math.floor(event.getX())) >> 4;
        int chunkZ = ((int) Math.floor(event.getZ())) >> 4;
        int regionX = Math.floorDiv(chunkX, regionChunks);
        int regionZ = Math.floorDiv(chunkZ, regionChunks);
        long key = ((long) regionX << 32) ^ (regionZ & 0xffffffffL);
        long now = level.getGameTime();

        Map<Long, Long> cooldowns = LAST_ENCOUNTER_TICK.computeIfAbsent(level, ignored -> new HashMap<>());
        long last = cooldowns.getOrDefault(key, Long.MIN_VALUE / 2);
        if (now - last < config.encounterCooldownTicks()) {
            event.setSpawnCancelled(true);
            return;
        }

        double minX = (double) regionX * regionChunks * 16;
        double minZ = (double) regionZ * regionChunks * 16;
        double maxX = minX + regionChunks * 16;
        double maxZ = minZ + regionChunks * 16;
        AABB region = new AABB(minX, level.getMinBuildHeight(), minZ,
                maxX, level.getMaxBuildHeight(), maxZ);
        int active = level.getEntitiesOfClass(AbstractSurvivorEntity.class, region,
                survivor -> survivor.isAlive()).size();
        if (active >= config.maxActiveSurvivorsPerRegion()) {
            event.setSpawnCancelled(true);
            return;
        }

        cooldowns.put(key, now);
    }
}
