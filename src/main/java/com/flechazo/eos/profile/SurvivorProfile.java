package com.flechazo.eos.profile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Immutable, permanent identity and classification of one survivor entity. */
public record SurvivorProfile(
        UUID identityUuid,
        String name,
        @Nullable UUID skinUuid,
        String skinName,
        ResourceLocation affiliationId,
        @Nullable ResourceLocation professionId,
        CombatArchetype combatArchetype,
        Temperament temperament,
        List<Specialty> specialties,
        SpawnOrigin spawnOrigin,
        long creationTime,
        @Nullable ResourceLocation homeDimension,
        @Nullable BlockPos homePos,
        @Nullable ResourceLocation homeStructure
) {
    public static final String NBT_KEY = "EosSurvivorProfile";
    public static final int DATA_VERSION = 1;

    public SurvivorProfile {
        name = name == null || name.isBlank() ? "Survivor" : name.trim();
        skinName = skinName == null ? "" : skinName.trim();
        specialties = List.copyOf(new LinkedHashSet<>(specialties).stream().limit(2).toList());
    }

    public static SurvivorProfile create(
            UUID entityUuid,
            @Nullable UUID skinUuid,
            String skinName,
            ResourceLocation affiliationId,
            @Nullable ResourceLocation professionId,
            MobSpawnType spawnType,
            long creationTime,
            @Nullable ResourceLocation dimension,
            BlockPos spawnPos
    ) {
        int seed = entityUuid.hashCode();
        CombatArchetype archetype = CombatArchetype.values()[Math.floorMod(seed >>> 3, CombatArchetype.values().length)];
        Temperament temperament = Temperament.values()[Math.floorMod(seed >>> 7, Temperament.values().length)];
        List<Specialty> specialties = pickSpecialties(seed, professionId);
        SpawnOrigin origin = SpawnOrigin.from(spawnType);
        BlockPos homePos = origin.keepsHome() ? spawnPos.immutable() : null;

        String permanentName = skinName == null || skinName.isBlank()
                ? "Survivor-" + entityUuid.toString().substring(0, 8)
                : skinName.trim();
        return new SurvivorProfile(
                entityUuid,
                permanentName,
                skinUuid,
                skinName,
                affiliationId,
                professionId,
                archetype,
                temperament,
                specialties,
                origin,
                creationTime,
                homePos == null ? null : dimension,
                homePos,
                null
        );
    }

    private static List<Specialty> pickSpecialties(int seed, @Nullable ResourceLocation professionId) {
        if (Math.floorMod(seed, 100) >= 65) return List.of();
        List<Specialty> pool = new ArrayList<>(List.of(Specialty.TOUGH, Specialty.AGILE,
                Specialty.STEADY_HAND, Specialty.VETERAN, Specialty.SURVIVALIST));
        String profession = professionId == null ? "" : professionId.getPath();
        if (profession.contains("medic")) pool.addFirst(Specialty.FIRST_AID);
        if (profession.contains("mechanic")) pool.addFirst(Specialty.MECHANICAL_SKILL);
        if (profession.contains("scavenger")) pool.addFirst(Specialty.SCAVENGING_SKILL);
        if (profession.contains("quartermaster") || profession.contains("trader")) pool.addFirst(Specialty.TRADER);

        Specialty first = pool.get(Math.floorMod(seed >>> 11, pool.size()));
        if (Math.floorMod(seed >>> 17, 100) >= 5 || pool.size() < 2) return List.of(first);
        Specialty second = pool.get(Math.floorMod(seed >>> 23, pool.size()));
        return first == second ? List.of(first) : List.of(first, second);
    }

    public SurvivorProfile withSkin(@Nullable UUID uuid, String skinName) {
        return new SurvivorProfile(identityUuid, name, uuid, skinName, affiliationId, professionId,
                combatArchetype, temperament, specialties, spawnOrigin, creationTime,
                homeDimension, homePos, homeStructure);
    }

    public SurvivorProfile withProfession(@Nullable ResourceLocation professionId) {
        return new SurvivorProfile(identityUuid, name, skinUuid, skinName, affiliationId, professionId,
                combatArchetype, temperament, specialties, spawnOrigin, creationTime,
                homeDimension, homePos, homeStructure);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", DATA_VERSION);
        tag.putUUID("identity_uuid", identityUuid);
        tag.putString("name", name);
        if (skinUuid != null) tag.putUUID("skin_uuid", skinUuid);
        if (!skinName.isBlank()) tag.putString("skin_name", skinName);
        tag.putString("affiliation", affiliationId.toString());
        if (professionId != null) tag.putString("profession", professionId.toString());
        tag.putString("combat_archetype", combatArchetype.serializedName());
        tag.putString("temperament", temperament.serializedName());
        ListTag specialtiesTag = new ListTag();
        specialties.forEach(value -> specialtiesTag.add(StringTag.valueOf(value.serializedName())));
        tag.put("specialties", specialtiesTag);
        tag.putString("spawn_origin", spawnOrigin.serializedName());
        tag.putLong("creation_time", creationTime);
        if (homeDimension != null) tag.putString("home_dimension", homeDimension.toString());
        if (homePos != null) {
            tag.putInt("home_x", homePos.getX());
            tag.putInt("home_y", homePos.getY());
            tag.putInt("home_z", homePos.getZ());
        }
        if (homeStructure != null) tag.putString("home_structure", homeStructure.toString());
        return tag;
    }

    public static Optional<SurvivorProfile> load(CompoundTag parent) {
        if (!parent.contains(NBT_KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag tag = parent.getCompound(NBT_KEY);
        if (!tag.hasUUID("identity_uuid")) return Optional.empty();
        ResourceLocation affiliation = ResourceLocation.tryParse(tag.getString("affiliation"));
        if (affiliation == null) affiliation = SurvivorAffiliations.INDEPENDENT;
        ResourceLocation profession = parseId(tag.getString("profession"));
        ResourceLocation homeDimension = parseId(tag.getString("home_dimension"));
        ResourceLocation homeStructure = parseId(tag.getString("home_structure"));
        BlockPos homePos = tag.contains("home_x", Tag.TAG_INT)
                ? new BlockPos(tag.getInt("home_x"), tag.getInt("home_y"), tag.getInt("home_z"))
                : null;
        List<Specialty> specialties = new ArrayList<>();
        ListTag specialtiesTag = tag.getList("specialties", Tag.TAG_STRING);
        for (int i = 0; i < specialtiesTag.size(); i++) {
            Specialty.parse(specialtiesTag.getString(i)).ifPresent(specialties::add);
        }
        return Optional.of(new SurvivorProfile(
                tag.getUUID("identity_uuid"),
                tag.getString("name"),
                tag.hasUUID("skin_uuid") ? tag.getUUID("skin_uuid") : null,
                tag.getString("skin_name"),
                affiliation,
                profession,
                CombatArchetype.parse(tag.getString("combat_archetype")).orElse(CombatArchetype.CIVILIAN),
                Temperament.parse(tag.getString("temperament")).orElse(Temperament.CAUTIOUS),
                specialties,
                SpawnOrigin.parse(tag.getString("spawn_origin")).orElse(SpawnOrigin.LEGACY),
                tag.getLong("creation_time"),
                homeDimension,
                homePos,
                homeStructure
        ));
    }

    @Nullable
    private static ResourceLocation parseId(String value) {
        return value == null || value.isBlank() ? null : ResourceLocation.tryParse(value);
    }

    public enum CombatArchetype {
        CIVILIAN, RIFLEMAN, MARKSMAN, BREACHER;

        public String serializedName() { return name().toLowerCase(Locale.ROOT); }
        public static Optional<CombatArchetype> parse(String value) { return parseEnum(CombatArchetype.class, value); }
    }

    public enum Temperament {
        CAUTIOUS, BRAVE, TIMID, AGGRESSIVE, FRIENDLY, SUSPICIOUS;

        public String serializedName() { return name().toLowerCase(Locale.ROOT); }
        public static Optional<Temperament> parse(String value) { return parseEnum(Temperament.class, value); }
    }

    public enum Specialty {
        FIRST_AID, TOUGH, AGILE, STEADY_HAND, VETERAN, MECHANICAL_SKILL,
        SCAVENGING_SKILL, TRADER, SURVIVALIST;

        public String serializedName() { return name().toLowerCase(Locale.ROOT); }
        public static Optional<Specialty> parse(String value) { return parseEnum(Specialty.class, value); }
    }

    public enum SpawnOrigin {
        WILDERNESS, STRUCTURE, EVENT, CAMP, COMMAND, LEGACY;

        public String serializedName() { return name().toLowerCase(Locale.ROOT); }
        public boolean keepsHome() { return this == STRUCTURE || this == CAMP; }

        public static SpawnOrigin from(MobSpawnType type) {
            if (type == null) return LEGACY;
            return switch (type) {
                case STRUCTURE -> STRUCTURE;
                case COMMAND, SPAWN_EGG -> COMMAND;
                case EVENT, TRIGGERED -> EVENT;
                default -> WILDERNESS;
            };
        }

        public static Optional<SpawnOrigin> parse(String value) { return parseEnum(SpawnOrigin.class, value); }
    }

    private static <E extends Enum<E>> Optional<E> parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
