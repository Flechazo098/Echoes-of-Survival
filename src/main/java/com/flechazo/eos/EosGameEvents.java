package com.flechazo.eos;


import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.quest.QuestApi;
import com.flechazo.eos.reputation.EosAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import javax.annotation.Nullable;

public final class EosGameEvents {
    private EosGameEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EosGameEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(EosGameEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EosGameEvents::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(EosGameEvents::onLivingIncomingDamage);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        EosCommands.register(event.getDispatcher());
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        QuestApi.onKill(player, event.getEntity());
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.syncData(EosAttachments.PLAYER_REPUTATION.get());
        }
    }

    private static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player hurtPlayer) {
            DamageSource source = event.getSource();
            FriendlySurvivorEntity survivor = findRecruitSurvivorSource(source);
            if (survivor != null && survivor.isRecruitOwner(hurtPlayer)) {
                event.setCanceled(true);
            }
        }
    }

    @Nullable
    private static FriendlySurvivorEntity findRecruitSurvivorSource(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity indirect = source.getEntity();

        if (indirect instanceof FriendlySurvivorEntity s) {
            return s;
        }
        if (direct instanceof Projectile proj && proj.getOwner() instanceof FriendlySurvivorEntity s) {
            return s;
        }
        return null;
    }
}

