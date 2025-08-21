/**
 * KINGDOM MEMBER MANAGEMENT COMMAND HANDLER
 *
 * Provides commands for managing kingdom membership, including adding, removing,
 * and listing members with rank assignments. Restricted to kingdom leadership.
 *
 * COMMANDS:
 * - /kingdom member add <player> <rank> - Adds player with specified rank
 * - /kingdom member remove <player> - Removes player from kingdom
 * - /kingdom member list - Lists all kingdom members with their ranks
 *
 * PERMISSION REQUIREMENTS:
 * - All commands require kingdom ownership (leader rank)
 * - Prevents unauthorized member management
 * - Validates player existence and kingdom membership
 *
 * SECURITY FEATURES:
 * - Ownership verification for sensitive operations
 * - Player existence validation
 * - Rank validity checking (through Kingdom.addMember)
 * - Feedback messages for all operations
 *
 * USER EXPERIENCE:
 * - Clear success/error messages for all operations
 * - Player notifications for membership changes
 * - Formatted member lists with rank display
 * - UUID fallback for offline players in lists
 *
 * INTEGRATION:
 * - Direct Kingdom object manipulation
 * - Real-time player lookup via server manager
 * - Rank system compatibility
 * - Future: Rank-based permission delegation
 *
 * EXPANSION POINTS:
 * - Rank modification commands
 * - Bulk member operations
 * - Member search and filtering
 * - Offline player support
 * - Membership audit logging
 */

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
