/**
 * COMMAND HANDLER FOR KINGDOM SETTINGS AND PERSONAL CLAIMS
 *
 * Provides in-game commands for players to manage kingdom settings
 * and personal claims through the chat interface.
 */
package com.odaishi.asheskingdoms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.odaishi.asheskingdoms.kingdoms.KingdomSettings;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

public class KingdomSettingsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("kingdom")
                .then(CommandManager.literal("settings")
                        .then(CommandManager.literal("list")
                                .executes(KingdomSettingsCommand::listSettings))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("setting", StringArgumentType.string())
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(KingdomSettingsCommand::setSetting))))
                        .then(CommandManager.literal("claim")
                                .then(CommandManager.literal("personal")
                                        .executes(KingdomPersonalClaimCommand::claimPersonal))
                                .then(CommandManager.literal("list")
                                        .executes(KingdomPersonalClaimCommand::listPersonalClaims))
                                .then(CommandManager.literal("unclaim")
                                        .executes(KingdomPersonalClaimCommand::unclaimPersonal))
                                .then(CommandManager.literal("info")
                                        .executes(KingdomPersonalClaimCommand::claimInfo)))));
    }

    private static int listSettings(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());

        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        player.sendMessage(Text.of("§6=== Kingdom Settings ==="), false);
        for (String setting : KingdomSettings.getAvailableSettings()) {
            boolean value = kingdom.getSettings().getSetting(setting);
            player.sendMessage(Text.of("§b" + setting + ": §a" + value), false);
        }
        return 1;
    }

    private static int setSetting(CommandContext<ServerCommandSource> context) {
        // Implementation for setting changes
        return 1;
    }

    private static int claimChunkPersonal(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        ChunkPos chunk = new ChunkPos(player.getBlockPos());

        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return 0;
        }

        if (kingdom.addPersonalClaim(chunk, player.getUuid())) {
            player.sendMessage(Text.of("§aSuccessfully claimed this chunk personally!"), false);
        } else {
            player.sendMessage(Text.of("§cCould not claim this chunk!"), false);
        }
        return 1;
    }
}