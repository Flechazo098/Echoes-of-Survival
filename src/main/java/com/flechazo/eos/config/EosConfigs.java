package com.flechazo.eos.config;

import cc.sighs.oelib.config.ConfigManager;
import cc.sighs.oelib.config.ConfigRecordCodecBuilder;
import cc.sighs.oelib.config.ConfigUnit;
import cc.sighs.oelib.config.field.ConfigField;
import cc.sighs.oelib.config.model.ConfigStorageFormat;
import com.flechazo.eos.EchoesofSurvival;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

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
                    ConfigField.list("datapackNamespaces", com.mojang.serialization.Codec.STRING)
                            .comment("Namespaces allowed for survivor datapack data. Defaults to echoes and echoes_of_survival.")
                            .defaultValue(List.of(EchoesofSurvival.DATAPACK_NAMESPACE, EchoesofSurvival.MODID))
                            .forGetter(SurvivorConfig::datapackNamespaces)
            ).apply(instance, SurvivorConfig::new),
            meta -> meta
                    .directory(EchoesofSurvival.MODID)
                    .fileName("survivor")
                    .format(ConfigStorageFormat.TOML)
    );

    public static void register() {
        ConfigManager.registerServer(SURVIVOR, player -> player.hasPermissions(2));
        SURVIVOR.applyAutoMigrationOnRegister();
    }

    public record SurvivorConfig(
            boolean enableSurvivors,
            List<String> datapackNamespaces
    ) {
    }
}
