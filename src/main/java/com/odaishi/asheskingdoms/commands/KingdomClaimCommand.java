/**
 * TERRITORY CLAIM COMMAND HANDLER
 *
 * Handles chunk claiming operations for kingdom territory expansion.
 * Provides the primary interface for players to claim land for their kingdom.
 *
 * COMMAND:
 * - /kingdom claim - Claims the current chunk for player's kingdom
 *
 * FEATURES:
 * - Centralized claim logic through KingdomManager
 * - Automatic adjacency validation for new claims
 * - Economy integration with claim costs
 * - Permission checking for claim authority
 * - Kingdom membership verification
 *
 * VALIDATION CHECKS:
 * - Player must be in a kingdom
 * - Player must have claim permission based on rank
 * - Chunk must not be already claimed
 * - Chunk must be adjacent to existing kingdom territory
 * - Kingdom must not exceed claim limit (25 chunks)
 * - Player must have sufficient coins for claim cost
 *
 * ECONOMY:
 * - Claims cost 10 silver (1000 bronze) after initial claim
 * - First claim is free (home territory)
 * - Integrated with Numismatic Overhaul currency
 * - Automatic coin deduction on successful claim
 *
 * USER FEEDBACK:
 * - Success messages with cost information
 * - Specific error messages for each failure case
 * - Color-coded feedback for easy understanding
 * - Automatic save confirmation
 *
 * INTEGRATION:
 * - Uses KingdomManager for core claim logic
 * - Leverages economy system for transactions
 * - Respects kingdom permission hierarchy
 * - Maintains territory adjacency rules
 */

package com.odaishi.asheskingdoms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class KingdomClaimCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("kingdom")
                        .then(literal("claim")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;

                                    // Use the centralized claim logic from KingdomManager
                                    boolean success = KingdomManager.claimChunk(player);

                                    if (!success) {
                                        // The claimChunk method already sends appropriate error messages
                                        return 0;
                                    }

                                    return 1;
                                })
                        )
        );
    }
}