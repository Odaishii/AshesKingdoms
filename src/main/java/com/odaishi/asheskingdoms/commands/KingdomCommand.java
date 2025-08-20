package com.odaishi.asheskingdoms.commands;

import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class KingdomCommand {

    private static final long KINGDOM_COST = 10000; // placeholder cost in bronze

    // Helper method to get player name from UUID
    private static String getPlayerName(UUID playerId, MinecraftServer server) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        return player != null ? player.getName().getString() : "Unknown";
    }

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

                // LEAVE command
                .then(literal("leave")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            boolean success = KingdomManager.leaveKingdom(player);
                            return success ? 1 : 0;
                        })
                )

                // DELETE command (owner only)
                .then(literal("delete")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            boolean success = KingdomManager.deleteKingdom(player);
                            return success ? 1 : 0;
                        })
                )

                // LIST command
                .then(literal("list")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            KingdomManager.listKingdoms(player);
                            return 1;
                        })
                )

                // INFO command (view your kingdom info)
                .then(literal("info")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
                            if (kingdom == null) {
                                player.sendMessage(Text.of("§cYou are not in any kingdom!"), false);
                                return 0;
                            }

                            player.sendMessage(Text.of("§6=== " + kingdom.getName() + " Info ==="), false);
                            player.sendMessage(Text.of("§bOwner: §a" + getPlayerName(kingdom.getOwner(), player.getServer())), false);
                            player.sendMessage(Text.of("§bMembers: §e" + kingdom.getMembers().size()), false);
                            player.sendMessage(Text.of("§bClaims: §6" + kingdom.getClaimedChunks().size()), false);
                            player.sendMessage(Text.of("§bYour Rank: §d" + kingdom.getRank(player)), false);

                            return 1;
                        })
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
                                                executor.sendMessage(Text.of(target.getName().getString() + " added as " + rank + " to " + kingdom.getName()), false);
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
                                                executor.sendMessage(Text.of(target.getName().getString() + " is now " + rank + " in " + kingdom.getName()), false);
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