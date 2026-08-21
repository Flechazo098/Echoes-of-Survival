package com.flechazo.eos.config;

import cc.sighs.oelib.config.ConfigManager;
import cc.sighs.oelib.config.ConfigRecordCodecBuilder;
import cc.sighs.oelib.config.ConfigUnit;
import cc.sighs.oelib.config.field.ConfigField;
import cc.sighs.oelib.config.model.ConfigStorageFormat;
import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.resources.ResourceLocation;

public final class EosConfigs {
    private EosConfigs() {
    }

    public static final ConfigUnit<SurvivorConfig> SURVIVOR = ConfigRecordCodecBuilder.create(
            ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "survivor"),
            instance -> instance.group(
                    ConfigField.bool("enableSurvivors")
                            .comment("Enable the survivor system, including entities, interaction, and quests.")
                            .defaultValue(true)
                            .forGetter(SurvivorConfig::enableSurvivors),
                    ConfigField.intRange("globalRecruitThreshold", -1000, 1000)
                            .comment("Minimum global reputation required to recruit a normal survivor.")
                            .defaultValue(101)
                            .forGetter(SurvivorConfig::globalRecruitThreshold),
                    ConfigField.intRange("factionRecruitThreshold", -1000, 1000)
                            .comment("Minimum reputation with the survivor's faction required for recruitment.")
                            .defaultValue(0)
                            .forGetter(SurvivorConfig::factionRecruitThreshold),
                    ConfigField.intRange("personalTrustRecruitThreshold", 0, 100)
                            .comment("Minimum personal trust required to recruit a normal survivor.")
                            .defaultValue(70)
                            .forGetter(SurvivorConfig::personalTrustRecruitThreshold),
                    ConfigField.intRange("maxFollowingSurvivors", 0, 16)
                            .comment("Public base squad-slot limit for survivors currently following a player.")
                            .defaultValue(2)
                            .forGetter(SurvivorConfig::maxFollowingSurvivors),
                    ConfigField.bool("allowBiomeSpawnSupplement")
                            .comment("Allow low-density biome spawns as a supplement to structures and encounters.")
                            .defaultValue(true)
                            .forGetter(SurvivorConfig::allowBiomeSpawnSupplement),
                    ConfigField.intRange("encounterRegionSizeChunks", 1, 64)
                            .comment("Square region width used for survivor encounter density limits.")
                            .defaultValue(16)
                            .forGetter(SurvivorConfig::encounterRegionSizeChunks),
                    ConfigField.intRange("encounterCooldownTicks", 0, 6048000)
                            .comment("Minimum delay between natural survivor encounters in one region.")
                            .defaultValue(24000)
                            .forGetter(SurvivorConfig::encounterCooldownTicks),
                    ConfigField.intRange("maxActiveSurvivorsPerRegion", 0, 64)
                            .comment("Maximum loaded survivors allowed in one encounter region.")
                            .defaultValue(4)
                            .forGetter(SurvivorConfig::maxActiveSurvivorsPerRegion)
            ).apply(instance, SurvivorConfig::new),
            meta -> meta
                    .directory(EchoesofSurvival.MODID)
                    .fileName("survivor")
                    .format(ConfigStorageFormat.TOML)
    );

    public static final ConfigUnit<TraitConfig> TRAITS = ConfigRecordCodecBuilder.create(
            ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "survivor_traits"),
            instance -> instance.group(
                    ConfigField.intRange("baseInitialTrust", 0, 100).defaultValue(10)
                            .forGetter(TraitConfig::baseInitialTrust),
                    ConfigField.intRange("friendlyInitialTrust", -100, 100).defaultValue(5)
                            .forGetter(TraitConfig::friendlyInitialTrust),
                    ConfigField.intRange("suspiciousInitialTrust", -100, 100).defaultValue(-5)
                            .forGetter(TraitConfig::suspiciousInitialTrust),
                    ConfigField.doubleRange("cautiousRetreatThresholdBonus", 0.0, 1.0).defaultValue(0.10)
                            .forGetter(TraitConfig::cautiousRetreatThresholdBonus),
                    ConfigField.doubleRange("timidRetreatThresholdBonus", 0.0, 1.0).defaultValue(0.20)
                            .forGetter(TraitConfig::timidRetreatThresholdBonus),
                    ConfigField.doubleRange("braveRetreatThresholdReduction", 0.0, 1.0).defaultValue(0.10)
                            .forGetter(TraitConfig::braveRetreatThresholdReduction),
                    ConfigField.doubleRange("aggressiveAttackDamageBonus", 0.0, 0.25).defaultValue(0.05)
                            .forGetter(TraitConfig::aggressiveAttackDamageBonus),
                    ConfigField.doubleRange("braveKnockbackResistanceBonus", 0.0, 0.25).defaultValue(0.05)
                            .forGetter(TraitConfig::braveKnockbackResistanceBonus),
                    ConfigField.doubleRange("toughMaxHealthBonus", 0.0, 4.0).defaultValue(4.0)
                            .forGetter(TraitConfig::toughMaxHealthBonus),
                    ConfigField.doubleRange("agileMovementSpeedBonus", 0.0, 0.10).defaultValue(0.05)
                            .forGetter(TraitConfig::agileMovementSpeedBonus),
                    ConfigField.doubleRange("veteranKnockbackResistanceBonus", 0.0, 0.25).defaultValue(0.10)
                            .forGetter(TraitConfig::veteranKnockbackResistanceBonus),
                    ConfigField.doubleRange("steadyHandRangeBonus", 0.0, 8.0).defaultValue(2.0)
                            .forGetter(TraitConfig::steadyHandRangeBonus),
                    ConfigField.doubleRange("firstAidHealingBonus", 0.0, 1.0).defaultValue(0.20)
                            .forGetter(TraitConfig::firstAidHealingBonus),
                    ConfigField.doubleRange("firstAidCooldownReduction", 0.0, 0.75).defaultValue(0.20)
                            .forGetter(TraitConfig::firstAidCooldownReduction)
            ).apply(instance, TraitConfig::new),
            meta -> meta.directory(EchoesofSurvival.MODID).fileName("survivor_traits").format(ConfigStorageFormat.TOML)
    );

    public static final ConfigUnit<EconomyConfig> ECONOMY = ConfigRecordCodecBuilder.create(
            ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "survivor_economy"),
            instance -> instance.group(
                    ConfigField.intRange("restockIntervalDays", 1, 30).defaultValue(1)
                            .forGetter(EconomyConfig::restockIntervalDays),
                    ConfigField.intRange("dailyProcurementBudget", 0, 100000).defaultValue(64)
                            .forGetter(EconomyConfig::dailyProcurementBudget),
                    ConfigField.intRange("dailyTradeTrustGain", 0, 10).defaultValue(1)
                            .forGetter(EconomyConfig::dailyTradeTrustGain)
            ).apply(instance, EconomyConfig::new),
            meta -> meta.directory(EchoesofSurvival.MODID).fileName("survivor_economy").format(ConfigStorageFormat.TOML)
    );

    public static void register() {
        ConfigManager.registerServer(SURVIVOR, player -> player.hasPermissions(2));
        ConfigManager.registerServer(TRAITS, player -> player.hasPermissions(2));
        ConfigManager.registerServer(ECONOMY, player -> player.hasPermissions(2));
        SURVIVOR.applyAutoMigrationOnRegister();
        TRAITS.applyAutoMigrationOnRegister();
        ECONOMY.applyAutoMigrationOnRegister();
    }

    public record SurvivorConfig(
            boolean enableSurvivors,
            int globalRecruitThreshold,
            int factionRecruitThreshold,
            int personalTrustRecruitThreshold,
            int maxFollowingSurvivors,
            boolean allowBiomeSpawnSupplement,
            int encounterRegionSizeChunks,
            int encounterCooldownTicks,
            int maxActiveSurvivorsPerRegion
    ) {
    }

    public record TraitConfig(
            int baseInitialTrust,
            int friendlyInitialTrust,
            int suspiciousInitialTrust,
            double cautiousRetreatThresholdBonus,
            double timidRetreatThresholdBonus,
            double braveRetreatThresholdReduction,
            double aggressiveAttackDamageBonus,
            double braveKnockbackResistanceBonus,
            double toughMaxHealthBonus,
            double agileMovementSpeedBonus,
            double veteranKnockbackResistanceBonus,
            double steadyHandRangeBonus,
            double firstAidHealingBonus,
            double firstAidCooldownReduction
    ) {
    }

    public record EconomyConfig(int restockIntervalDays, int dailyProcurementBudget, int dailyTradeTrustGain) {
    }
}
