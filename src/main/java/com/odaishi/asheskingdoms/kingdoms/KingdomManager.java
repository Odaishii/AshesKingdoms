/**
 * KINGDOM MANAGEMENT SYSTEM - ASHES KINGDOMS MOD
 *
 * This class serves as the central manager for all kingdom-related operations including:
 * - Kingdom creation, deletion, and persistence
 * - Chunk claiming and territory management
 * - Player membership and rank management
 * - Invitation system with expiration handling
 * - Economy integration with coin-based transactions
 * - Personal claim system for individual player permissions
 * - Settings management for kingdom customization
 *
 * The system maintains:
 * - Global registry of all kingdoms
 * - Chunk ownership mapping
 * - Player-kingdom associations
 * - Pending invitations and deletion confirmations
 *
 * Data is persisted to JSON format and automatically saved/loaded from disk.
 * All operations are server-side only and thread-safe for multiplayer environments.
 */



package com.odaishi.asheskingdoms.kingdoms;

import com.odaishi.asheskingdoms.utils.InventoryCoins;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.*;
import java.util.*;

public class KingdomManager {

    private static final Map<UUID, Kingdom> playerKingdoms = new HashMap<>();
    private static final Set<ChunkPos> claimedChunks = new HashSet<>();
    public static final Map<String, Kingdom> kingdoms = new HashMap<>();
    private static MinecraftServer server;

    // Invitation system
    private static final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();
    private static final Map<UUID, PendingDeletion> pendingDeletions = new HashMap<>();

    public static class PendingInvite {
        public final UUID kingdomId;
        public final String kingdomName;
        public final long expiryTime;

        public PendingInvite(UUID kingdomId, String kingdomName, String rankMember) {
            this.kingdomId = kingdomId;
            this.kingdomName = kingdomName;
            this.expiryTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes
        }
    }

    // Deletion confirmation class
    public static class PendingDeletion {
        public final String kingdomName;
        public final long expiryTime;

        public PendingDeletion(String kingdomName) {
            this.kingdomName = kingdomName;
            this.expiryTime = System.currentTimeMillis() + (30 * 1000); // 30 seconds
        }
    }

    public static void setServer(MinecraftServer server) {
        KingdomManager.server = server;
    }

