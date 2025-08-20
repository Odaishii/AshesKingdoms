package com.odaishi.asheskingdoms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.AshesKingdoms;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import static net.minecraft.server.command.CommandManager.literal;

public class KingdomClaimCommand {

    private static final int BASE_CLAIM_COST = 100; // Example: 100 coins per claim
    private static final int MAX_CLAIMS = 25;       // Max claims per kingdom

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("kingdom")
                        .then(literal("claim")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;

                                    // Get player's kingdom
                                    Kingdom kingdom = KingdomManager.getPlayerKingdom(player.getUuid());
                                    if (kingdom == null) {
                                        player.sendMessage(Text.of("§cYou must be in a kingdom to claim land."), false);
                                        return 0;
                                    }

                                    ChunkPos pos = new ChunkPos(player.getBlockPos());

                                    // Check if chunk is already claimed
                                    if (KingdomManager.isClaimed(pos)) {
                                        player.sendMessage(Text.of("§cThis chunk is already claimed."), false);
                                        return 0;
                                    }

                                    // Check claim limit
                                    if (kingdom.getClaims().size() >= MAX_CLAIMS) {
                                        player.sendMessage(Text.of("§cYour kingdom has reached the maximum number of claims (" + MAX_CLAIMS + ")."), false);
                                        return 0;
                                    }

                                    // ✅ New adjacency rule:
                                    if (!kingdom.isAdjacent(pos) && !kingdom.getClaims().isEmpty()) {
                                        player.sendMessage(Text.of("§cNew claims must be adjacent to your kingdom's territory."), false);
                                        return 0;
                                    }

                                    // Calculate cost
                                    int cost = BASE_CLAIM_COST * (kingdom.getClaims().size() + 1);

                                    // Deduct coins using NoApi or fallback
                                    boolean paid;
                                    if (AshesKingdoms.noApi != null) {
                                        paid = AshesKingdoms.noApi.withdraw(player, cost);
                                    } else {
                                        paid = KingdomManager.hasCoins(player, cost);
                                        if (paid) KingdomManager.takeCoins(player, cost);
                                    }

                                    if (!paid) {
                                        player.sendMessage(Text.of("§cYou need " + cost + " coins to claim this land."), false);
                                        return 0;
                                    }

                                    // Add the claim
                                    kingdom.addClaim(pos);

                                    // FIXED: Replace KingdomManager.saveData() with the correct method
                                    try {
                                        KingdomManager.saveToFile();
                                    } catch (Exception e) {
                                        player.sendMessage(Text.of("§cFailed to save kingdom data: " + e.getMessage()), false);
                                    }

                                    player.sendMessage(Text.of("§aSuccessfully claimed chunk at " + pos.x + ", " + pos.z + "!"), false);
                                    return 1;
                                })
                        )
        );
    }
}