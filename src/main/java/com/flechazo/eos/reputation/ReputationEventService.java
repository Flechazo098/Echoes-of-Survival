package com.flechazo.eos.reputation;

import com.flechazo.eos.data.EosDatapackIndex;
import com.flechazo.eos.entity.AbstractSurvivorEntity;
import com.flechazo.eos.config.EosConfigs;
import com.flechazo.eos.profile.SurvivorProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * The only write gateway for global reputation, faction reputation, and
 * personal trust. Game features and compatibility layers should report an
 * event here instead of mutating player attachments directly.
 */
public final class ReputationEventService {
    public static final int MIN_REPUTATION = -1000;
    public static final int MAX_REPUTATION = 1000;
    public static final int MIN_TRUST = 0;
    public static final int MAX_TRUST = 100;

    private ReputationEventService() {
    }

    public static RelationshipState getState(ServerPlayer player) {
        RelationshipState state = player.getData(EosAttachments.PLAYER_RELATIONSHIPS.get());
        int legacyGlobal = player.getData(EosAttachments.PLAYER_REPUTATION.get());
        if (state.globalReputation() == 0 && legacyGlobal != 0) {
            state = state.withGlobalReputation(clampReputation(legacyGlobal));
            setState(player, state);
        }
        return state;
    }

    public static int global(ServerPlayer player) {
        return getState(player).globalReputation();
    }

    public static int faction(ServerPlayer player, ResourceLocation factionId) {
        Objects.requireNonNull(factionId, "factionId");
        return getState(player).factionReputation().getOrDefault(factionId, 0);
    }

    public static int trust(ServerPlayer player, UUID survivorUuid) {
        Objects.requireNonNull(survivorUuid, "survivorUuid");
        return getState(player).personalTrust().getOrDefault(survivorUuid, 0);
    }

    public static int trust(ServerPlayer player, AbstractSurvivorEntity survivor) {
        RelationshipState state = getState(player);
        Integer stored = state.personalTrust().get(survivor.getUUID());
        if (stored != null) return stored;
        var config = EosConfigs.TRAITS.get();
        int initial = config.baseInitialTrust() + switch (survivor.getTemperament()) {
            case FRIENDLY -> config.friendlyInitialTrust();
            case SUSPICIOUS -> config.suspiciousInitialTrust();
            default -> 0;
        };
        return Math.clamp(initial, MIN_TRUST, MAX_TRUST);
    }

    public static int ensureInitialTrust(ServerPlayer player, AbstractSurvivorEntity survivor) {
        RelationshipState state = getState(player);
        Integer stored = state.personalTrust().get(survivor.getUUID());
        if (stored != null) return stored;
        int initial = trust(player, survivor);
        setState(player, state.withPersonalTrust(survivor.getUUID(), initial));
        return initial;
    }

    public static void setGlobal(ServerPlayer player, int value, String reason) {
        setState(player, getState(player).withGlobalReputation(clampReputation(value)));
    }

    public static ChangeResult apply(
            ServerPlayer player,
            String eventId,
            int globalDelta,
            @Nullable ResourceLocation factionId,
            int factionDelta,
            @Nullable UUID survivorUuid,
            int trustDelta
    ) {
        Objects.requireNonNull(player, "player");
        RelationshipState before = getState(player);
        RelationshipState after = before;

        if (globalDelta != 0) {
            after = after.withGlobalReputation(clampReputation(before.globalReputation() + globalDelta));
        }
        if (factionId != null && factionDelta != 0) {
            int current = before.factionReputation().getOrDefault(factionId, 0);
            after = after.withFactionReputation(factionId, clampReputation(current + factionDelta));
        }
        if (survivorUuid != null && trustDelta != 0) {
            int current = before.personalTrust().getOrDefault(survivorUuid, 0);
            after = after.withPersonalTrust(survivorUuid, Math.clamp(current + trustDelta, MIN_TRUST, MAX_TRUST));
        }

        if (!after.equals(before)) setState(player, after);
        return new ChangeResult(eventId, before, after);
    }

    public static ChangeResult apply(ServerPlayer player, String eventId, @Nullable AbstractSurvivorEntity survivor) {
        int globalDelta = EosDatapackIndex.reputationEvent(eventId)
                .map(event -> event.change().sample(new java.util.Random(player.getRandom().nextLong())))
                .orElse(0);
        ResourceLocation factionId = survivor == null ? null : survivor.getAffiliationId();
        UUID survivorUuid = survivor == null ? null : survivor.getUUID();
        return apply(player, eventId, globalDelta, factionId, globalDelta, survivorUuid, 0);
    }

    private static void setState(ServerPlayer player, RelationshipState state) {
        player.setData(EosAttachments.PLAYER_RELATIONSHIPS.get(), state);
        // Mirror the legacy synchronized scalar until all client UI has migrated.
        player.setData(EosAttachments.PLAYER_REPUTATION.get(), state.globalReputation());
        player.syncData(EosAttachments.PLAYER_REPUTATION.get());
    }

    private static int clampReputation(int value) {
        return Math.clamp(value, MIN_REPUTATION, MAX_REPUTATION);
    }

    public record ChangeResult(String eventId, RelationshipState before, RelationshipState after) {
        public int globalChange() {
            return after.globalReputation() - before.globalReputation();
        }
    }
}
