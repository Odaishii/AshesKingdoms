/**
 * KINGDOM WAR COMMAND HANDLER
 *
 * Provides in-game commands for declaring and managing inter-kingdom warfare.
 * Enables kingdom leaders to initiate conflicts and monitor ongoing wars.
 *
 * COMMANDS:
 * - /kingdom war declare <attacker> <defender> - Declares war between kingdoms
 * - /kingdom war list - Lists all active and historical wars
 *
 * FUNCTIONALITY:
 * - War declaration with automatic conflict creation
 * - UUID-based war tracking and identification
 * - War status display (active/ended)
 * - Feedback messages for command execution
 *
 * SECURITY:
 * - Currently open to any player (consider adding rank checks)
 * - Future: Restrict to kingdom leaders/officers
 * - Future: Add declaration cooldowns and costs
 *
 * INTEGRATION:
 * - Direct interface with KingdomWarManager
 * - War persistence through KingdomManager saves
 * - Compatible with future war expansion features
 *
 * EXPANSION POINTS:
 * - War resolution commands (/kingdom war end)
 * - War status and information queries
 * - Alliance system integration
 * - War cost and economy requirements
 * - Declarative war goals and victory conditions
 */

package com.odaishi.asheskingdoms.commands;

import com.odaishi.asheskingdoms.kingdoms.KingdomWarManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KingdomWarCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("kingdom")
                        .then(literal("war")
                                .then(literal("declare")
                                        .then(argument("attacker", StringArgumentType.word())
                                                .then(argument("defender", StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            ServerCommandSource src = ctx.getSource();
                                                            String attacker = StringArgumentType.getString(ctx, "attacker");
                                                            String defender = StringArgumentType.getString(ctx, "defender");

                                                            KingdomWarManager.War war = KingdomWarManager.declareWar(attacker, defender);
                                                            src.sendFeedback(() -> Text.of("Declared war: " + war.attacker + " -> " + war.defender + " (id=" + war.id + ")"), false);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                                .then(literal("list")
                                        .executes(ctx -> {
                                            ServerCommandSource src = ctx.getSource();
                                            StringBuilder sb = new StringBuilder();
                                            for (KingdomWarManager.War w : KingdomWarManager.getAllWars()) {
                                                sb.append(w.id).append(" : ").append(w.attacker).append(" -> ").append(w.defender).append(w.active ? " (active)" : " (ended)").append("\n");
                                            }
                                            String out = sb.length() == 0 ? "No wars." : sb.toString();
                                            src.sendFeedback(() -> Text.of(out), false);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