    // ==========================================================
    // === SAVE / LOAD TO FILE =================================
    // ==========================================================
    public static void saveToFile() throws IOException {
        if (server == null) return;

        File worldDir = server.getRunDirectory().toFile();
        File dataDir = new File(worldDir, "asheskingdoms");
        File file = new File(dataDir, "kingdoms.json");

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

        File worldDir = server.getRunDirectory().toFile();
        File dataDir = new File(worldDir, "asheskingdoms");
        File file = new File(dataDir, "kingdoms.json");

        if (!file.exists()) return;

        kingdoms.clear();
        claimedChunks.clear();
        pendingInvites.clear();
        pendingDeletions.clear();

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
        if (isClaimed(pos)) return  false;
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
    // === INVITATION SYSTEM ====================================
    // ==========================================================
    public static boolean invitePlayer(Kingdom kingdom, ServerPlayerEntity targetPlayer, ServerPlayerEntity executor) {
        if (!kingdom.hasPermission(executor, "invite")) {
            executor.sendMessage(Text.of("§cYou don't have permission to invite players!"), false);
            return false;
        }

        if (kingdom.isMember(targetPlayer)) {
            executor.sendMessage(Text.of("§c" + targetPlayer.getName().getString() + " is already a member!"), false);
            return false;
        }

        // Create and store invitation - default rank is "member"
        PendingInvite invite = new PendingInvite(kingdom.getOwner(), kingdom.getName(), Kingdom.RANK_MEMBER);
        pendingInvites.put(targetPlayer.getUuid(), invite);

        // Send messages
        executor.sendMessage(Text.of("§aInvited " + targetPlayer.getName().getString() + " to join " + kingdom.getName()), false);
        targetPlayer.sendMessage(Text.of("§6You've been invited to join " + kingdom.getName() + "!"), false);
        targetPlayer.sendMessage(Text.of("§6Type §a/kingdom accept §6to join or §c/kingdom decline §6to decline."), false);

        return true;
    }

    public static boolean acceptInvite(ServerPlayerEntity player) {
        PendingInvite invite = pendingInvites.get(player.getUuid());

        if (invite == null) {
            player.sendMessage(Text.of("§cYou don't have any pending invitations!"), false);
            return false;
        }

        if (System.currentTimeMillis() > invite.expiryTime) {
            pendingInvites.remove(player.getUuid());
            player.sendMessage(Text.of("§cYour invitation has expired!"), false);
            return false;
        }

        Kingdom kingdom = getKingdom(invite.kingdomName);
        if (kingdom == null) {
            player.sendMessage(Text.of("§cThe kingdom no longer exists!"), false);
            return false;
        }

        // Add player to kingdom with default "member" rank
        kingdom.addMember(player, Kingdom.RANK_MEMBER);
        pendingInvites.remove(player.getUuid());
        setPlayerKingdom(player.getUuid(), kingdom);

        try {
            saveToFile();
        } catch (IOException e) {
            player.sendMessage(Text.of("§cFailed to save kingdom data: " + e.getMessage()), false);
        }

        player.sendMessage(Text.of("§aYou've joined " + kingdom.getName() + " as a member!"), false);

        // Notify kingdom members
        if (player.getServer() != null) {
            for (UUID memberId : kingdom.getMembers().keySet()) {
                ServerPlayerEntity member = player.getServer().getPlayerManager().getPlayer(memberId);
                if (member != null && !member.getUuid().equals(player.getUuid())) {
                    member.sendMessage(Text.of("§a" + player.getName().getString() + " has joined the kingdom!"), false);
                }
            }
        }

        return true;
    }

    public static boolean declineInvite(ServerPlayerEntity player) {
        PendingInvite invite = pendingInvites.remove(player.getUuid());

        if (invite == null) {
            player.sendMessage(Text.of("§cYou don't have any pending invitations!"), false);
            return false;
        }

        player.sendMessage(Text.of("§cYou declined the invitation to join " + invite.kingdomName), false);
        return true;
    }

    // ==========================================================
    // === DELETION CONFIRMATION ================================
    // ==========================================================
    public static boolean confirmDeleteKingdom(ServerPlayerEntity player) {
        PendingDeletion pending = pendingDeletions.get(player.getUuid());

        if (pending == null) {
            player.sendMessage(Text.of("§cYou don't have a kingdom deletion pending!"), false);
            return false;
        }

        if (System.currentTimeMillis() > pending.expiryTime) {
            pendingDeletions.remove(player.getUuid());
            player.sendMessage(Text.of("§cDeletion confirmation has expired!"), false);
            return false;
        }

        // Actually delete the kingdom
        Kingdom kingdom = getKingdom(pending.kingdomName);
        if (kingdom != null && kingdom.isOwner(player)) {
            return actuallyDeleteKingdom(player, kingdom);
        }

        pendingDeletions.remove(player.getUuid());
        return false;
    }

    private static boolean actuallyDeleteKingdom(ServerPlayerEntity player, Kingdom kingdom) {
        claimedChunks.removeAll(kingdom.getClaimedChunks());
        kingdoms.remove(kingdom.getName());
        pendingDeletions.remove(player.getUuid());

        for (UUID memberId : kingdom.getMembers().keySet()) {
            setPlayerKingdom(memberId, null);
        }

        try {
            saveToFile();
        } catch (IOException e) {
            player.sendMessage(Text.of("§cFailed to save kingdom data: " + e.getMessage()), false);
            return false;
        }

        if (player.getServer() != null) {
            player.getServer().getPlayerManager().broadcast(Text.of(
                    "§cThe kingdom of " + kingdom.getName() + " has been disbanded by " + player.getName().getString() + "!"
            ), false);
        }

        player.sendMessage(Text.of("§aYour kingdom has been deleted."), false);
        return true;
    }

    // Modified deleteKingdom method with confirmation
    public static boolean deleteKingdom(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in any kingdom!"), false);
            return false;
        }

        if (!kingdom.isOwner(player)) {
            player.sendMessage(Text.of("§cOnly the kingdom owner can delete the kingdom!"), false);
            return false;
        }

        // Add confirmation requirement
        pendingDeletions.put(player.getUuid(), new PendingDeletion(kingdom.getName()));
        player.sendMessage(Text.of("§cWARNING: This will permanently delete your kingdom and all claims!"), false);
        player.sendMessage(Text.of("§cType §6/kingdom confirm delete §cto confirm deletion."), false);
        player.sendMessage(Text.of("§cThis action will expire in 30 seconds."), false);

        return true; // Not actually deleted yet, just pending confirmation
    }

