package com.flechazo.eos.data;

import cc.sighs.oelib.data.DataManager;
import cc.sighs.oelib.neoforge.event.DataReloadEvent;
import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.data.armor.ArmorSetDefinition;
import com.flechazo.eos.data.common.HealingPotionList;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.data.quest.QuestPoolDefinition;
import com.flechazo.eos.data.reputation.ReputationEventsDefinition;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.data.skin.SkinLibraryDefinition;
import com.flechazo.eos.data.trade.ProfessionDefinition;
import com.flechazo.eos.data.trade.TradePoolDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

@EventBusSubscriber(modid = EchoesofSurvival.MODID)
public final class EosDatapackIndex {
    private EosDatapackIndex() {
    }

    private static volatile Map<ResourceLocation, ProfessionDefinition> professionsById = Map.of();
    private static volatile Map<ResourceLocation, TradePoolDefinition> tradePoolsByFileId = Map.of();
    private static volatile Map<ResourceLocation, List<TradePoolDefinition>> tradePoolsByProfession = Map.of();
    private static volatile Map<ResourceLocation, QuestDefinition> questsById = Map.of();
    private static volatile Map<ResourceLocation, QuestPoolDefinition> questPoolsByFileId = Map.of();
    private static volatile Map<ResourceLocation, ArmorSetDefinition> armorSetsByFileId = Map.of();
    private static volatile Map<String, ReputationTiersDefinition.Tier> reputationTierByName = Map.of();
    private static volatile List<Map.Entry<String, ReputationTiersDefinition.Tier>> reputationTiers = List.of();
    private static volatile Map<String, ReputationEventsDefinition.ReputationEvent> reputationEventById = Map.of();
    private static volatile List<UUID> skinLibraryUuids = List.of();
    private static volatile List<String> healingPotionPatterns = List.of();

    @SubscribeEvent
    public static void onDataReload(DataReloadEvent event) {
        Class<?> type = event.getDataClass();

        if (type == ProfessionDefinition.class) rebuildProfessions();
        if (type == TradePoolDefinition.class) rebuildTradePools();
        if (type == QuestDefinition.class) rebuildQuests();
        if (type == QuestPoolDefinition.class) rebuildQuestPools();
        if (type == ArmorSetDefinition.class) rebuildArmorSets();
        if (type == ReputationTiersDefinition.class) rebuildReputationTiers();
        if (type == ReputationEventsDefinition.class) rebuildReputationEvents();
        if (type == SkinLibraryDefinition.class) rebuildSkinLibrary();
        if (type == HealingPotionList.class) rebuildHealingPotions();
    }

    private static void rebuildProfessions() {
        Map<ResourceLocation, ProfessionDefinition> map = new HashMap<>();
        for (ProfessionDefinition def : DataManager.getDataList(ProfessionDefinition.class)) {
            if (def != null && def.id() != null) {
                map.put(def.id(), def);
            }
        }
        professionsById = Map.copyOf(map);
    }

    private static void rebuildTradePools() {
        Map<ResourceLocation, TradePoolDefinition> fileMap = DataManager.getAllData(TradePoolDefinition.class);
        tradePoolsByFileId = Map.copyOf(fileMap);

        Map<ResourceLocation, List<TradePoolDefinition>> byProfession = new HashMap<>();
        for (TradePoolDefinition pool : fileMap.values()) {
            if (pool == null || pool.profession() == null) continue;
            byProfession.computeIfAbsent(pool.profession(), k -> new ArrayList<>()).add(pool);
        }
        byProfession.replaceAll((k, v) -> List.copyOf(v));
        tradePoolsByProfession = Map.copyOf(byProfession);
    }

    private static void rebuildQuests() {
        Map<ResourceLocation, QuestDefinition> map = new HashMap<>();
        for (QuestDefinition quest : DataManager.getDataList(QuestDefinition.class)) {
            if (quest != null && quest.questId() != null) {
                map.put(quest.questId(), quest);
            }
        }
        questsById = Map.copyOf(map);
    }

    private static void rebuildQuestPools() {
        questPoolsByFileId = Map.copyOf(DataManager.getAllData(QuestPoolDefinition.class));
    }

    private static void rebuildArmorSets() {
        armorSetsByFileId = Map.copyOf(DataManager.getAllData(ArmorSetDefinition.class));
    }

    private static void rebuildReputationTiers() {
        Map<String, ReputationTiersDefinition.Tier> byName = new HashMap<>();
        List<Map.Entry<String, ReputationTiersDefinition.Tier>> list = new ArrayList<>();
        for (ReputationTiersDefinition def : DataManager.getDataList(ReputationTiersDefinition.class)) {
            if (def == null || def.values() == null) continue;
            for (Map<String, ReputationTiersDefinition.Tier> tierMap : def.values().values()) {
                if (tierMap == null) continue;
                tierMap.forEach((name, tier) -> {
                    if (name != null && tier != null) {
                        byName.put(name, tier);
                        list.add(Map.entry(name, tier));
                    }
                });
            }
        }
        reputationTierByName = Map.copyOf(byName);
        reputationTiers = List.copyOf(list);
    }

