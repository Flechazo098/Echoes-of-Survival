package com.flechazo.eos.data;

import cc.sighs.oelib.data.DataRegistry;
import com.flechazo.eos.config.EosConfigs;
import com.flechazo.eos.data.armor.ArmorSetDefinition;
import com.flechazo.eos.data.common.HealingPotionList;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.data.quest.QuestPoolDefinition;
import com.flechazo.eos.data.reputation.ReputationEventsDefinition;
import com.flechazo.eos.data.reputation.ReputationTiersDefinition;
import com.flechazo.eos.data.skin.SkinLibraryDefinition;
import com.flechazo.eos.data.trade.ProfessionDefinition;
import com.flechazo.eos.data.trade.TradePoolDefinition;
import com.mojang.serialization.Codec;

import java.util.List;

public final class EosDataTypes {
    private EosDataTypes() {
    }

    public static void register() {
        List<String> namespaces = EosConfigs.SURVIVOR.get().datapackNamespaces();

        registerType(ProfessionDefinition.class, ProfessionDefinition.CODEC, namespaces);
        registerType(TradePoolDefinition.class, TradePoolDefinition.CODEC, namespaces);
        registerType(QuestDefinition.class, QuestDefinition.CODEC, namespaces);
        registerType(QuestPoolDefinition.class, QuestPoolDefinition.CODEC, namespaces);
        registerType(ReputationTiersDefinition.class, ReputationTiersDefinition.CODEC, namespaces);
        registerType(ReputationEventsDefinition.class, ReputationEventsDefinition.CODEC, namespaces);
        registerType(ArmorSetDefinition.class, ArmorSetDefinition.CODEC, namespaces);
        registerType(SkinLibraryDefinition.class, SkinLibraryDefinition.CODEC, namespaces);
        registerType(HealingPotionList.class, HealingPotionList.CODEC, namespaces);
    }

    private static <T> void registerType(Class<T> dataClass, Codec<T> codec, List<String> namespaces) {
        if (namespaces == null || namespaces.isEmpty()) {
            DataRegistry.register(dataClass, codec);
            return;
        }
        DataRegistry.registerWithNamespaces(dataClass, codec, namespaces.toArray(String[]::new));
    }
}
