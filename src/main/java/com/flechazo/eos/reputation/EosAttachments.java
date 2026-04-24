package com.flechazo.eos.reputation;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.quest.PlayerQuestState;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class EosAttachments {
    private EosAttachments() {
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, EchoesofSurvival.MODID);

    public static final Supplier<AttachmentType<Integer>> PLAYER_REPUTATION = ATTACHMENTS.register(
            "player_reputation",
            () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .copyOnDeath()
                    .sync(ByteBufCodecs.VAR_INT)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerQuestState>> PLAYER_QUESTS = ATTACHMENTS.register(
            "player_quests",
            () -> AttachmentType.builder(PlayerQuestState::empty)
                    .serialize(PlayerQuestState.CODEC)
                    .copyOnDeath()
                    .build()
    );

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }
}
