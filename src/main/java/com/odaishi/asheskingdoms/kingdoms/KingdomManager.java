package com.odaishi.asheskingdoms.kingdoms;

import com.odaishi.asheskingdoms.AshesKingdoms;
import com.odaishi.asheskingdoms.utils.InventoryCoins;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.*;
import java.util.*;

public class KingdomManager {

    private static final Map<UUID, Kingdom> playerKingdoms = new HashMap<>();
    private static final Set<ChunkPos> claimedChunks = new HashSet<>();
    public static final Map<String, Kingdom> kingdoms = new HashMap<>();
    private static MinecraftServer server;

    public static void setServer(MinecraftServer server) {
        KingdomManager.server = server;
    }

    // ==========================================================
    // === SAVE / LOAD TO FILE =================================
    // ==========================================================
    public static void saveToFile() throws IOException {
        if (server == null) return;

        // FIXED: Use the correct method for getting world directory
        File worldDir = server.getRunDirectory().toFile();
        File dataDir = new File(worldDir, "asheskingdoms");
        File file = new File(dataDir, "kingdoms.json");

        // Create directory if it doesn't exist
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<JsonElement> jsonKingdoms = new ArrayList<>();
        for (Kingdom k : kingdoms.values()) {
            jsonKingdoms.add(k.toJson());
        }
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(jsonKingdoms, writer);
        }
    }

    public static void loadFromFile() throws IOException {
        if (server == null) return;

        // FIXED: Use the correct method for getting world directory
        File worldDir = server.getRunDirectory().toFile();
        File dataDir = new File(worldDir, "asheskingdoms");
        File file = new File(dataDir, "kingdoms.json");

        if (!file.exists()) return;

        kingdoms.clear();
        claimedChunks.clear();

        Gson gson = new Gson();
        JsonElement root = JsonParser.parseReader(new FileReader(file));
        if (root.isJsonArray()) {
            root.getAsJsonArray().forEach(el -> {
                Kingdom k = Kingdom.fromJson(el.getAsJsonObject());
                kingdoms.put(k.getName(), k);
                claimedChunks.addAll(k.getClaimedChunks());
            });
        }
    }

    public static Collection<Kingdom> getAllKingdoms() {
        return kingdoms.values();
    }

    // ==========================================================
    // === PLAYER & CLAIM LOGIC =================================
    // ==========================================================
    public static Kingdom getKingdomOfPlayer(UUID playerId) {
        return kingdoms.values().stream()
                .filter(k -> k.getMembers().containsKey(playerId))
                .findFirst().orElse(null);
    }

    public static Kingdom getPlayerKingdom(UUID playerId) {
        return playerKingdoms.get(playerId);
    }

    public static void setPlayerKingdom(UUID playerId, Kingdom kingdom) {
        playerKingdoms.put(playerId, kingdom);
    }

    public static boolean isClaimed(ChunkPos pos) {
        return claimedChunks.contains(pos);
    }

    public static boolean claimChunkForKingdom(ServerPlayerEntity player, Kingdom kingdom, ChunkPos pos) {
        if (isClaimed(pos)) return false;
        claimedChunks.add(pos);
        kingdom.addClaim(pos);
        try {
            saveToFile();
        } catch (IOException e) {
            player.sendMessage(Text.of("Failed to save kingdom data: " + e.getMessage()), false);
        }
        return true;
    }

    // ==========================================================
    // === ECONOMY HELPERS =====================================
    // ==========================================================
    public static boolean hasCoins(ServerPlayerEntity player, long amount) {
        return InventoryCoins.countCoins(player) >= amount;
    }

    public static boolean takeCoins(ServerPlayerEntity player, long amount) {
        if (!hasCoins(player, amount)) return false;
        InventoryCoins.removeCoins(player, amount);
        return true;
    }

    // ==========================================================
    // === KINGDOM CREATION & CLAIMING ==========================
    // ==========================================================
    // Add these methods to your KingdomManager class

    public static boolean leaveKingdom(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in any kingdom!"), false);
            return false;
        }

        // Check if player is the owner (can't leave, must delete or transfer)
        if (kingdom.isOwner(player)) {
            player.sendMessage(Text.of("§cAs the owner, you cannot leave. Use /kingdom delete to disband or /kingdom transfer to give ownership."), false);
            return false;
        }

        // Remove player from kingdom
        kingdom.removeMember(player.getUuid());
        setPlayerKingdom(player.getUuid(), null);

        try {
            saveToFile();
        } catch (IOException e) {
            player.sendMessage(Text.of("§cFailed to save kingdom data: " + e.getMessage()), false);
        }

        player.sendMessage(Text.of("§aYou have left " + kingdom.getName()), false);
        return true;
    }

    public static boolean deleteKingdom(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in any kingdom!"), false);
            return false;
        }

        // Only owner can delete kingdom
        if (!kingdom.isOwner(player)) {
            player.sendMessage(Text.of("§cOnly the kingdom owner can delete the kingdom!"), false);
            return false;
        }

        // Remove all claims
        claimedChunks.removeAll(kingdom.getClaimedChunks());

        // Remove kingdom
        kingdoms.remove(kingdom.getName());

        // Clear player kingdom references
        for (UUID memberId : kingdom.getMembers().keySet()) {
            setPlayerKingdom(memberId, null);
        }

        try {
            saveToFile();
        } catch (IOException e) {
            player.sendMessage(Text.of("§cFailed to save kingdom data: " + e.getMessage()), false);
        }

        if (player.getServer() != null) {
            player.getServer().getPlayerManager().broadcast(Text.of(
                    "§cThe kingdom of " + kingdom.getName() + " has been disbanded by " + player.getName().getString() + "!"
            ), false);
        }

        return true;
    }

    public static void listKingdoms(ServerPlayerEntity player) {
        if (kingdoms.isEmpty()) {
            player.sendMessage(Text.of("§eThere are no kingdoms yet."), false);
            return;
        }

        player.sendMessage(Text.of("§6=== Kingdoms List ==="), false);
        for (Kingdom kingdom : kingdoms.values()) {
            String ownerName = "Unknown";
            ServerPlayerEntity ownerPlayer = player.getServer().getPlayerManager().getPlayer(kingdom.getOwner());
            if (ownerPlayer != null) {
                ownerName = ownerPlayer.getName().getString();
            }

            player.sendMessage(Text.of("§b" + kingdom.getName() + " §7- Owner: §a" + ownerName +
                    " §7- Members: §e" + kingdom.getMembers().size() +
                    " §7- Claims: §6" + kingdom.getClaimedChunks().size()), false);
        }
    }

    public static boolean createKingdom(ServerPlayerEntity player, String name, long creationCost) {
        // FIXED: Check if player is already in a kingdom
        Kingdom existingKingdom = getKingdomOfPlayer(player.getUuid());
        if (existingKingdom != null) {
            player.sendMessage(Text.of("§cYou are already a member of " + existingKingdom.getName() + "! Leave your current kingdom first."), false);
            return false;
        }

        if (kingdoms.containsKey(name)) {
            player.sendMessage(Text.of("§cA kingdom with that name already exists!"), false);
            return false;
        }

        // FIXED: Use InventoryCoins instead of NoApi
        if (!hasCoins(player, creationCost)) {
            player.sendMessage(Text.of("§cYou do not have enough coins to create this kingdom. You need " + creationCost + " bronze."), false);
            return false;
        }

        if (!takeCoins(player, creationCost)) {
            player.sendMessage(Text.of("§cFailed to process payment."), false);
            return false;
        }


        Kingdom kingdom = new Kingdom(name, player, new ChunkPos(player.getBlockPos()));
        kingdoms.put(name, kingdom);
        claimedChunks.addAll(kingdom.getClaimedChunks());

        // FIXED: Set player's kingdom reference
        setPlayerKingdom(player.getUuid(), kingdom);

        try {
            saveToFile();
        } catch (IOException e) {
            player.sendMessage(Text.of("§cFailed to save kingdom data: " + e.getMessage()), false);
        }

        if (player.getServer() != null) {
            player.getServer().getPlayerManager().broadcast(Text.of(
                    "§a" + player.getName().getString() + " founded the kingdom of " + name + "!"
            ), false);
        }

        return true;
    }
    public static Kingdom getKingdom(String name) {
        return kingdoms.get(name);
    }

    public static Kingdom getKingdomAt(ChunkPos chunk) {
        return kingdoms.values().stream().filter(k -> k.getClaimedChunks().contains(chunk)).findFirst().orElse(null);
    }

    public static boolean addMember(Kingdom kingdom, ServerPlayerEntity newPlayer, String rank, ServerPlayerEntity executor) {
        if (!kingdom.hasPermission(executor, "addMember")) return false;
        kingdom.addMember(newPlayer, rank);
        try {
            saveToFile();
        } catch (IOException e) {
            executor.sendMessage(Text.of("Failed to save kingdom data: " + e.getMessage()), false);
        }
        return true;
    }

    public static boolean assignRank(Kingdom kingdom, ServerPlayerEntity target, String rank, ServerPlayerEntity executor) {
        if (!kingdom.isOwner(executor)) return false;
        kingdom.setRank(target, rank);
        try {
            saveToFile();
        } catch (IOException e) {
            executor.sendMessage(Text.of("Failed to save kingdom data: " + e.getMessage()), false);
        }
        return true;
    }

    public static boolean claimChunk(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in a kingdom!"), false);
            return false;
        }
        if (!kingdom.hasPermission(player, "claim")) {
            player.sendMessage(Text.of("§cYou do not have permission to claim chunks."), false);
            return false;
        }

        ChunkPos chunk = new ChunkPos(player.getBlockPos());
        if (isClaimed(chunk)) {
            player.sendMessage(Text.of("§cThis chunk is already claimed by another kingdom."), false);
            return false;
        }
        if (kingdom.getClaimCount() >= 25) {
            player.sendMessage(Text.of("§cYour kingdom has reached the maximum claim limit."), false);
            return false;
        }
        if (!kingdom.getClaimedChunks().isEmpty() && !kingdom.isAdjacent(chunk)) {
            player.sendMessage(Text.of("§cNew claims must be adjacent to your kingdom's territory."), false);
            return false;
        }

        // FIXED: 10 Silver cost (1000 bronze in NO's internal currency)
        final long CLAIM_COST = 1000; // 10 silver = 1000 bronze in NO's system

        if (!kingdom.getClaimedChunks().isEmpty()) {
            if (!hasCoins(player, CLAIM_COST)) {
                player.sendMessage(Text.of("§cYou need 10 silver to claim this chunk."), false);
                return false;
            }
            if (!takeCoins(player, CLAIM_COST)) {
                player.sendMessage(Text.of("§cFailed to process payment."), false);
                return false;
            }
        }

        kingdom.claimChunk(chunk);
        claimedChunks.add(chunk);
        try {
            saveToFile();
        } catch (IOException e) {
            player.sendMessage(Text.of("§cFailed to save kingdom data: " + e.getMessage()), false);
        }
        player.sendMessage(Text.of("§aSuccessfully claimed this chunk for kingdom " + kingdom.getName() + " for 10 silver!"), false);
        return true;
    }
}