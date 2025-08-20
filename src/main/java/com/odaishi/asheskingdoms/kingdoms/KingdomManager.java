package com.odaishi.asheskingdoms.kingdoms;

import com.odaishi.asheskingdoms.AshesKingdoms;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
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

    // ==========================================================
    // === SAVE / LOAD TO FILE =================================
    // ==========================================================
    public static void saveToFile(File file) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<JsonElement> jsonKingdoms = new ArrayList<>();
        for (Kingdom k : kingdoms.values()) {
            jsonKingdoms.add(k.toJson());
        }
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(jsonKingdoms, writer);
        }
    }

    public static void loadFromFile(File file) throws IOException {
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
        AshesKingdoms.getInstance().saveData();
        return true;
    }

    // ==========================================================
    // === ECONOMY HELPERS =====================================
    // ==========================================================
    public static boolean hasCoins(ServerPlayerEntity player, long amount) {
        return AshesKingdoms.noApi != null && AshesKingdoms.noApi.getBalance(player.getUuid()) >= amount;
    }

    public static boolean takeCoins(ServerPlayerEntity player, long amount) {
        return AshesKingdoms.noApi != null && AshesKingdoms.noApi.tryRemove(player.getUuid(), amount);
    }

    // ==========================================================
    // === KINGDOM CREATION & CLAIMING ==========================
    // ==========================================================
    public static boolean createKingdom(ServerPlayerEntity player, String name, long creationCost) {
        if (kingdoms.containsKey(name)) {
            player.sendMessage(Text.of("A kingdom with that name already exists!"), false);
            return false;
        }

        if (!hasCoins(player, creationCost)) {
            player.sendMessage(Text.of("You do not have enough coins to create this kingdom."), false);
            return false;
        }

        takeCoins(player, creationCost);

        Kingdom kingdom = new Kingdom(name, player, new ChunkPos(player.getBlockPos()));
        kingdoms.put(name, kingdom);
        claimedChunks.addAll(kingdom.getClaimedChunks());

        AshesKingdoms.getInstance().saveData();

        player.getServer().getPlayerManager().broadcast(Text.of(
                player.getName().getString() + " founded the kingdom of " + name + "!"
        ), false);

        return true;
    }

    public static Kingdom getKingdom(String name) { return kingdoms.get(name); }

    public static Kingdom getKingdomAt(ChunkPos chunk) {
        return kingdoms.values().stream().filter(k -> k.getClaimedChunks().contains(chunk)).findFirst().orElse(null);
    }

    public static boolean addMember(Kingdom kingdom, ServerPlayerEntity newPlayer, String rank, ServerPlayerEntity executor) {
        if (!kingdom.hasPermission(executor, "addMember")) return false;
        kingdom.addMember(newPlayer, rank);
        AshesKingdoms.getInstance().saveData();
        return true;
    }

    public static boolean assignRank(Kingdom kingdom, ServerPlayerEntity target, String rank, ServerPlayerEntity executor) {
        if (!kingdom.isOwner(executor)) return false;
        kingdom.setRank(target, rank);
        AshesKingdoms.getInstance().saveData();
        return true;
    }

    public static boolean claimChunk(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) { player.sendMessage(Text.of("You are not in a kingdom!"), false); return false; }
        if (!kingdom.hasPermission(player, "claim")) { player.sendMessage(Text.of("You do not have permission to claim chunks."), false); return false; }

        ChunkPos chunk = new ChunkPos(player.getBlockPos());
        if (isClaimed(chunk)) { player.sendMessage(Text.of("This chunk is already claimed by another kingdom."), false); return false; }
        if (kingdom.getClaimCount() >= 25) { player.sendMessage(Text.of("Your kingdom has reached the maximum claim limit."), false); return false; }
        if (!kingdom.getClaimedChunks().isEmpty() && !kingdom.isAdjacent(chunk)) { player.sendMessage(Text.of("New claims must be adjacent to your kingdom's territory."), false); return false; }

        if (!kingdom.getClaimedChunks().isEmpty()) {
            if (!hasCoins(player, 1000)) { player.sendMessage(Text.of("You need 1000 bronze to claim this chunk."), false); return false; }
            takeCoins(player, 1000);
        }

        kingdom.claimChunk(chunk);
        claimedChunks.add(chunk);
        AshesKingdoms.getInstance().saveData();
        player.sendMessage(Text.of("Successfully claimed this chunk for kingdom " + kingdom.getName() + "!"), false);
        return true;
    }
}