    // ==========================================================
    // === ECONOMY HELPERS =====================================
    // ==========================================================
    public static boolean hasCoins(ServerPlayerEntity player, long amount) {
        return InventoryCoins.countCoins(player) >= amount;
    }

    public static boolean takeCoins(ServerPlayerEntity player, long amount) {
        if (!hasCoins(player, amount)) return false;
        long actualPaid = InventoryCoins.removeCoins(player, amount);
        return actualPaid >= amount;
    }

    // ==========================================================
    // === KINGDOM CREATION & CLAIMING ==========================
    // ==========================================================
    public static boolean leaveKingdom(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) {
            player.sendMessage(Text.of("§cYou are not in any kingdom!"), false);
            return false;
        }

        if (kingdom.isOwner(player)) {
            player.sendMessage(Text.of("§cAs the owner, you cannot leave. Use /kingdom delete to disband or /kingdom transfer to give ownership."), false);
            return false;
        }

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
        Kingdom existingKingdom = getKingdomOfPlayer(player.getUuid());
        if (existingKingdom != null) {
            player.sendMessage(Text.of("§cYou are already a member of " + existingKingdom.getName() + "! Leave your current kingdom first."), false);
            return false;
        }

        if (kingdoms.containsKey(name)) {
            player.sendMessage(Text.of("§cA kingdom with that name already exists!"), false);
            return false;
        }

        if (!hasCoins(player, creationCost)) {
            player.sendMessage(Text.of("§cYou do not have enough coins to create this kingdom. You need " + creationCost + " bronze."), false);
            return false;
        }

        long actualPaid = InventoryCoins.removeCoins(player, creationCost);

        // If we overpaid, give change back
        if (actualPaid > creationCost) {
            long change = actualPaid - creationCost;
            InventoryCoins.addCoins(player, change);
            player.sendMessage(Text.of("§6You received " + formatCoins(change) + " in change."), false);
        }

        // If we underpaid, that's an error
        if (actualPaid < creationCost) {
            player.sendMessage(Text.of("§cPayment error: Could only pay " + formatCoins(actualPaid) + " of " + formatCoins(creationCost)), false);
            InventoryCoins.addCoins(player, actualPaid);
            return false;
        }

        Kingdom kingdom = new Kingdom(name, player, new ChunkPos(player.getBlockPos()));
        kingdoms.put(name, kingdom);
        claimedChunks.addAll(kingdom.getClaimedChunks());
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

    public static boolean assignRank(Kingdom kingdom, ServerPlayerEntity target, String rank, ServerPlayerEntity executor) {
        // Allow owner AND assistants to assign ranks
        if (!kingdom.isOwner(executor) && !kingdom.getRank(executor).equals(Kingdom.RANK_ASSISTANT)) {
            executor.sendMessage(Text.of("§cOnly the owner and assistants can assign ranks!"), false);
            return false;
        }

        if (!Kingdom.isValidRank(rank)) {
            executor.sendMessage(Text.of("§cInvalid rank! Use: leader, assistant, officer, member, ally"), false);
            return false;
        }

        // Prevent promoting someone above your own rank
        String executorRank = kingdom.getRank(executor);
        if (isRankHigher(rank, executorRank)) {
            executor.sendMessage(Text.of("§cYou cannot assign a rank higher than your own!"), false);
            return false;
        }

        kingdom.setRank(target, rank);
        try {
            saveToFile();
        } catch (IOException e) {
            executor.sendMessage(Text.of("Failed to save kingdom data: " + e.getMessage()), false);
        }
        return true;
    }

