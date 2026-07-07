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
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.hkt.business.util.OptionalOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;
import java.util.function.Predicate;

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
    private static volatile List<SkinProfile> skinLibraryProfiles = List.of();
    private static volatile Map<ResourceLocation, List<SkinProfile>> skinProfilesByLibrary = Map.of();
    private static volatile Map<UUID, String> skinLibraryUsernamesByUuid = Map.of();
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
        Map<UUID, String> byUuid = new HashMap<>();
        Map<ResourceLocation, List<SkinProfile>> byLibrary = new HashMap<>();
        List<SkinProfile> allProfiles = new ArrayList<>();
        for (SkinLibraryDefinition def : DataManager.getDataList(SkinLibraryDefinition.class)) {
            if (def == null || def.skins() == null) continue;
            List<SkinProfile> profiles = new ArrayList<>();
            for (SkinLibraryDefinition.SkinEntry entry : def.skins()) {
                if (entry == null || entry.name() == null || entry.name().isBlank()) continue;
                SkinProfile profile = SkinProfile.from(entry);
                profiles.add(profile);
                profile.uuid().ifPresent(uuid -> {
                    byUuid.put(uuid, profile.name());
                    allProfiles.add(profile);
                });
            }
            if (def.id() != null && !profiles.isEmpty()) {
                byLibrary.put(def.id(), List.copyOf(profiles));
            }
        }
        skinLibraryUsernamesByUuid = Map.copyOf(byUuid);
        skinProfilesByLibrary = Map.copyOf(byLibrary);
        skinLibraryProfiles = List.copyOf(allProfiles);
    }

    private static void rebuildHealingPotions() {
        List<String> list = new ArrayList<>();
        for (HealingPotionList def : DataManager.getDataList(HealingPotionList.class)) {
            if (def != null && def.values() != null) list.addAll(def.values());
        }
        healingPotionPatterns = List.copyOf(list);
    }

    public static Maybe<ProfessionDefinition> profession(ResourceLocation id) {
        return Maybe.ofNullable(professionsById.get(id));
    }

    public static Maybe<ProfessionDefinition> randomProfession() {
        if (professionsById.isEmpty()) return Maybe.none();
        int idx = (int) (Math.random() * professionsById.size());
        return Pathway.listPath(professionsById.values().stream().toList()).get(idx).run();
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

    public static Maybe<QuestDefinition> quest(ResourceLocation id) {
        return Maybe.ofNullable(questsById.get(id));
    }

    public static Maybe<QuestPoolDefinition> questPool(ResourceLocation id) {
        return Maybe.ofNullable(questPoolsByFileId.get(id));
    }

    public static Maybe<ArmorSetDefinition> armorSet(ResourceLocation id) {
        return Maybe.ofNullable(armorSetsByFileId.get(id));
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
        return questIdsFromPools(poolIds, new Random(), questId -> true);
    }

    public static List<ResourceLocation> questIdsFromPools(List<ResourceLocation> poolIds, Random random) {
        return questIdsFromPools(poolIds, random, questId -> true);
    }

    public static List<ResourceLocation> questIdsFromPools(List<ResourceLocation> poolIds, Random random, Predicate<ResourceLocation> questFilter) {
        if (poolIds == null || poolIds.isEmpty()) return List.of();
        List<ResourceLocation> result = new ArrayList<>();

        for (ResourceLocation poolId : poolIds) {
            QuestPoolDefinition pool = questPoolsByFileId.get(poolId);
            if (pool == null) continue;
            List<ResourceLocation> candidates = new ArrayList<>();
            for (ResourceLocation questId : pool.quests()) {
                if (questsById.containsKey(questId) && (questFilter == null || questFilter.test(questId))) {
                    candidates.add(questId);
                }
            }
            if (candidates.isEmpty()) continue;
            Collections.shuffle(candidates, random);
            int rolls = Math.min(pool.rolls(), candidates.size());
            for (int i = 0; i < rolls; i++) {
                result.add(candidates.get(i));
            }
        }
        return List.copyOf(result);
    }

    public static Maybe<ReputationTiersDefinition.Tier> reputationTierByName(String name) {
        return Maybe.ofNullable(reputationTierByName.get(name));
    }

    public static List<Map.Entry<String, ReputationTiersDefinition.Tier>> reputationTiers() {
        return reputationTiers;
    }

    public static Maybe<ReputationEventsDefinition.ReputationEvent> reputationEvent(String id) {
        return Maybe.ofNullable(reputationEventById.get(id));
    }

    public static List<UUID> skinLibraryUuids() {
        return skinLibraryProfiles.stream().flatMap(profile -> profile.uuid().toList().stream()).toList();
    }

    public static List<SkinProfile> skinLibraryProfiles() {
        return skinLibraryProfiles;
    }

    public static Maybe<SkinProfile> pickSkinProfile(UUID seed) {
        if (seed == null || skinLibraryProfiles.isEmpty()) return Maybe.none();
        int idx = Math.floorMod(seed.hashCode(), skinLibraryProfiles.size());
        return Pathway.listPath(skinLibraryProfiles).get(idx).run();
    }

    public static Maybe<SkinProfile> pickSkinProfile(ResourceLocation library, UUID seed) {
        if (library == null || seed == null) return Maybe.none();
        List<SkinProfile> profiles = skinProfilesByLibrary.getOrDefault(library, List.of());
        if (profiles.isEmpty()) return Maybe.none();
        int idx = Math.floorMod(seed.hashCode(), profiles.size());
        return Pathway.listPath(profiles).get(idx).run();
    }

    public static Maybe<String> skinLibraryUsername(UUID uuid) {
        if (uuid == null) return Maybe.none();
        return Maybe.ofNullable(skinLibraryUsernamesByUuid.get(uuid));
    }

    public record SkinProfile(
            String name,
            Maybe<UUID> uuid,
            Maybe<ResourceLocation> texture,
            Maybe<String> model,
            Maybe<ResourceLocation> cape,
            Maybe<ResourceLocation> elytra
    ) {
        private static SkinProfile from(SkinLibraryDefinition.SkinEntry entry) {
            return new SkinProfile(
                    entry.name().trim(),
                    OptionalOps.toMaybe(entry.uuid()),
                    OptionalOps.toMaybe(entry.texture()),
                    OptionalOps.toMaybe(entry.model()),
                    OptionalOps.toMaybe(entry.cape()),
                    OptionalOps.toMaybe(entry.elytra()));
        }

        public boolean slim() {
            return model()
                    .map(value -> value.equalsIgnoreCase("slim"))
                    .orElse(false);
        }
    }
}
