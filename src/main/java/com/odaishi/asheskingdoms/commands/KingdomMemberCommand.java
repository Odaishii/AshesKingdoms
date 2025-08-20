package com.odaishi.asheskingdoms.commands;

import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class KingdomMemberCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("kingdom")
                .then(literal("member")
                        .then(literal("add")
                                .then(argument("player", StringArgumentType.word())
                                        .then(argument("rank", StringArgumentType.word())
                                                .executes(context -> {
                                                    PlayerEntity executor = context.getSource().getPlayer();
                                                    String targetName = StringArgumentType.getString(context, "player");
                                                    String rank = StringArgumentType.getString(context, "rank");

                                                    Kingdom kingdom = getPlayerKingdom(executor);
                                                    if (kingdom == null) {
                                                        executor.sendMessage(Text.of("You are not in a kingdom."), false);
                                                        return 0;
                                                    }

                                                    if (!executor.getUuid().equals(kingdom.getOwner())) {
                                                        executor.sendMessage(Text.of("Only the kingdom owner can add members."), false);
                                                        return 0;
                                                    }

                                                    PlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
                                                    if (target == null) {
                                                        executor.sendMessage(Text.of("Player not found."), false);
                                                        return 0;
                                                    }

                                                    kingdom.addMember(target.getUuid(), rank);
                                                    executor.sendMessage(Text.of(target.getName().getString() + " has been added as " + rank), false);
                                                    target.sendMessage(Text.of("You have been added to the kingdom " + kingdom.getName() + " as " + rank), false);

                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(literal("remove")
                                .then(argument("player", StringArgumentType.word())
                                        .executes(context -> {
                                            PlayerEntity executor = context.getSource().getPlayer();
                                            String targetName = StringArgumentType.getString(context, "player");

                                            Kingdom kingdom = getPlayerKingdom(executor);
                                            if (kingdom == null) {
                                                executor.sendMessage(Text.of("You are not in a kingdom."), false);
                                                return 0;
                                            }

                                            if (!executor.getUuid().equals(kingdom.getOwner())) {
                                                executor.sendMessage(Text.of("Only the kingdom owner can remove members."), false);
                                                return 0;
                                            }

                                            PlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
                                            if (target == null) {
                                                executor.sendMessage(Text.of("Player not found."), false);
                                                return 0;
                                            }

                                            kingdom.removeMember(target.getUuid());
                                            executor.sendMessage(Text.of(target.getName().getString() + " has been removed from the kingdom."), false);
                                            target.sendMessage(Text.of("You have been removed from the kingdom " + kingdom.getName()), false);

                                            return 1;
                                        })
                                )
                        )
                        .then(literal("list")
                                .executes(context -> {
                                    PlayerEntity executor = context.getSource().getPlayer();
                                    Kingdom kingdom = getPlayerKingdom(executor);
                                    if (kingdom == null) {
                                        executor.sendMessage(Text.of("You are not in a kingdom."), false);
                                        return 0;
                                    }

                                    StringBuilder sb = new StringBuilder("Members of " + kingdom.getName() + ": ");
                                    kingdom.getMembers().forEach((uuid, rank) -> {
                                        PlayerEntity player = executor.getServer().getPlayerManager().getPlayer(uuid);
                                        String name = (player != null) ? player.getName().getString() : uuid.toString();
                                        sb.append(name).append(" (").append(rank).append("), ");
                                    });

                                    executor.sendMessage(Text.of(sb.toString()), false);
                                    return 1;
                                })
                        )
                )
        );
    }

    private static Kingdom getPlayerKingdom(PlayerEntity player) {
        // Use the kingdoms collection directly from KingdomManager
        return KingdomManager.kingdoms.values().stream()
                .filter(k -> k.isMember(player.getUuid()))
                .findFirst()
                .orElse(null);
    }
}
