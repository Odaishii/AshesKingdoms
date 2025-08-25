/**
 * PERSONAL CLAIM COMMAND HANDLER
 *
 * Handles all commands related to personal claims within kingdom territory.
 * Allows players to claim, unclaim, and manage their personal chunks.
 *
 * COMMANDS:
 * - /kingdom claim personal - Claim current chunk personally
 * - /kingdom claim personal <player> - Assign claim to another player (owner/assistant only)
 * - /kingdom claim list - List your personal claims
 * - /kingdom claim unclaim - Unclaim current personal claim
 * - /kingdom claim unclaim <player> - Remove someone else's claim (owner/assistant only)
 * - /kingdom claim transfer <from> <to> - Transfer claim between players (owner/assistant only)
 * - /kingdom claim info - Show info about current claim
 *
 * PERMISSIONS:
 * - Requires kingdom membership
 * - Subject to kingdom personal claim limits
 * - Respects chunk ownership and expiration
 * - Owner/Assistant can manage all claims in kingdom
 */

package com.odaishi.asheskingdoms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.odaishi.asheskingdoms.AshesKingdoms;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Map;
import java.util.UUID;

public class KingdomPersonalClaimCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("kingdom")
                .then(CommandManager.literal("claim")
                        .then(CommandManager.literal("personal")
                                .executes(KingdomPersonalClaimCommand::claimPersonal)
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .requires(source -> source.getPlayer() != null &&
                                                hasClaimManagementPermission(source.getPlayer()))
                                        .executes(KingdomPersonalClaimCommand::claimForPlayer)))
                        .then(CommandManager.literal("list")
                                .executes(KingdomPersonalClaimCommand::listPersonalClaims))
                        .then(CommandManager.literal("unclaim")
                                .executes(KingdomPersonalClaimCommand::unclaimPersonal)
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .requires(source -> source.getPlayer() != null &&
                                                hasClaimManagementPermission(source.getPlayer()))
                                        .executes(KingdomPersonalClaimCommand::unclaimForPlayer)))
                        .then(CommandManager.literal("transfer")
                                .requires(source -> source.getPlayer() != null &&
                                        hasClaimManagementPermission(source.getPlayer()))
                                .then(CommandManager.argument("fromPlayer", StringArgumentType.word())
                                        .then(CommandManager.argument("toPlayer", StringArgumentType.word())
                                                .executes(KingdomPersonalClaimCommand::transferClaim))))
                        .then(CommandManager.literal("info")
                                .executes(KingdomPersonalClaimCommand::claimInfo))));
    }

    private static boolean hasClaimManagementPermission(ServerPlayerEntity player) {
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        return kingdom != null && (kingdom.isOwner(player) || kingdom.getRank(player).equals(Kingdom.RANK_ASSISTANT));
    }

    static int claimPersonal(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        ChunkPos chunk = new ChunkPos(player.getBlockPos());

        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        // Check if player can claim personally
        if (!kingdom.canClaimPersonally(player.getUuid(), chunk)) {
            player.sendMessage(Text.of("§cCannot claim this chunk personally!"), false);
            return 0;
        }

        // Check personal claim limit
        int currentClaims = kingdom.getPersonalClaimCount(player.getUuid());
        int maxClaims = AshesKingdoms.getConfig().maxPersonalClaimsPerPlayer;

        if (currentClaims >= maxClaims) {
            player.sendMessage(Text.of("§cYou have reached your personal claim limit (" + maxClaims + ")!"), false);
            return 0;
        }

        // Check if personal claims are enabled
        if (!AshesKingdoms.getConfig().allowPersonalClaims) {
            player.sendMessage(Text.of("§cPersonal claims are disabled!"), false);
            return 0;
        }

        // Check economy if enabled
        long claimCost = AshesKingdoms.getConfig().personalClaimCost;
        if (claimCost > 0) {
            if (!KingdomManager.hasCoins(player, claimCost)) {
                player.sendMessage(Text.of("§cYou need " + KingdomManager.formatCoins(claimCost) + " to claim this chunk!"), false);
                return 0;
            }

            if (!KingdomManager.takeCoins(player, claimCost)) {
                player.sendMessage(Text.of("§cFailed to process payment!"), false);
                return 0;
            }
        }

        // Create the personal claim - FIXED: Add executor parameter
        if (kingdom.addPersonalClaim(chunk, player.getUuid(), player.getUuid())) {
            player.sendMessage(Text.of("§aSuccessfully claimed this chunk personally!"), false);

            // Notify kingdom leadership
            if (player.getServer() != null) {
                for (UUID memberId : kingdom.getMembers().keySet()) {
                    ServerPlayerEntity member = player.getServer().getPlayerManager().getPlayer(memberId);
                    if (member != null && (kingdom.isOwner(memberId) || kingdom.getRank(memberId).equals(Kingdom.RANK_ASSISTANT))) {
                        member.sendMessage(Text.of("§e" + player.getName().getString() + " has claimed a personal chunk at [" + chunk.x + ", " + chunk.z + "]"), false);
                    }
                }
            }

            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                player.sendMessage(Text.of("§cFailed to save kingdom data!"), false);
            }

            return 1;
        } else {
            player.sendMessage(Text.of("§cFailed to claim this chunk!"), false);
            return 0;
        }
    }

    static int claimForPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        String targetName = StringArgumentType.getString(context, "player");
        ChunkPos chunk = new ChunkPos(executor.getBlockPos());

        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
        if (kingdom == null) {
            executor.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        ServerPlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null || !kingdom.isMember(target.getUuid())) {
            executor.sendMessage(Text.of("§cPlayer not found or not a kingdom member!"), false);
            return 0;
        }

        // Check target's personal claim limit
        int currentClaims = kingdom.getPersonalClaimCount(target.getUuid());
        int maxClaims = AshesKingdoms.getConfig().maxPersonalClaimsPerPlayer;

        if (currentClaims >= maxClaims) {
            executor.sendMessage(Text.of("§c" + target.getName().getString() + " has reached their personal claim limit (" + maxClaims + ")!"), false);
            return 0;
        }

        // Create the personal claim for target player
        if (kingdom.addPersonalClaim(chunk, target.getUuid(), executor.getUuid())) {
            executor.sendMessage(Text.of("§aClaim assigned to " + target.getName().getString() + "!"), false);
            target.sendMessage(Text.of("§aYou've been granted a personal claim by " + executor.getName().getString()), false);

            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                executor.sendMessage(Text.of("§cFailed to save kingdom data!"), false);
            }

            return 1;
        } else {
            executor.sendMessage(Text.of("§cFailed to assign claim!"), false);
            return 0;
        }
    }

    static int listPersonalClaims(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());

        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        Map<ChunkPos, ?> personalClaims = kingdom.getPersonalClaims(player.getUuid());

        if (personalClaims.isEmpty()) {
            player.sendMessage(Text.of("§eYou have no personal claims."), false);
            return 0;
        }

        player.sendMessage(Text.of("§6=== Your Personal Claims ==="), false);
        player.sendMessage(Text.of("§aTotal: §e" + personalClaims.size() + "§a/§e" + AshesKingdoms.getConfig().maxPersonalClaimsPerPlayer), false);

        for (ChunkPos chunk : personalClaims.keySet()) {
            String status = "§aActive";
            player.sendMessage(Text.of("§bChunk [" + chunk.x + ", " + chunk.z + "] - " + status), false);
        }

        return 1;
    }

    static int unclaimPersonal(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        ChunkPos chunk = new ChunkPos(player.getBlockPos());

        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        if (!kingdom.hasPersonalClaim(chunk)) {
            player.sendMessage(Text.of("§cThis chunk is not personally claimed!"), false);
            return 0;
        }

        UUID claimOwner = kingdom.getPersonalClaimOwner(chunk);
        if (claimOwner == null || (!claimOwner.equals(player.getUuid()) && !kingdom.isOwner(player.getUuid()))) {
            player.sendMessage(Text.of("§cYou don't own this personal claim!"), false);
            return 0;
        }

        // FIXED: Add executor parameter
        if (kingdom.removePersonalClaim(chunk, player.getUuid())) {
            player.sendMessage(Text.of("§aPersonal claim removed successfully!"), false);

            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                player.sendMessage(Text.of("§cFailed to save kingdom data!"), false);
            }

            return 1;
        } else {
            player.sendMessage(Text.of("§cFailed to remove personal claim!"), false);
            return 0;
        }
    }

    static int unclaimForPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        String targetName = StringArgumentType.getString(context, "player");
        ChunkPos chunk = new ChunkPos(executor.getBlockPos());

        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
        if (kingdom == null) {
            executor.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        ServerPlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null || !kingdom.isMember(target.getUuid())) {
            executor.sendMessage(Text.of("§cPlayer not found or not a kingdom member!"), false);
            return 0;
        }

        if (!kingdom.hasPersonalClaim(chunk)) {
            executor.sendMessage(Text.of("§cThis chunk is not personally claimed!"), false);
            return 0;
        }

        // Remove the claim using executor's permissions
        if (kingdom.removePersonalClaim(chunk, executor.getUuid())) {
            executor.sendMessage(Text.of("§aRemoved personal claim from " + target.getName().getString() + "!"), false);
            target.sendMessage(Text.of("§cYour personal claim was removed by " + executor.getName().getString()), false);

            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                executor.sendMessage(Text.of("§cFailed to save kingdom data!"), false);
            }

            return 1;
        } else {
            executor.sendMessage(Text.of("§cFailed to remove claim!"), false);
            return 0;
        }
    }

    static int transferClaim(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        String fromName = StringArgumentType.getString(context, "fromPlayer");
        String toName = StringArgumentType.getString(context, "toPlayer");
        ChunkPos chunk = new ChunkPos(executor.getBlockPos());

        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
        if (kingdom == null) {
            executor.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        ServerPlayerEntity fromPlayer = executor.getServer().getPlayerManager().getPlayer(fromName);
        ServerPlayerEntity toPlayer = executor.getServer().getPlayerManager().getPlayer(toName);

        if (fromPlayer == null || toPlayer == null ||
                !kingdom.isMember(fromPlayer.getUuid()) || !kingdom.isMember(toPlayer.getUuid())) {
            executor.sendMessage(Text.of("§cPlayers not found or not kingdom members!"), false);
            return 0;
        }

        // Check target's personal claim limit
        int currentClaims = kingdom.getPersonalClaimCount(toPlayer.getUuid());
        int maxClaims = AshesKingdoms.getConfig().maxPersonalClaimsPerPlayer;

        if (currentClaims >= maxClaims) {
            executor.sendMessage(Text.of("§c" + toPlayer.getName().getString() + " has reached their personal claim limit (" + maxClaims + ")!"), false);
            return 0;
        }

        if (kingdom.transferPersonalClaim(chunk, toPlayer.getUuid(), executor.getUuid())) {
            executor.sendMessage(Text.of("§aClaim transferred from " + fromName + " to " + toName + "!"), false);
            fromPlayer.sendMessage(Text.of("§eYour personal claim was transferred to " + toPlayer.getName().getString()), false);
            toPlayer.sendMessage(Text.of("§aYou received a personal claim from " + fromPlayer.getName().getString()), false);

            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                executor.sendMessage(Text.of("§cFailed to save kingdom data!"), false);
            }

            return 1;
        } else {
            executor.sendMessage(Text.of("§cFailed to transfer claim!"), false);
            return 0;
        }
    }

    static int claimInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        ChunkPos chunk = new ChunkPos(player.getBlockPos());

        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        if (!kingdom.hasPersonalClaim(chunk)) {
            player.sendMessage(Text.of("§eThis chunk is not personally claimed."), false);
            return 0;
        }

        UUID claimOwner = kingdom.getPersonalClaimOwner(chunk);
        if (claimOwner == null) {
            player.sendMessage(Text.of("§cInvalid claim data!"), false);
            return 0;
        }

        String ownerName = "Unknown";
        ServerPlayerEntity ownerPlayer = player.getServer().getPlayerManager().getPlayer(claimOwner);
        if (ownerPlayer != null) {
            ownerName = ownerPlayer.getName().getString();
        }

        boolean hasAccess = kingdom.hasPersonalClaimAccess(player.getUuid(), chunk);

        player.sendMessage(Text.of("§6=== Personal Claim Info ==="), false);
        player.sendMessage(Text.of("§bOwner: §a" + ownerName), false);
        player.sendMessage(Text.of("§bLocation: §e[" + chunk.x + ", " + chunk.z + "]"), false);
        player.sendMessage(Text.of("§bYour Access: " + (hasAccess ? "§aGranted" : "§cDenied")), false);

        return 1;
    }
}