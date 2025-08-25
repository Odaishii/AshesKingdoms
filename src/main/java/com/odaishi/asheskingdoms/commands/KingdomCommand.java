package com.odaishi.asheskingdoms.commands;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.odaishi.asheskingdoms.noapi.NoApiAccess;
import com.odaishi.asheskingdoms.utils.InventoryCoins;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.UUID;
import static net.minecraft.server.command.CommandManager.*;

public class KingdomCommand {
    private static final long KINGDOM_COST = 10000;
    private static final long WAR_DECLARATION_COST = 50000; // Fixed cost to declare war

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("kingdom")
                .then(literal("create").then(argument("name", StringArgumentType.word()).executes(context ->
                        createKingdom(context, StringArgumentType.getString(context, "name")))))
                .then(literal("leave").executes(context -> leaveKingdom(context)))
                .then(literal("delete").executes(context -> deleteKingdom(context)))
                .then(literal("confirm").then(literal("delete").executes(context -> confirmDelete(context))))
                .then(literal("list").executes(context -> listKingdoms(context)))
                .then(literal("info").executes(context -> kingdomInfo(context)))

                // ==================== DIPLOMACY COMMANDS ====================
                .then(literal("relations")
                        .executes(context -> showRelations(context)))
                .then(literal("ally")
                        .then(literal("add").then(argument("kingdom", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestOtherKingdoms(context, builder).buildFuture())
                                        .executes(context -> addAlly(context, StringArgumentType.getString(context, "kingdom"))))
                                .then(literal("remove").then(argument("kingdom", StringArgumentType.word())
                                                .suggests((context, builder) -> suggestAllies(context, builder).buildFuture())
                                                .executes(context -> removeAlly(context, StringArgumentType.getString(context, "kingdom"))))
                                        .then(literal("list").executes(context -> listAllies(context))))
                                .then(literal("enemy")
                                        .then(literal("add").then(argument("kingdom", StringArgumentType.word())
                                                        .suggests((context, builder) -> suggestOtherKingdoms(context, builder).buildFuture())
                                                        .executes(context -> addEnemy(context, StringArgumentType.getString(context, "kingdom"))))
                                                .then(literal("remove").then(argument("kingdom", StringArgumentType.word())
                                                                .suggests((context, builder) -> suggestEnemies(context, builder).buildFuture())
                                                                .executes(context -> removeEnemy(context, StringArgumentType.getString(context, "kingdom"))))
                                                        .then(literal("list").executes(context -> listEnemies(context))))
                                                // ==================== END DIPLOMACY COMMANDS ====================

                                                .then(literal("treasury")
                                                        .then(literal("balance").executes(KingdomCommand::checkTreasuryBalance))
                                                        .then(literal("upkeep").executes(KingdomCommand::checkUpkeepCost))
                                                        .then(literal("deposit").then(argument("amount", StringArgumentType.greedyString())
                                                                .executes(context -> {
                                                                    String amountStr = StringArgumentType.getString(context, "amount");
                                                                    long amount = parseCurrencyAmount(amountStr);
                                                                    if (amount <= 0) {
                                                                        context.getSource().sendError(Text.literal("§cInvalid amount! Use format: 1g, 5s, 100b, or 500"));
                                                                        return 0;
                                                                    }
                                                                    return depositToTreasury(context, amount);
                                                                })
                                                        ))

                                                        .then(literal("withdraw").then(argument("amount", StringArgumentType.greedyString())
                                                                .executes(context -> {
                                                                    String amountStr = StringArgumentType.getString(context, "amount");
                                                                    long amount = parseCurrencyAmount(amountStr);
                                                                    if (amount <= 0) {
                                                                        context.getSource().sendError(Text.literal("§cInvalid amount! Use format: 1g, 5s, 100b, or 500"));
                                                                        return 0;
                                                                    }
                                                                    return withdrawFromTreasury(context, amount);
                                                                })
                                                        )))
// MOVE ADMIN COMMAND HERE - ROOT LEVEL
                                                .then(literal("admin")
                                                        .requires(source -> source.hasPermissionLevel(4)) // Only OPs can use
                                                        .then(literal("forceupkeep")
                                                                .executes(context -> {
                                                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                                                    Kingdom kingdom = Kingdom.getPlayerKingdom(player);
                                                                    if (kingdom == null) return error(context, "You are not in a kingdom");

                                                                    // Force upkeep check
                                                                    if (!kingdom.processUpkeep()) {
                                                                        kingdom.setFalling(true);
                                                                        context.getSource().sendFeedback(() -> Text.literal("§cKingdom set to falling state - upkeep failed"), false);
                                                                    } else {
                                                                        kingdom.lastUpkeepCollection = System.currentTimeMillis();
                                                                        context.getSource().sendFeedback(() -> Text.literal("§aUpkeep processed successfully"), false);
                                                                    }
                                                                    kingdom.markDirty();
                                                                    return 1;
                                                                })
                                                        )
                                                        .then(literal("setfalling")
                                                                .executes(context -> {
                                                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                                                    Kingdom kingdom = Kingdom.getPlayerKingdom(player);
                                                                    if (kingdom == null) return error(context, "You are not in a kingdom");

                                                                    kingdom.setFalling(true);
                                                                    kingdom.markDirty();
                                                                    context.getSource().sendFeedback(() -> Text.literal("§cKingdom manually set to falling state"), false);
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                                .then(literal("reclaim")
                                                        .executes(context -> {
                                                            ServerPlayerEntity player = context.getSource().getPlayer();
                                                            Kingdom kingdom = Kingdom.getPlayerKingdom(player);
                                                            if (kingdom == null) return error(context, "You are not in a kingdom");
                                                            if (!kingdom.isFalling()) return error(context, "Your kingdom is not falling");

                                                            long reclaimCost = kingdom.calculateDailyUpkeep();
                                                            if (kingdom.reclaim(player.getUuid(), reclaimCost)) {
                                                                kingdom.markDirty();
                                                                return success(context, "Reclaimed kingdom for " + formatCurrency(reclaimCost));
                                                            } else {
                                                                return error(context, "Cannot reclaim kingdom - check permissions or treasury");
                                                            }
                                                        })
                                                )
                                                .then(literal("invite").then(argument("player", StringArgumentType.word())
                                                        .suggests((context, builder) -> suggestInvitablePlayers(context, builder).buildFuture())
                                                        .executes(context -> invitePlayer(context, StringArgumentType.getString(context, "player")))))
                                                .then(literal("accept").executes(context -> acceptInvite(context)))
                                                .then(literal("decline").executes(context -> declineInvite(context)))
                                                .then(literal("setrank").then(argument("player", StringArgumentType.word())
                                                        .suggests((context, builder) -> suggestMembers(context, builder).buildFuture())
                                                        .then(argument("rank", StringArgumentType.word())
                                                                .suggests((context, builder) -> suggestRanks(builder).buildFuture())
                                                                .executes(context -> setRank(context,
                                                                        StringArgumentType.getString(context, "player"),
                                                                        StringArgumentType.getString(context, "rank")))))))))));
    }

    // ==================== DIPLOMACY COMMAND METHODS ====================
    private static int showRelations(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error(context, "You are not in a kingdom");

        context.getSource().sendFeedback(() -> Text.literal("§6=== Your Kingdom's Relations ==="), false);

        // Show allies
        Set<String> allies = kingdom.getAllies();
        if (allies.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("§aAllies: §7None"), false);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("§aAllies (§e" + allies.size() + "§a): §7" + String.join(", ", allies)), false);
        }

        // Show enemies
        Set<String> enemies = kingdom.getEnemies();
        if (enemies.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("§cEnemies: §7None"), false);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("§cEnemies (§e" + enemies.size() + "§c): §7" + String.join(", ", enemies)), false);
        }

        return 1;
    }

    private static int addAlly(CommandContext<ServerCommandSource> context, String targetKingdomName) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error(context, "You are not in a kingdom");
        if (!kingdom.isOwner(player.getUuid()) && !kingdom.getRank(player.getUuid()).equals(Kingdom.RANK_ASSISTANT))
            return error(context, "Only leaders/assistants can manage allies");

        Kingdom targetKingdom = KingdomManager.getKingdom(targetKingdomName);
        if (targetKingdom == null) return error(context, "Kingdom '" + targetKingdomName + "' not found");
        if (kingdom.getName().equals(targetKingdomName)) return error(context, "Cannot ally with yourself");
        if (kingdom.isAlly(targetKingdomName)) return error(context, "Already allied with " + targetKingdomName);

        if (kingdom.addAlly(targetKingdomName)) {
            kingdom.markDirty();
            // Notify both kingdoms
            notifyKingdom(kingdom, "§aYou are now allied with " + targetKingdomName);
            notifyKingdom(targetKingdom, "§a" + kingdom.getName() + " has offered an alliance with your kingdom");
            return success(context, "Alliance offered to " + targetKingdomName);
        }
        return error(context, "Failed to add ally");
    }

    private static int removeAlly(CommandContext<ServerCommandSource> context, String targetKingdomName) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error(context, "You are not in a kingdom");
        if (!kingdom.isOwner(player.getUuid()) && !kingdom.getRank(player.getUuid()).equals(Kingdom.RANK_ASSISTANT))
            return error(context, "Only leaders/assistants can manage allies");

        if (!kingdom.isAlly(targetKingdomName)) return error(context, "Not allied with " + targetKingdomName);

        if (kingdom.removeAlly(targetKingdomName)) {
            kingdom.markDirty();
            notifyKingdom(kingdom, "§cEnded alliance with " + targetKingdomName);
            return success(context, "Ended alliance with " + targetKingdomName);
        }
        return error(context, "Failed to remove ally");
    }

    private static int listAllies(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error(context, "You are not in a kingdom");

        Set<String> allies = kingdom.getAllies();
        if (allies.isEmpty()) {
            return success(context, "Your kingdom has no allies");
        } else {
            return success(context, "Allies: " + String.join(", ", allies));
        }
    }

    private static int addEnemy(CommandContext<ServerCommandSource> context, String targetKingdomName) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error(context, "You are not in a kingdom");
        if (!kingdom.isOwner(player.getUuid()) && !kingdom.getRank(player.getUuid()).equals(Kingdom.RANK_ASSISTANT))
            return error(context, "Only leaders/assistants can manage enemies");

        Kingdom targetKingdom = KingdomManager.getKingdom(targetKingdomName);
        if (targetKingdom == null) return error(context, "Kingdom '" + targetKingdomName + "' not found");
        if (kingdom.getName().equals(targetKingdomName)) return error(context, "Cannot declare yourself as enemy");
        if (kingdom.isEnemy(targetKingdomName)) return error(context, "Already enemies with " + targetKingdomName);

        if (kingdom.addEnemy(targetKingdomName)) {
            kingdom.markDirty();
            // Notify both kingdoms
            notifyKingdom(kingdom, "§cYou have declared " + targetKingdomName + " as an enemy");
            notifyKingdom(targetKingdom, "§c" + kingdom.getName() + " has declared your kingdom as an enemy!");
            return success(context, "Declared " + targetKingdomName + " as an enemy");
        }
        return error(context, "Failed to add enemy");
    }

    private static int removeEnemy(CommandContext<ServerCommandSource> context, String targetKingdomName) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error(context, "You are not in a kingdom");
        if (!kingdom.isOwner(player.getUuid()) && !kingdom.getRank(player.getUuid()).equals(Kingdom.RANK_ASSISTANT))
            return error(context, "Only leaders/assistants can manage enemies");

        if (!kingdom.isEnemy(targetKingdomName)) return error(context, "Not enemies with " + targetKingdomName);

        if (kingdom.removeEnemy(targetKingdomName)) {
            kingdom.markDirty();
            notifyKingdom(kingdom, "§aRemoved " + targetKingdomName + " from enemies");
            return success(context, "Removed " + targetKingdomName + " from enemies");
        }
        return error(context, "Failed to remove enemy");
    }

    private static int listEnemies(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error(context, "You are not in a kingdom");

        Set<String> enemies = kingdom.getEnemies();
        if (enemies.isEmpty()) {
            return success(context, "Your kingdom has no enemies");
        } else {
            return success(context, "Enemies: " + String.join(", ", enemies));
        }
    }

    private static void notifyKingdom(Kingdom kingdom, String message) {
        MinecraftServer server = KingdomManager.getServer();
        if (server != null) {
            kingdom.getMembers().keySet().forEach(id -> {
                ServerPlayerEntity member = server.getPlayerManager().getPlayer(id);
                if (member != null) {
                    member.sendMessage(Text.literal("§6[Kingdom] " + message), false);
                }
            });
        }
    }

    private static ServerPlayerEntity getServerPlayer(UUID playerId) {
        // This is a helper method to get a player from any available server reference
        // We'll need to find a way to get the server instance
        // For now, let's modify this to work with the existing code
        return null; // This will be implemented based on available context
    }

    // ==================== DIPLOMACY SUGGESTIONS ====================
    private static com.mojang.brigadier.suggestion.SuggestionsBuilder suggestOtherKingdoms(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            Kingdom playerKingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
            if (playerKingdom != null) {
                KingdomManager.getAllKingdoms().stream()
                        .filter(k -> !k.getName().equals(playerKingdom.getName()))
                        .forEach(k -> builder.suggest(k.getName()));
            }
        }
        return builder;
    }

    private static com.mojang.brigadier.suggestion.SuggestionsBuilder suggestAllies(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
            if (kingdom != null) {
                kingdom.getAllies().forEach(builder::suggest);
            }
        }
        return builder;
    }

    private static com.mojang.brigadier.suggestion.SuggestionsBuilder suggestEnemies(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
            if (kingdom != null) {
                kingdom.getEnemies().forEach(builder::suggest);
            }
        }
        return builder;
    }

    private static long parseCurrencyAmount(String input) {
        if (input == null || input.isEmpty()) return 0;

        input = input.toLowerCase().trim();
        try {
            if (input.endsWith("g")) {
                return Long.parseLong(input.substring(0, input.length() - 1)) * 10000;
            } else if (input.endsWith("s")) {
                return Long.parseLong(input.substring(0, input.length() - 1)) * 100;
            } else if (input.endsWith("b")) {
                return Long.parseLong(input.substring(0, input.length() - 1));
            } else {
                // Default to bronze if no suffix
                return Long.parseLong(input);
            }
        } catch (NumberFormatException e) {
            return -1; // Invalid format
        }
    }

    private static String getCurrencySuffix(String input) {
        if (input == null || input.isEmpty()) return "b";

        input = input.toLowerCase().trim();
        if (input.endsWith("g")) return "g";
        if (input.endsWith("s")) return "s";
        if (input.endsWith("b")) return "b";
        return "b"; // Default to bronze
    }

    // ==================== TREASURY COMMANDS ====================
    private static int depositToTreasury(CommandContext<ServerCommandSource> context, long amount) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = Kingdom.getPlayerKingdom(player);
        if (kingdom == null) return error(context, "You are not in a kingdom");

        // Check if player has enough total value
        long playerBalance = InventoryCoins.countCoins(player);
        if (playerBalance < amount) return error(context, "You don't have enough money");

        // Remove the exact amount
        long actualPaid = InventoryCoins.removeCoins(player, amount);

        kingdom.deposit(actualPaid);
        kingdom.addTaxContribution(player.getUuid(), actualPaid);
        kingdom.markDirty();

        return success(context, "Deposited " + formatCurrency(actualPaid) + " to treasury");
    }

    private static int withdrawFromTreasury(CommandContext<ServerCommandSource> context, long amount) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = Kingdom.getPlayerKingdom(player);
        if (kingdom == null) return error(context, "You are not in a kingdom");
        if (!kingdom.isOwner(player.getUuid()) && !kingdom.getRank(player.getUuid()).equals(Kingdom.RANK_ASSISTANT))
            return error(context, "Only leaders/assistants can withdraw");
        if (kingdom.getTreasury() < amount) return error(context, "Treasury doesn't have enough money");
        if (!kingdom.withdraw(amount)) return error(context, "Failed to withdraw money");

        // Use InventoryCoins to add coins to player (same as change in chunk claiming)
        InventoryCoins.addCoins(player, amount);
        kingdom.markDirty();

        return success(context, "Withdrew " + formatCurrency(amount) + " from treasury");
    }

    private static int checkTreasuryBalance(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = Kingdom.getPlayerKingdom(player);
        if (kingdom == null) return error(context, "You are not in a kingdom");

        context.getSource().sendFeedback(() -> Text.literal("Treasury: " + formatCurrency(kingdom.getTreasury())), false);
        context.getSource().sendFeedback(() -> Text.literal("Upkeep: " + formatCurrency(kingdom.calculateDailyUpkeep())), false);
        context.getSource().sendFeedback(() -> Text.literal("Can afford: " + kingdom.canAffordUpkeep()), false);
        return 1;
    }

    private static int checkUpkeepCost(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = Kingdom.getPlayerKingdom(player);
        if (kingdom == null) return error(context, "You are not in a kingdom");

        context.getSource().sendFeedback(() -> Text.literal("Daily: " + formatCurrency(kingdom.calculateDailyUpkeep())), false);
        context.getSource().sendFeedback(() -> Text.literal("Base: " + formatCurrency(kingdom.getSettings().getBaseUpkeep())), false);
        context.getSource().sendFeedback(() -> Text.literal("Per-claim: " + formatCurrency(kingdom.getSettings().getClaimUpkeep())), false);
        context.getSource().sendFeedback(() -> Text.literal("Claims: " + kingdom.getClaimCount()), false);
        return 1;
    }

    // ==================== CORE COMMANDS ====================
    private static int createKingdom(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        boolean success = KingdomManager.createKingdom(player, name, KINGDOM_COST);
        return success ? success(context, "Kingdom created!") : error(context, "Failed to create kingdom");
    }

    private static int leaveKingdom(CommandContext<ServerCommandSource> context) {
        return KingdomManager.leaveKingdom(context.getSource().getPlayer()) ?
                success(context, "Left kingdom") : error(context, "Cannot leave kingdom");
    }

    private static int deleteKingdom(CommandContext<ServerCommandSource> context) {
        return KingdomManager.deleteKingdom(context.getSource().getPlayer()) ? 1 : 0;
    }

    private static int confirmDelete(CommandContext<ServerCommandSource> context) {
        return KingdomManager.confirmDeleteKingdom(context.getSource().getPlayer()) ?
                success(context, "Kingdom deleted") : error(context, "Deletion failed");
    }

    private static int listKingdoms(CommandContext<ServerCommandSource> context) {
        KingdomManager.listKingdoms(context.getSource().getPlayer());
        return 1;
    }

    private static int kingdomInfo(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) return error(context, "Not in a kingdom");

        player.sendMessage(Text.of("§6=== " + kingdom.getName() + " Info ==="), false);
        player.sendMessage(Text.of("§bOwner: §a" + getPlayerName(kingdom.getOwner(), player.getServer())), false);
        player.sendMessage(Text.of("§bMembers: §e" + kingdom.getMembers().size()), false);
        player.sendMessage(Text.of("§bClaims: §6" + kingdom.getClaimedChunks().size()), false);
        player.sendMessage(Text.of("§bYour Rank: §d" + kingdom.getRank(player)), false);

        // ADD FALLING STATE INFO HERE:
        if (kingdom.isFalling()) {
            long timeFallen = System.currentTimeMillis() - kingdom.getFallingStartTime();
            long hoursLeft = (86400000 - timeFallen) / (60 * 60 * 1000);
            long hoursFallen = timeFallen / (60 * 60 * 1000);

            player.sendMessage(Text.of("§c⚔ FALLING STATE ⚔"), false);
            player.sendMessage(Text.of("§cTime fallen: §6" + hoursFallen + " hours"), false);
            player.sendMessage(Text.of("§cTime until dissolution: §6" + hoursLeft + " hours"), false);
            player.sendMessage(Text.of("§cReclaim cost: §6" + formatCurrency(kingdom.calculateDailyUpkeep())), false);

            if (hoursFallen <= 12) {
                player.sendMessage(Text.of("§eOnly the leader can reclaim now"), false);
                player.sendMessage(Text.of("§eUse: §a/kingdom reclaim"), false);
            } else {
                player.sendMessage(Text.of("§eAny member can reclaim now"), false);
                player.sendMessage(Text.of("§eUse: §a/kingdom reclaim"), false);
            }
        }

        return 1;
    }

    // ==================== SOCIAL COMMANDS ====================
    private static int invitePlayer(CommandContext<ServerCommandSource> context, String targetName) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        ServerPlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) return error(context, "Player not found");

        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
        if (kingdom == null) return error(context, "Not in a kingdom");

        return KingdomManager.invitePlayer(kingdom, target, executor) ?
                success(context, "Invited " + targetName) : error(context, "Invite failed");
    }

    private static int acceptInvite(CommandContext<ServerCommandSource> context) {
        return KingdomManager.acceptInvite(context.getSource().getPlayer()) ?
                success(context, "Invite accepted") : error(context, "No valid invite");
    }

    private static int declineInvite(CommandContext<ServerCommandSource> context) {
        return KingdomManager.declineInvite(context.getSource().getPlayer()) ?
                success(context, "Invite declined") : error(context, "No invite to decline");
    }

    private static int setRank(CommandContext<ServerCommandSource> context, String targetName, String rank) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        ServerPlayerEntity target = executor.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) return error(context, "Player not found");

        Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
        if (kingdom == null) return error(context, "Not in a kingdom");

        boolean assigned = KingdomManager.assignRank(kingdom, target, rank, executor);
        return assigned ? success(context, targetName + " is now " + rank) : error(context, "Rank assignment failed");
    }

    // ==================== UTILITIES ====================
    private static com.mojang.brigadier.suggestion.SuggestionsBuilder suggestInvitablePlayers(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {

        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor != null) {
            Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
            if (kingdom != null) {
                executor.getServer().getPlayerManager().getPlayerList().stream()
                        .filter(player -> !kingdom.isMember(player) && !player.getUuid().equals(executor.getUuid()))
                        .forEach(player -> builder.suggest(player.getName().getString()));
            }
        }
        return builder;
    }

    private static com.mojang.brigadier.suggestion.SuggestionsBuilder suggestMembers(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {

        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor != null) {
            Kingdom kingdom = KingdomManager.getKingdomOfPlayer(executor.getUuid());
            if (kingdom != null) {
                executor.getServer().getPlayerManager().getPlayerList().stream()
                        .filter(kingdom::isMember)
                        .forEach(player -> builder.suggest(player.getName().getString()));
            }
        }
        return builder;
    }

    private static com.mojang.brigadier.suggestion.SuggestionsBuilder suggestRanks(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {

        builder.suggest("leader").suggest("assistant").suggest("officer").suggest("member").suggest("ally");
        return builder;
    }

    private static String getPlayerName(UUID playerId, net.minecraft.server.MinecraftServer server) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        return player != null ? player.getName().getString() : "Unknown";
    }

    private static String formatCurrency(long amount) {
        long gold = amount / 10000, silver = (amount % 10000) / 100, bronze = amount % 100;
        if (gold > 0) return String.format("%d gold, %d silver, %d bronze", gold, silver, bronze);
        if (silver > 0) return String.format("%d silver, %d bronze", silver, bronze);
        return String.format("%d bronze", bronze);
    }

    private static int success(CommandContext<ServerCommandSource> context, String message) {
        context.getSource().sendFeedback(() -> Text.literal("§a" + message), false);
        return 1;
    }

    private static int error(CommandContext<ServerCommandSource> context, String message) {
        context.getSource().sendError(Text.literal("§c" + message));
        return 0;
    }
}