    // Helper method to check if one rank is higher than another
    private static boolean isRankHigher(String newRank, String currentRank) {
        // Rank hierarchy: leader > assistant > officer > member > ally > outsider
        Map<String, Integer> rankOrder = Map.of(
                Kingdom.RANK_LEADER, 5,
                Kingdom.RANK_ASSISTANT, 4,
                Kingdom.RANK_OFFICER, 3,
                Kingdom.RANK_MEMBER, 2,
                Kingdom.RANK_ALLY, 1,
                Kingdom.RANK_OUTSIDER, 0
        );

        return rankOrder.getOrDefault(newRank, 0) > rankOrder.getOrDefault(currentRank, 0);
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
            Kingdom chunkOwner = getKingdomAt(chunk);
            if (chunkOwner != null && chunkOwner.equals(kingdom)) {
                player.sendMessage(Text.of("§cThis chunk is already claimed by your kingdom."), false);
            } else {
                player.sendMessage(Text.of("§cThis chunk is already claimed by another kingdom."), false);
            }
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

        final long CLAIM_COST = 1000; // 10 silver = 1000 bronze

        if (!kingdom.getClaimedChunks().isEmpty()) {
            long playerCoins = InventoryCoins.countCoins(player);
            if (playerCoins < CLAIM_COST) {
                player.sendMessage(Text.of("§cYou need 10 silver to claim this chunk."), false);
                return false;
            }

            long actualPaid = InventoryCoins.removeCoins(player, CLAIM_COST);

            if (actualPaid > CLAIM_COST) {
                long change = actualPaid - CLAIM_COST;
                InventoryCoins.addCoins(player, change);
                player.sendMessage(Text.of("§6You received " + formatCoins(change) + " in change."), false);
            }

            if (actualPaid < CLAIM_COST) {
                player.sendMessage(Text.of("§cPayment error: Could only pay " + formatCoins(actualPaid) + " of " + formatCoins(CLAIM_COST)), false);
                InventoryCoins.addCoins(player, actualPaid);
                return false;
            }
        }

        if (kingdom.getClaimedChunks().contains(chunk)) {
            player.sendMessage(Text.of("§cThis chunk is already claimed by your kingdom."), false);
            return false;
        }

        boolean claimed = kingdom.claimChunk(chunk);
        if (!claimed) {
            player.sendMessage(Text.of("§cFailed to claim chunk (already claimed by your kingdom)."), false);
            return false;
        }

        claimedChunks.add(chunk);

        try {
            saveToFile();
        } catch (IOException e) {
            player.sendMessage(Text.of("§cFailed to save kingdom data: " + e.getMessage()), false);
        }

        player.sendMessage(Text.of("§aSuccessfully claimed this chunk for kingdom " + kingdom.getName() + " for 10 silver!"), false);
        return true;
    }

    // Helper method to give a specific coin item to the player
    private static void givePlayerCoinItem(ServerPlayerEntity player, String itemId, int count) {
        if (count <= 0) return;

        try {
            Identifier identifier = Identifier.of(itemId);
            net.minecraft.item.Item coinItem = Registries.ITEM.get(identifier);

            if (coinItem != null) {
                net.minecraft.item.ItemStack coinStack = new net.minecraft.item.ItemStack(coinItem, count);

                if (!player.getInventory().insertStack(coinStack)) {
                    player.dropItem(coinStack, false);
                }
            }
        } catch (Exception e) {
            // Silently fail for coin adding
        }
    }

    public static String formatCoins(long bronze) {
        long gold = bronze / 10000;
        long silver = (bronze % 10000) / 100;
        long remainingBronze = bronze % 100;

        StringBuilder sb = new StringBuilder();
        if (gold > 0) sb.append(gold).append(" gold ");
        if (silver > 0) sb.append(silver).append(" silver ");
        if (remainingBronze > 0 || sb.length() == 0) sb.append(remainingBronze).append(" bronze");
        return sb.toString().trim();
    }

    // Cleanup expired invites and deletions
    public static void cleanupExpired() {
        long now = System.currentTimeMillis();

        // Clean expired invites
        pendingInvites.entrySet().removeIf(entry -> now > entry.getValue().expiryTime);

        // Clean expired deletions
        pendingDeletions.entrySet().removeIf(entry -> now > entry.getValue().expiryTime);

        // Clean expired personal claims
        for (Kingdom kingdom : kingdoms.values()) {
            kingdom.cleanupExpiredClaims();
        }
    }
}