package com.flechazo.eos;

import cc.sighs.oelib.data.DataManager;
import com.flechazo.eos.data.quest.QuestDefinition;
import com.flechazo.eos.quest.QuestApi;
import com.flechazo.eos.reputation.ReputationApi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;

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
                                                    ReputationApi.set(player, value);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("reputation=" + value), false);
                                                    return 1;
                                                })))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    int delta = IntegerArgumentType.getInteger(ctx, "delta");
                                                    ReputationApi.add(player, delta);
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
        );
    }
}