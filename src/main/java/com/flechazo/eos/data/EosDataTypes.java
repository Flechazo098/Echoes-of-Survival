package com.flechazo.eos.data;

import cc.sighs.oelib.data.DataRegistry;
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
import com.mojang.serialization.Codec;

public final class EosDataTypes {
    private EosDataTypes() {
    }

    public static void register() {
        String[] namespaces = {EchoesofSurvival.MODID};

        DataRegistry.registerWithNamespaces(ProfessionDefinition.class, ProfessionDefinition.CODEC, namespaces);
        DataRegistry.registerWithNamespaces(TradePoolDefinition.class, TradePoolDefinition.CODEC, namespaces);
        DataRegistry.registerWithNamespaces(QuestDefinition.class, QuestDefinition.CODEC, namespaces);
        DataRegistry.registerWithNamespaces(QuestPoolDefinition.class, QuestPoolDefinition.CODEC, namespaces);
        DataRegistry.registerWithNamespaces(ReputationTiersDefinition.class, ReputationTiersDefinition.CODEC, namespaces);
        DataRegistry.registerWithNamespaces(ReputationEventsDefinition.class, ReputationEventsDefinition.CODEC, namespaces);
        DataRegistry.registerWithNamespaces(ArmorSetDefinition.class, ArmorSetDefinition.CODEC, namespaces);
        DataRegistry.registerWithNamespaces(SkinLibraryDefinition.class, SkinLibraryDefinition.CODEC, namespaces);
        DataRegistry.registerWithNamespaces(HealingPotionList.class, HealingPotionList.CODEC, namespaces);
    }
}
