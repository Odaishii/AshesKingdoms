/**
 * PERSONAL CLAIM COMMAND HANDLER
 *
 * Handles all commands related to personal claims within kingdom territory.
 * Allows players to claim, unclaim, and manage their personal chunks.
 *
 * COMMANDS:
 * - /kingdom claim personal - Claim current chunk personally
 * - /kingdom claim list - List your personal claims
 * - /kingdom claim unclaim - Unclaim current personal claim
 * - /kingdom claim info - Show info about current claim
 *
 * PERMISSIONS:
 * - Requires kingdom membership
 * - Subject to kingdom personal claim limits
 * - Respects chunk ownership and expiration
 */

package com.odaishi.asheskingdoms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.odaishi.asheskingdoms.utils.ModConfig;
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
                                .executes(KingdomPersonalClaimCommand::claimPersonal))
                        .then(CommandManager.literal("list")
                                .executes(KingdomPersonalClaimCommand::listPersonalClaims))
                        .then(CommandManager.literal("unclaim")
                                .executes(KingdomPersonalClaimCommand::unclaimPersonal))
                        .then(CommandManager.literal("info")
                                .executes(KingdomPersonalClaimCommand::claimInfo))));
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
        int maxClaims = ModConfig.getInstance().maxPersonalClaimsPerPlayer;

        if (currentClaims >= maxClaims) {
            player.sendMessage(Text.of("§cYou have reached your personal claim limit (" + maxClaims + ")!"), false);
            return 0;
        }

        // Check if personal claims are enabled
        if (!ModConfig.getInstance().allowPersonalClaims) {
            player.sendMessage(Text.of("§cPersonal claims are disabled!"), false);
            return 0;
        }

        // Check economy if enabled
        long claimCost = ModConfig.getInstance().personalClaimCost;
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

        // Create the personal claim
        if (kingdom.addPersonalClaim(chunk, player.getUuid())) {
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
        player.sendMessage(Text.of("§aTotal: §e" + personalClaims.size() + "§a/§e" + ModConfig.getInstance().maxPersonalClaimsPerPlayer), false);

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

        if (kingdom.removePersonalClaim(chunk)) {
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