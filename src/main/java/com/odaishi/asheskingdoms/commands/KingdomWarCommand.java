package com.odaishi.asheskingdoms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.odaishi.asheskingdoms.kingdoms.KingdomWarManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.Optional;

import static com.odaishi.asheskingdoms.commands.KingdomCommand.notifyKingdom;
import static com.odaishi.asheskingdoms.noapi.NOLog.error;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KingdomWarCommand {

    private static int endWarEarly(CommandContext<ServerCommandSource> context, KingdomWarManager.War war, ServerPlayerEntity player, boolean isAttacker) {
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error("You are not in a kingdom", context);

        if (!kingdom.isOwner(player.getUuid()) && !kingdom.getRank(player.getUuid()).equals(Kingdom.RANK_ASSISTANT)) {
            return error("Only leaders/assistants can end wars", context);
        }

        if (isAttacker) {
            // Attacker chooses to end war early (white peace)
            KingdomWarManager.endWar(war.id, false);
            context.getSource().sendFeedback(() -> Text.literal("§aEnded war early with " + war.defender), false);

            // Notify both sides
            notifyKingdom(kingdom, "§aEnded war early with " + war.defender);
            notifyKingdom(KingdomManager.getKingdom(war.defender), "§a" + kingdom.getName() + " has ended the war early");
        }

        return 1;
    }

    private static int surrender(CommandContext<ServerCommandSource> context, KingdomWarManager.War war, ServerPlayerEntity player) {
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error("You are not in a kingdom", context);

        if (!kingdom.isOwner(player.getUuid())) {
            return error("Only the kingdom leader can surrender", context);
        }

        // Defender surrenders - attacker gets victory
        war.defenderSurrendered = true;
        war.attackerVictory = true;
        KingdomWarManager.endWar(war.id, true);

        context.getSource().sendFeedback(() -> Text.literal("§cSurrendered to " + war.attacker), false);

        // Notify both sides
        notifyKingdom(kingdom, "§cSurrendered to " + war.attacker);
        notifyKingdom(KingdomManager.getKingdom(war.attacker), "§a" + kingdom.getName() + " has surrendered!");

        return 1;
    }

    private static int claimFallenKingdom(CommandContext<ServerCommandSource> context, KingdomWarManager.War war, Kingdom attackingKingdom) {
        Kingdom defenderKingdom = KingdomManager.getKingdom(war.defender);
        if (defenderKingdom == null || !defenderKingdom.isFalling()) {
            return error("Kingdom is not available for claiming", context);
        }

        // Claim all captured territories
        for (ChunkPos claim : war.capturedClaims) {
            defenderKingdom.getClaimedChunks().remove(claim);
            attackingKingdom.addClaim(claim);
        }

        // Transfer treasury (optional - could be war spoils)
        long spoils = defenderKingdom.getTreasury() / 2; // 50% of defender's treasury
        if (spoils > 0) {
            defenderKingdom.withdraw(spoils);
            attackingKingdom.deposit(spoils);
        }

        // End the war completely
        KingdomWarManager.removeWar(war.id);
        defenderKingdom.setFalling(false);

        context.getSource().sendFeedback(() -> Text.literal("§aSuccessfully claimed " + war.defender + "!"), false);
        notifyKingdom(defenderKingdom, "§cYour kingdom has been claimed by " + attackingKingdom.getName());

        return 1;
    }

    private static void notifyKingdom(Kingdom kingdom, String message) {
        MinecraftServer server = KingdomManager.getServer();
        if (server != null && kingdom != null) {
            kingdom.getMembers().keySet().forEach(id -> {
                ServerPlayerEntity member = server.getPlayerManager().getPlayer(id);
                if (member != null) {
                    member.sendMessage(Text.literal("§6[War] " + message), false);
                }
            });
        }
    }

    private static final long WAR_DECLARATION_COST = 50000; // 50,000 bronze

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("kingdom")
                .then(literal("war")
                        .then(literal("end")
                                .executes(ctx -> {
                                    ServerCommandSource src = ctx.getSource();
                                    ServerPlayerEntity player = src.getPlayer();
                                    if (player == null) return 0;

                                    Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
                                    if (kingdom == null) {
                                        src.sendError(Text.literal("§cYou are not in a kingdom!"));
                                        return 0;
                                    }

                                    // Find active war involving this kingdom
                                    Optional<KingdomWarManager.War> warOpt = KingdomWarManager.getAllWars().stream()
                                            .filter(w -> w.active && (w.attacker.equals(kingdom.getName()) || w.defender.equals(kingdom.getName())))
                                            .findFirst();

                                    if (warOpt.isEmpty()) {
                                        src.sendError(Text.literal("§cYour kingdom is not in any active war!"));
                                        return 0;
                                    }

                                    KingdomWarManager.War war = warOpt.get();

                                    if (war.attacker.equals(kingdom.getName())) {
                                        // Attacker ending war early
                                        return endWarEarly(ctx, war, player, true);
                                    } else {
                                        // Defender surrendering
                                        return surrender(ctx, war, player);
                                    }
                                })
                        )
                        .then(literal("claim")
                                .executes(ctx -> {
                                    ServerCommandSource src = ctx.getSource();
                                    ServerPlayerEntity player = src.getPlayer();
                                    if (player == null) return 0;

                                    Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
                                    if (kingdom == null) {
                                        src.sendError(Text.literal("§cYou are not in a kingdom!"));
                                        return 0;
                                    }

                                    // Find war where this kingdom is attacker and defender has fallen
                                    Optional<KingdomWarManager.War> warOpt = KingdomWarManager.getAllWars().stream()
                                            .filter(w -> w.attacker.equals(kingdom.getName()) && w.attackerVictory)
                                            .findFirst();

                                    if (warOpt.isEmpty()) {
                                        src.sendError(Text.literal("§cNo fallen kingdoms available to claim!"));
                                        return 0;
                                    }

                                    return claimFallenKingdom(ctx, warOpt.get(), kingdom);
                                })
                        )
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