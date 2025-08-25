package com.odaishi.asheskingdoms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.odaishi.asheskingdoms.kingdoms.KingdomWarManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KingdomWarCommand {

    private static final long WAR_DECLARATION_COST = 50000; // 50,000 bronze

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("kingdom")
                .then(literal("war")
                        .then(literal("declare")
                                .then(argument("defender", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerCommandSource src = ctx.getSource();
                                            ServerPlayerEntity player = src.getPlayer();
                                            if (player == null) return 0;

                                            String defender = StringArgumentType.getString(ctx, "defender");
                                            Kingdom attackerKingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());

                                            if (attackerKingdom == null) {
                                                src.sendError(Text.literal("§cYou are not in a kingdom!"));
                                                return 0;
                                            }

                                            if (!attackerKingdom.isOwner(player.getUuid()) &&
                                                    !attackerKingdom.getRank(player.getUuid()).equals(Kingdom.RANK_ASSISTANT)) {
                                                src.sendError(Text.literal("§cOnly leaders and assistants can declare war!"));
                                                return 0;
                                            }

                                            if (attackerKingdom.getName().equals(defender)) {
                                                src.sendError(Text.literal("§cCannot declare war on yourself!"));
                                                return 0;
                                            }

                                            if (!attackerKingdom.isEnemy(defender)) {
                                                src.sendError(Text.literal("§cYou must set " + defender + " as an enemy first!"));
                                                return 0;
                                            }

                                            Optional<KingdomWarManager.War> warOpt =
                                                    KingdomWarManager.declareWar(attackerKingdom.getName(), defender, WAR_DECLARATION_COST);

                                            if (warOpt.isPresent()) {
                                                KingdomWarManager.War war = warOpt.get();
                                                src.sendFeedback(() -> Text.literal("§cWar declared on " + defender + "! Cost: " +
                                                        formatCurrency(WAR_DECLARATION_COST) + ". Grace period ends in 48 hours."), false);
                                                return 1;
                                            } else {
                                                src.sendError(Text.literal("§cFailed to declare war! Check treasury or if already at war."));
                                                return 0;
                                            }
                                        })
                                )
                        )
                        .then(literal("list")
                                .executes(ctx -> {
                                    ServerCommandSource src = ctx.getSource();
                                    StringBuilder sb = new StringBuilder("§6=== Active Wars ===\n");

                                    for (KingdomWarManager.War war : KingdomWarManager.getAllWars()) {
                                        if (war.active) {
                                            String status = war.isInGracePeriod() ? "§eGrace Period" : "§cActive";
                                            double progress = war.getConquestPercentage() * 100;
                                            sb.append("§b").append(war.attacker).append(" §7-> §c").append(war.defender)
                                                    .append(" §8- ").append(status).append(" §7(").append(String.format("%.1f", progress))
                                                    .append("% captured)\n");
                                        }
                                    }

                                    final String output = sb.toString().equals("§6=== Active Wars ===\n")
                                            ? "§aNo active wars."
                                            : sb.toString();

                                    src.sendFeedback(() -> Text.literal(output), false);
                                    return 1;
                                })
                        )
                        .then(literal("status")
                                .executes(ctx -> {
                                    ServerCommandSource src = ctx.getSource();
                                    ServerPlayerEntity player = src.getPlayer();
                                    if (player == null) return 0;

                                    Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
                                    if (kingdom == null) {
                                        src.sendError(Text.literal("§cYou are not in a kingdom!"));
                                        return 0;
                                    }

                                    StringBuilder sb = new StringBuilder("§6=== Your Kingdom's Wars ===\n");
                                    boolean hasWars = false;

                                    for (KingdomWarManager.War war : KingdomWarManager.getAllWars()) {
                                        if (war.active && (war.attacker.equals(kingdom.getName()) || war.defender.equals(kingdom.getName()))) {
                                            hasWars = true;
                                            String role = war.attacker.equals(kingdom.getName()) ? "§aAttacker" : "§cDefender";
                                            String status = war.isInGracePeriod() ? "§eGrace Period" : "§cActive";
                                            double progress = war.getConquestPercentage() * 100;

                                            sb.append(role).append(": §b").append(war.attacker.equals(kingdom.getName()) ? war.defender : war.attacker)
                                                    .append(" §8- ").append(status).append(" §7(").append(String.format("%.1f", progress))
                                                    .append("% captured)\n");
                                        }
                                    }

                                    if (!hasWars) {
                                        sb.append("§aNo active wars involving your kingdom.");
                                    }

                                    src.sendFeedback(() -> Text.literal(sb.toString()), false);
                                    return 1;
                                })
                        )
                        .then(literal("capture")
                                .executes(ctx -> {
                                    ServerCommandSource src = ctx.getSource();
                                    ServerPlayerEntity player = src.getPlayer();
                                    if (player == null) return 0;

                                    Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
                                    if (kingdom == null) {
                                        src.sendError(Text.literal("§cYou are not in a kingdom!"));
                                        return 0;
                                    }

                                    ChunkPos currentChunk = new ChunkPos(player.getBlockPos());
                                    Kingdom chunkKingdom = KingdomManager.getKingdomAt(currentChunk);

                                    if (chunkKingdom == null || chunkKingdom.getName().equals(kingdom.getName())) {
                                        src.sendError(Text.literal("§cYou are not on enemy territory!"));
                                        return 0;
                                    }

                                    Optional<KingdomWarManager.War> warOpt = KingdomWarManager.getWarBetween(kingdom.getName(), chunkKingdom.getName());
                                    if (warOpt.isEmpty()) {
                                        src.sendError(Text.literal("§cYour kingdom is not at war with " + chunkKingdom.getName() + "!"));
                                        return 0;
                                    }

                                    KingdomWarManager.War war = warOpt.get();
                                    if (war.isInGracePeriod()) {
                                        src.sendError(Text.literal("§cWar is still in grace period!"));
                                        return 0;
                                    }

                                    if (KingdomWarManager.captureClaim(currentChunk, kingdom.getName(), player)) {
                                        src.sendFeedback(() -> Text.literal("§aCapture initiated! Hold position for 2 minutes."), false);
                                    } else {
                                        src.sendFeedback(() -> Text.literal("§eCapture in progress..."), false);
                                    }
                                    return 1;
                                })
                        )
                )
        );
    }

    private static String formatCurrency(long amount) {
        long gold = amount / 10000, silver = (amount % 10000) / 100, bronze = amount % 100;
        if (gold > 0) return String.format("%d gold, %d silver, %d bronze", gold, silver, bronze);
        if (silver > 0) return String.format("%d silver, %d bronze", silver, bronze);
        return String.format("%d bronze", bronze);
    }
}