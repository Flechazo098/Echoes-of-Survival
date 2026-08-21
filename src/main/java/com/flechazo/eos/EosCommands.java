package com.flechazo.eos;

import cc.sighs.oelib.data.DataManager;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.data.trade.ProfessionDefinition;
import com.flechazo.eos.entity.EosEntityTypes;
import com.flechazo.eos.entity.FriendlySurvivorEntity;
import com.flechazo.eos.quest.QuestApi;
import com.flechazo.eos.reputation.ReputationApi;
import com.flechazo.eos.reputation.ReputationEventService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Objects;

public final class EosCommands {
    private EosCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("eos")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("rep")
                                .then(Commands.literal("get")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            int rep = ReputationApi.get(player);
                                            ctx.getSource().sendSuccess(() -> Component.literal("reputation=" + rep), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    int value = IntegerArgumentType.getInteger(ctx, "value");
                                                    ReputationEventService.setGlobal(player, value, "admin_command_set");
                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            "reputation=" + ReputationEventService.global(player)), false);
                                                    return 1;
                                                })))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    int delta = IntegerArgumentType.getInteger(ctx, "delta");
                                                    ReputationEventService.apply(player, "admin_command_add", delta,
                                                            null, 0, null, 0);
                                                    int rep = ReputationApi.get(player);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("reputation=" + rep), false);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("quest")
                                .then(Commands.literal("list")
                                        .executes(ctx -> {
                                            var list = DataManager.getDataList(QuestDefinition.class).stream()
                                                    .sorted(Comparator.comparing(q -> q.questId().toString()))
                                                    .toList();
                                            ctx.getSource().sendSuccess(() -> Component.literal("quests=" + list.size()), false);
                                            for (QuestDefinition q : list) {
                                                ctx.getSource().sendSuccess(() -> Component.literal("- " + q.questId()), false);
                                            }
                                            return list.size();
                                        }))
                                .then(Commands.literal("accept")
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                                    boolean ok = QuestApi.accept(player, id);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal(ok ? "accepted " + id : "failed to accept " + id),
                                                            false
                                                    );
                                                    return ok ? 1 : 0;
                                                })))
                                .then(Commands.literal("submit")
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                                    boolean ok = QuestApi.submitItems(player, id);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal(ok ? "submitted " + id : "failed to submit " + id),
                                                            false
                                                    );
                                                    return ok ? 1 : 0;
                                                })))
                                .then(Commands.literal("claim")
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                                    boolean ok = QuestApi.claim(player, id);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal(ok ? "claimed " + id : "failed to claim " + id),
                                                            false
                                                    );
                                                    return ok ? 1 : 0;
                                                }))))
                        .then(Commands.literal("survivor")
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("profession", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        DataManager.getDataList(ProfessionDefinition.class).stream()
                                                                .map(ProfessionDefinition::id)
                                                                .filter(Objects::nonNull),
                                                        builder
                                                ))
                                                .executes(ctx -> spawnFriendlySurvivor(
                                                        ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "profession"),
                                                        ctx.getSource().getPosition()
                                                ))
                                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                                        .executes(ctx -> spawnFriendlySurvivor(
                                                                ctx.getSource(),
                                                                ResourceLocationArgument.getId(ctx, "profession"),
                                                                Vec3Argument.getVec3(ctx, "pos")
                                                        ))))))
        );
    }

    private static int spawnFriendlySurvivor(CommandSourceStack source, ResourceLocation professionId, Vec3 pos) {
        ProfessionDefinition profession = DataManager.getDataList(ProfessionDefinition.class).stream()
                .filter(def -> def != null && professionId.equals(def.id()))
                .findFirst()
                .orElse(null);
        if (profession == null) {
            source.sendFailure(Component.literal("Unknown survivor profession: " + professionId));
            return 0;
        }

        BlockPos blockPos = BlockPos.containing(pos);
        if (!Level.isInSpawnableBounds(blockPos)) {
            source.sendFailure(Component.translatable("commands.summon.invalidPosition"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        FriendlySurvivorEntity survivor = EosEntityTypes.FRIENDLY_SURVIVOR.get().create(level);
        if (survivor == null) {
            source.sendFailure(Component.literal("Failed to create friendly survivor"));
            return 0;
        }

        survivor.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
        survivor.setProfessionId(professionId);
        survivor.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(blockPos),
                MobSpawnType.COMMAND,
                null
        );
        if (!level.addFreshEntity(survivor)) {
            source.sendFailure(Component.literal("Failed to add friendly survivor to the world"));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Spawned friendly survivor with profession " + professionId),
                true
        );
        return 1;
    }
}