    private static void rebuildReputationEvents() {
        Map<String, ReputationEventsDefinition.ReputationEvent> byId = new HashMap<>();
        for (ReputationEventsDefinition def : DataManager.getDataList(ReputationEventsDefinition.class)) {
            if (def == null || def.events() == null) continue;
            for (ReputationEventsDefinition.ReputationEvent event : def.events()) {
                if (event != null && event.id() != null && !event.id().isBlank()) {
                    byId.put(event.id(), event);
                }
            }
        }
        reputationEventById = Map.copyOf(byId);
    }

    private static void rebuildSkinLibrary() {
        List<UUID> list = new ArrayList<>();
        for (SkinLibraryDefinition def : DataManager.getDataList(SkinLibraryDefinition.class)) {
            if (def == null || def.uuids() == null) continue;
            for (UUID uuid : def.uuids()) {
                if (uuid != null) list.add(uuid);
            }
        }
        skinLibraryUuids = List.copyOf(list);
    }

    private static void rebuildHealingPotions() {
        List<String> list = new ArrayList<>();
        for (HealingPotionList def : DataManager.getDataList(HealingPotionList.class)) {
            if (def != null && def.values() != null) list.addAll(def.values());
        }
        healingPotionPatterns = List.copyOf(list);
    }

    public static Optional<ProfessionDefinition> profession(ResourceLocation id) {
        return Optional.ofNullable(professionsById.get(id));
    }

    public static Optional<ProfessionDefinition> randomProfession() {
        if (professionsById.isEmpty()) return Optional.empty();
        int idx = (int) (Math.random() * professionsById.size());
        var list = professionsById.values().stream().toList();
        return idx >= 0 && idx < list.size() ? Optional.of(list.get(idx)) : Optional.empty();
    }

    public static boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty() || healingPotionPatterns.isEmpty()) return false;

        String type;
        if (stack.is(Items.POTION)) type = "potion";
        else if (stack.is(Items.SPLASH_POTION)) type = "splash";
        else if (stack.is(Items.LINGERING_POTION)) type = "lingering";
        else return false;

        var contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;

        if (contents.potion().isPresent()) {
            String path = contents.potion().get().unwrapKey()
                    .map(k -> k.location().getPath()).orElse("");
            if (!path.isEmpty() && healingPotionPatterns.contains(type + ":" + path)) return true;
        }

        for (var effect : contents.customEffects()) {
            String path = effect.getEffect().unwrapKey()
                    .map(k -> k.location().getPath()).orElse("");
            if (!path.isEmpty() && healingPotionPatterns.contains(type + ":" + path)) return true;
        }

        return false;
    }

    public static Optional<QuestDefinition> quest(ResourceLocation id) {
        return Optional.ofNullable(questsById.get(id));
    }

    public static Optional<QuestPoolDefinition> questPool(ResourceLocation id) {
        return Optional.ofNullable(questPoolsByFileId.get(id));
    }

    public static Optional<ArmorSetDefinition> armorSet(ResourceLocation id) {
        return Optional.ofNullable(armorSetsByFileId.get(id));
    }

    public static List<TradePoolDefinition> tradePools(ResourceLocation professionId, List<ResourceLocation> requestedPoolIds) {
        if (requestedPoolIds != null && !requestedPoolIds.isEmpty()) {
            List<TradePoolDefinition> resolved = new ArrayList<>();
            for (ResourceLocation id : requestedPoolIds) {
                TradePoolDefinition pool = tradePoolsByFileId.get(id);
                if (pool != null) resolved.add(pool);
            }
            if (!resolved.isEmpty()) return List.copyOf(resolved);
        }
        return tradePoolsByProfession.getOrDefault(professionId, List.of());
    }

    public static List<ResourceLocation> questIdsFromPools(List<ResourceLocation> poolIds) {
        if (poolIds == null || poolIds.isEmpty()) return List.of();
        List<ResourceLocation> result = new ArrayList<>();

        for (ResourceLocation poolId : poolIds) {
            QuestPoolDefinition pool = questPoolsByFileId.get(poolId);
            if (pool == null) continue;
            for (ResourceLocation questId : pool.quests()) {
                if (questsById.containsKey(questId)) {
                    result.add(questId);
                }
            }
        }
        return List.copyOf(result);
    }

    public static Optional<ReputationTiersDefinition.Tier> reputationTierByName(String name) {
        return Optional.ofNullable(reputationTierByName.get(name));
    }

    public static List<Map.Entry<String, ReputationTiersDefinition.Tier>> reputationTiers() {
        return reputationTiers;
    }

    public static Optional<ReputationEventsDefinition.ReputationEvent> reputationEvent(String id) {
        return Optional.ofNullable(reputationEventById.get(id));
    }

    public static List<UUID> skinLibraryUuids() {
        return skinLibraryUuids;
    }
}
