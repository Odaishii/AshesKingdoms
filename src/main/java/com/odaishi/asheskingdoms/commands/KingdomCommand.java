/**
 * CORE KINGDOM MANAGEMENT COMMAND HANDLER
 *
 * Primary command interface for kingdom creation, management, and basic operations.
 * Provides the main user interface for players to interact with the kingdom system.
 *
 * COMMAND CATEGORIES:
 * - Kingdom Creation: /kingdom create <name> (with economy cost)
 * - Membership Management: leave, invite, accept, decline invitations
 * - Kingdom Operations: delete (with confirmation), info, list kingdoms
 * - Rank Management: setrank with tab completion
 * - Administrative: Owner-only deletion and rank assignments
 *
 * ECONOMY INTEGRATION:
 * - Kingdom creation costs (configurable bronze amount)
 * - Integrated with Numismatic Overhaul currency system
 * - Automatic payment processing and validation
 *
 * SECURITY FEATURES:
 * - Ownership verification for sensitive operations
 * - Deletion confirmation system to prevent accidents
 * - Permission-based command execution
 * - Player validation and existence checks
 *
 * USER EXPERIENCE:
 * - Tab completion for players and ranks
 * - Color-coded feedback messages
 * - Comprehensive error handling
 * - Invitation system with expiration
 * - Clear success/failure notifications
 *
 * INTEGRATION POINTS:
 * - KingdomManager for core operations
 * - Economy system for transaction processing
 * - Invitation system with pending invites
 * - Rank hierarchy and permission system
 *
 * EXPANSION READY:
 * - Modular command structure for easy additions
 * - Tab completion framework for new arguments
 * - Permission system ready for new commands
 * - Economy hooks for future paid features
 */

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

                // DELETE command (owner only) - now requires confirmation
                .then(literal("delete")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            boolean success = KingdomManager.deleteKingdom(player);
                            return success ? 1 : 0;
                        })
                )

                // CONFIRM DELETE command
                .then(literal("confirm")
                        .then(literal("delete")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;

                                    boolean success = KingdomManager.confirmDeleteKingdom(player);
                                    return success ? 1 : 0;
                                })
                        )
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

                // INVITE command (replaces addmember)

// INVITE command with tab completion
                        .then(literal("invite")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            // Tab complete online players NOT in the kingdom
                                            ServerPlayerEntity executor = context.getSource().getPlayer();
                                            if (executor != null) {
                                                Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
                                                if (kingdom != null) {
                                                    for (ServerPlayerEntity player : executor.getServer().getPlayerManager().getPlayerList()) {
                                                        if (!kingdom.isMember(player) && !player.getUuid().equals(executor.getUuid())) {
                                                            builder.suggest(player.getName().getString());
                                                        }
                                                    }
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            ServerPlayerEntity executor = context.getSource().getPlayer();
                                            String targetName = StringArgumentType.getString(context, "player");

                                            ServerPlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
                                            if (target == null) {
                                                executor.sendMessage(Text.of("§cPlayer not found."), false);
                                                return 0;
                                            }

                                            Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
                                            if (kingdom == null) {
                                                executor.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
                                                return 0;
                                            }

                                            boolean invited = KingdomManager.invitePlayer(kingdom, target, executor);
                                            return invited ? 1 : 0;
                                        })
                                )
                        )

                // ACCEPT invitation command
                .then(literal("accept")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            boolean accepted = KingdomManager.acceptInvite(player);
                            return accepted ? 1 : 0;
                        })
                )

                // DECLINE invitation command
                .then(literal("decline")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            boolean declined = KingdomManager.declineInvite(player);
                            return declined ? 1 : 0;
                        })
                )

                // Set rank
// Set rank command with tab completion
                        .then(literal("setrank")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            // Tab complete online players
                                            ServerPlayerEntity executor = context.getSource().getPlayer();
                                            if (executor != null) {
                                                Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
                                                if (kingdom != null) {
                                                    for (ServerPlayerEntity player : executor.getServer().getPlayerManager().getPlayerList()) {
                                                        if (kingdom.isMember(player)) {
                                                            builder.suggest(player.getName().getString());
                                                        }
                                                    }
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(argument("rank", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    // Tab complete available ranks
                                                    builder.suggest("leader");
                                                    builder.suggest("assistant");
                                                    builder.suggest("officer");
                                                    builder.suggest("member");
                                                    builder.suggest("ally");
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    ServerPlayerEntity executor = context.getSource().getPlayer();
                                                    String targetName = StringArgumentType.getString(context, "player");
                                                    String rank = StringArgumentType.getString(context, "rank");

                                                    ServerPlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
                                                    if (target == null) {
                                                        executor.sendMessage(Text.of("§cPlayer not found."), false);
                                                        return 0;
                                                    }

                                                    Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
                                                    if (kingdom == null) {
                                                        executor.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
                                                        return 0;
                                                    }

                                                    boolean assigned = KingdomManager.assignRank(kingdom, target, rank, executor);
                                                    if (assigned) {
                                                        executor.sendMessage(Text.of("§a" + target.getName().getString() + " is now " + rank + " in " + kingdom.getName()), false);
                                                        return 1;
                                                    } else {
                                                        return 0;
                                                    }
                                                })
                                        )
                                )
                        )
        );
    }
}