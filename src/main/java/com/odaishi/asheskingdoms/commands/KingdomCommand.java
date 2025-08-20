package com.odaishi.asheskingdoms.commands;

import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager.Kingdom;
import com.odaishi.asheskingdoms.utils.InventoryCoins;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class KingdomCommand {

    private static final long KINGDOM_COST = 10000; // placeholder cost in bronze

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("kingdom")
                // Create kingdom (simple version)
                .then(literal("create")
                        .then(argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    PlayerEntity player = context.getSource().getPlayer();
                                    String name = StringArgumentType.getString(context, "name");

                                    // Use KingdomManager to create the kingdom (handles coins)
                                    boolean success = KingdomManager.createKingdom((ServerPlayerEntity) player, name, KINGDOM_COST);
                                    if (!success) {
                                        player.sendMessage(Text.of("Failed to create kingdom."), false);
                                        return 0;
                                    }
                                    return 1;
                                })
                        )
                )
                .then(literal("addmember")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("rank", StringArgumentType.word())
                                        .executes(context -> {
                                            PlayerEntity executor = context.getSource().getPlayer();
                                            String targetName = StringArgumentType.getString(context, "player");
                                            String rank = StringArgumentType.getString(context, "rank");

                                            PlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
                                            if (target == null) {
                                                executor.sendMessage(Text.of("Player not found."), false);
                                                return 0;
                                            }

                                            Kingdom kingdom = KingdomManager.getKingdomAt(new ChunkPos(executor.getBlockPos()));
                                            if (kingdom == null) {
                                                executor.sendMessage(Text.of("You are not in a kingdom!"), false);
                                                return 0;
                                            }

                                            boolean added = KingdomManager.addMember(kingdom, (ServerPlayerEntity) target, rank, (ServerPlayerEntity) executor);
                                            if (added) {
                                                executor.sendMessage(Text.of(target.getName().getString() + " added as " + rank + " to " + kingdom.name), false);
                                                return 1;
                                            } else {
                                                executor.sendMessage(Text.of("You do not have permission to add members."), false);
                                                return 0;
                                            }
                                        })
                                )
                        )
                )
                // Set rank
                .then(literal("setrank")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("rank", StringArgumentType.word())
                                        .executes(context -> {
                                            PlayerEntity executor = context.getSource().getPlayer();
                                            String targetName = StringArgumentType.getString(context, "player");
                                            String rank = StringArgumentType.getString(context, "rank");

                                            PlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
                                            if (target == null) {
                                                executor.sendMessage(Text.of("Player not found."), false);
                                                return 0;
                                            }

                                            Kingdom kingdom = KingdomManager.getKingdomAt(new ChunkPos(executor.getBlockPos()));
                                            if (kingdom == null) {
                                                executor.sendMessage(Text.of("You are not in a kingdom!"), false);
                                                return 0;
                                            }

                                            boolean assigned = KingdomManager.assignRank(kingdom, (ServerPlayerEntity) target, rank, (ServerPlayerEntity) executor);
                                            if (assigned) {
                                                executor.sendMessage(Text.of(target.getName().getString() + " is now " + rank + " in " + kingdom.name), false);
                                                return 1;
                                            } else {
                                                executor.sendMessage(Text.of("Only the Owner can assign ranks."), false);
                                                return 0;
                                            }
                                        })
                                )
                        )
                )
        );
    }
}
