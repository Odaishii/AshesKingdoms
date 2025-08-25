package com.odaishi.asheskingdoms.kingdoms;

import com.odaishi.asheskingdoms.utils.InventoryCoins;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import java.io.*;
import java.util.*;

public class KingdomManager {
    private static final Map<UUID, Kingdom> playerKingdoms = new HashMap<>();
    private static final Set<ChunkPos> claimedChunks = new HashSet<>();
    public static final Map<String, Kingdom> kingdoms = new HashMap<>();
    private static MinecraftServer server;
    public static final KingdomManager INSTANCE = new KingdomManager();

    private static final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();
    private static final Map<UUID, PendingDeletion> pendingDeletions = new HashMap<>();
    private long upkeepTickCounter = 0;

    public record PendingInvite(UUID kingdomId, String kingdomName, long expiryTime) {
        public PendingInvite(UUID kingdomId, String kingdomName) {
            this(kingdomId, kingdomName, System.currentTimeMillis() + 300000); // 5 minutes
        }
    }

    public record PendingDeletion(String kingdomName, long expiryTime) {
        public PendingDeletion(String kingdomName) {
            this(kingdomName, System.currentTimeMillis() + 30000); // 30 seconds
        }
    }

    public static void setServer(MinecraftServer server) { KingdomManager.server = server; }

    // ==================== PERSISTENCE ====================
    public static void saveToFile() throws IOException {
        if (server == null) return;
        File file = new File(server.getRunDirectory().toFile(), "asheskingdoms/kingdoms.json");
        file.getParentFile().mkdirs();

        try (Writer writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(
                    kingdoms.values().stream().map(Kingdom::toJson).toList(), writer
            );
        }
    }



    public static void loadFromFile() throws IOException {
        if (server == null) return;
        File file = new File(server.getRunDirectory().toFile(), "asheskingdoms/kingdoms.json");
        if (!file.exists()) return;

        kingdoms.clear(); claimedChunks.clear(); pendingInvites.clear(); pendingDeletions.clear();
        JsonParser.parseReader(new FileReader(file)).getAsJsonArray().forEach(el -> {
            Kingdom k = Kingdom.fromJson(el.getAsJsonObject());
            kingdoms.put(k.getName(), k);
            claimedChunks.addAll(k.getClaimedChunks());
        });
    }

    public static Collection<Kingdom> getAllKingdoms() { return kingdoms.values(); }

    // ==================== CORE OPERATIONS ====================
    public static Kingdom getKingdomOfPlayer(UUID playerId) {
        return kingdoms.values().stream().filter(k -> k.getMembers().containsKey(playerId)).findFirst().orElse(null);
    }

    public static Kingdom getPlayerKingdom(UUID playerId) { return playerKingdoms.get(playerId); }
    public static void setPlayerKingdom(UUID playerId, Kingdom kingdom) { playerKingdoms.put(playerId, kingdom); }
    public static boolean isClaimed(ChunkPos pos) { return claimedChunks.contains(pos); }

    public static boolean claimChunkForKingdom(ServerPlayerEntity player, Kingdom kingdom, ChunkPos pos) {
        if (isClaimed(pos)) return false;
        claimedChunks.add(pos); kingdom.addClaim(pos);
        try { saveToFile(); } catch (IOException e) { player.sendMessage(Text.of("Save failed: " + e.getMessage()), false); }
        return true;
    }

    // ==================== INVITATION SYSTEM ====================
    public static boolean invitePlayer(Kingdom kingdom, ServerPlayerEntity target, ServerPlayerEntity executor) {
        if (!kingdom.hasPermission(executor, "invite")) {
            executor.sendMessage(Text.of("§cNo permission to invite!"), false); return false;
        }
        if (kingdom.isMember(target)) {
            executor.sendMessage(Text.of("§c" + target.getName().getString() + " is already a member!"), false); return false;
        }

        pendingInvites.put(target.getUuid(), new PendingInvite(kingdom.getOwner(), kingdom.getName()));
        executor.sendMessage(Text.of("§aInvited " + target.getName().getString() + " to " + kingdom.getName()), false);
        target.sendMessage(Text.of("§6Invited to " + kingdom.getName() + "! §a/kingdom accept §6or §c/kingdom decline"), false);
        return true;
    }

    public static boolean acceptInvite(ServerPlayerEntity player) {
        PendingInvite invite = pendingInvites.get(player.getUuid());
        if (invite == null || System.currentTimeMillis() > invite.expiryTime()) {
            player.sendMessage(Text.of("§cNo valid invitation!"), false); return false;
        }

        Kingdom kingdom = getKingdom(invite.kingdomName());
        if (kingdom == null) { player.sendMessage(Text.of("§cKingdom gone!"), false); return false; }

        kingdom.addMember(player, Kingdom.RANK_MEMBER); pendingInvites.remove(player.getUuid());
        setPlayerKingdom(player.getUuid(), kingdom);
        try { saveToFile(); } catch (IOException e) { player.sendMessage(Text.of("§cSave error"), false); }

        player.sendMessage(Text.of("§aJoined " + kingdom.getName() + "!"), false);
        notifyMembers(kingdom, "§a" + player.getName().getString() + " joined!");
        return true;
    }

    public static boolean declineInvite(ServerPlayerEntity player) {
        if (pendingInvites.remove(player.getUuid()) != null) {
            player.sendMessage(Text.of("§cInvitation declined"), false); return true;
        }
        player.sendMessage(Text.of("§cNo invitation!"), false); return false;
    }

    // ==================== DELETION SYSTEM ====================
    public static boolean confirmDeleteKingdom(ServerPlayerEntity player) {
        PendingDeletion pending = pendingDeletions.get(player.getUuid());
        if (pending == null || System.currentTimeMillis() > pending.expiryTime()) {
            player.sendMessage(Text.of("§cNo deletion pending!"), false); return false;
        }

        Kingdom kingdom = getKingdom(pending.kingdomName());
        if (kingdom != null && kingdom.isOwner(player)) return actuallyDeleteKingdom(player, kingdom);
        pendingDeletions.remove(player.getUuid()); return false;
    }

    private static boolean actuallyDeleteKingdom(ServerPlayerEntity player, Kingdom kingdom) {
        claimedChunks.removeAll(kingdom.getClaimedChunks()); kingdoms.remove(kingdom.getName());
        pendingDeletions.remove(player.getUuid()); kingdom.getMembers().keySet().forEach(id -> setPlayerKingdom(id, null));

        try { saveToFile(); } catch (IOException e) { player.sendMessage(Text.of("§cSave error"), false); return false; }

        broadcast("§c" + kingdom.getName() + " disbanded by " + player.getName().getString() + "!");
        player.sendMessage(Text.of("§aKingdom deleted"), false); return true;
    }

    public static boolean deleteKingdom(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) { player.sendMessage(Text.of("§cNot in kingdom!"), false); return false; }
        if (!kingdom.isOwner(player)) { player.sendMessage(Text.of("§cOnly owner can delete!"), false); return false; }

        pendingDeletions.put(player.getUuid(), new PendingDeletion(kingdom.getName()));
        player.sendMessage(Text.of("§cWARNING: Permanent deletion! §6/kingdom confirm delete §c(30s)"), false);
        return true;
    }

    // ==================== ECONOMY & KINGDOM MGMT ====================
    public static boolean hasCoins(ServerPlayerEntity player, long amount) { return InventoryCoins.countCoins(player) >= amount; }
    public static boolean takeCoins(ServerPlayerEntity player, long amount) { return hasCoins(player, amount) && InventoryCoins.removeCoins(player, amount) >= amount; }

    public static boolean leaveKingdom(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null) { player.sendMessage(Text.of("§cNot in kingdom!"), false); return false; }
        if (kingdom.isOwner(player)) { player.sendMessage(Text.of("§cOwner can't leave!"), false); return false; }

        kingdom.removeMember(player.getUuid()); setPlayerKingdom(player.getUuid(), null);
        try { saveToFile(); } catch (IOException e) { player.sendMessage(Text.of("§cSave error"), false); }
        player.sendMessage(Text.of("§aLeft " + kingdom.getName()), false); return true;
    }

    public static void listKingdoms(ServerPlayerEntity player) {
        if (kingdoms.isEmpty()) { player.sendMessage(Text.of("§eNo kingdoms"), false); return; }
        player.sendMessage(Text.of("§6=== Kingdoms ==="), false);
        kingdoms.values().forEach(k -> player.sendMessage(Text.of(formatKingdomInfo(k)), false));
    }

    public static boolean createKingdom(ServerPlayerEntity player, String name, long cost) {
        if (getKingdomOfPlayer(player.getUuid()) != null) {
            player.sendMessage(Text.of("§cAlready in kingdom!"), false); return false;
        }
        if (kingdoms.containsKey(name)) { player.sendMessage(Text.of("§cName taken!"), false); return false; }
        if (!hasCoins(player, cost)) { player.sendMessage(Text.of("§cNeed " + cost + " bronze!"), false); return false; }

        long paid = InventoryCoins.removeCoins(player, cost);
        if (paid < cost) { InventoryCoins.addCoins(player, paid); return false; }
        if (paid > cost) InventoryCoins.addCoins(player, paid - cost);

        Kingdom kingdom = new Kingdom(name, player, new ChunkPos(player.getBlockPos()));
        kingdoms.put(name, kingdom); claimedChunks.addAll(kingdom.getClaimedChunks());
        setPlayerKingdom(player.getUuid(), kingdom);
        try { saveToFile(); } catch (IOException e) { player.sendMessage(Text.of("§cSave error"), false); }

        broadcast("§a" + player.getName().getString() + " founded " + name + "!");
        return true;
    }

    public static Kingdom getKingdom(String name) { return kingdoms.get(name); }
    public static Kingdom getKingdomAt(ChunkPos chunk) { return kingdoms.values().stream().filter(k -> k.getClaimedChunks().contains(chunk)).findFirst().orElse(null); }

    public static boolean assignRank(Kingdom kingdom, ServerPlayerEntity target, String rank, ServerPlayerEntity executor) {
        if (!kingdom.isOwner(executor) && !kingdom.getRank(executor).equals(Kingdom.RANK_ASSISTANT)) {
            executor.sendMessage(Text.of("§cNo permission!"), false); return false;
        }
        if (!Kingdom.isValidRank(rank)) { executor.sendMessage(Text.of("§cInvalid rank!"), false); return false; }
        if (isRankHigher(rank, kingdom.getRank(executor))) { executor.sendMessage(Text.of("§cCannot promote higher!"), false); return false; }

        kingdom.setRank(target, rank);
        try { saveToFile(); } catch (IOException e) { executor.sendMessage(Text.of("Save error"), false); }
        return true;
    }

    // ==================== UPKEEP & CLAIMS ====================
    public void onServerTick() {
        if (++upkeepTickCounter >= 72000) { upkeepTickCounter = 0; checkAndCollectUpkeep(); }
    }

    private void checkAndCollectUpkeep() {
        long now = System.currentTimeMillis();
        for (Kingdom kingdom : getAllKingdoms()) {
            if (!kingdom.getSettings().isUpkeepEnabled()) continue;

            long lastCollection = kingdom.getLastUpkeepCollection();
            long timeSinceLastCollection = now - lastCollection;

            // Check if it's time for upkeep (24 hours)
            if (timeSinceLastCollection >= 86400000) {
                if (kingdom.processUpkeep()) {
                    // Upkeep paid successfully
                    kingdom.lastUpkeepCollection = lastCollection + 86400000;
                } else {
                    // Couldn't pay upkeep - set falling state
                    kingdom.setFalling(true);
                }
                try { saveToFile(); } catch (IOException e) { /* Log error */ }
            }

            // Check for fully fallen kingdoms (24 hours in falling state)
            if (kingdom.isFalling()) {
                long timeFallen = now - kingdom.getFallingStartTime();
                if (timeFallen >= 86400000) {
                    // Kingdom has been falling for 24 hours - delete it
                    actuallyDeleteFallenKingdom(kingdom);
                }
            }
        }
    }

    private void actuallyDeleteFallenKingdom(Kingdom kingdom) {
        claimedChunks.removeAll(kingdom.getClaimedChunks());
        kingdoms.remove(kingdom.getName());

        // Notify all members
        for (UUID memberId : kingdom.getMembers().keySet()) {
            setPlayerKingdom(memberId, null);
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
            if (member != null) {
                member.sendMessage(Text.of("§cYour kingdom " + kingdom.getName() + " has fallen due to unpaid upkeep!"), false);
            }
        }



        try { saveToFile(); } catch (IOException e) { /* Log error */ }
        broadcast("§cThe kingdom of " + kingdom.getName() + " has fallen and been dissolved!");
    }
    public static MinecraftServer getServer() {
        return server;
    }

    public static boolean claimChunk(ServerPlayerEntity player) {
        Kingdom kingdom = getKingdomOfPlayer(player.getUuid());
        if (kingdom == null || !kingdom.hasPermission(player, "claim")) {
            player.sendMessage(Text.of("§cNo permission!"), false); return false;
        }

        ChunkPos chunk = new ChunkPos(player.getBlockPos());
        if (isClaimed(chunk)) {
            player.sendMessage(Text.of("§cAlready claimed!"), false); return false;
        }
        if (kingdom.getClaimCount() >= 25) { player.sendMessage(Text.of("§cMax claims!"), false); return false; }
        if (!kingdom.getClaimedChunks().isEmpty() && !kingdom.isAdjacent(chunk)) {
            player.sendMessage(Text.of("§cNot adjacent!"), false); return false;
        }

        final long COST = 1000;
        if (!kingdom.getClaimedChunks().isEmpty()) {
            long playerCoins = InventoryCoins.countCoins(player);
            if (playerCoins < COST) {
                player.sendMessage(Text.of("§cYou need 10 silver to claim this chunk."), false);
                return false;
            }

            long actualPaid = InventoryCoins.removeCoins(player, COST);

            // GIVE CHANGE BACK if overpaid
            if (actualPaid > COST) {
                long change = actualPaid - COST;
                InventoryCoins.addCoins(player, change);
                player.sendMessage(Text.of("§6You received " + formatCoins(change) + " in change."), false);
            }

            // If underpaid, that's an error
            if (actualPaid < COST) {
                player.sendMessage(Text.of("§cPayment error: Could only pay " + formatCoins(actualPaid) + " of " + formatCoins(COST)), false);
                InventoryCoins.addCoins(player, actualPaid);
                return false;
            }
        }

        if (kingdom.claimChunk(chunk)) {
            claimedChunks.add(chunk);
            try { saveToFile(); } catch (IOException e) { player.sendMessage(Text.of("§cSave error"), false); }
            player.sendMessage(Text.of("§aClaimed for " + kingdom.getName() + "!"), false); return true;
        }
        return false;
    }

    public boolean removeMember(UUID playerId) {
        Kingdom kingdom = getKingdomOfPlayer(playerId);
        if (kingdom == null) return false;
        kingdom.revokeAllPlayerClaims(playerId);
        boolean removed = kingdom.removeMember(playerId);
        if (removed) { setPlayerKingdom(playerId, null); try { saveToFile(); } catch (IOException e) { /* Log */ } }
        return removed;
    }

    // ==================== UTILITIES ====================
    private static boolean isRankHigher(String newRank, String currentRank) {
        Map<String, Integer> rankOrder = Map.of(Kingdom.RANK_LEADER, 5, Kingdom.RANK_ASSISTANT, 4,
                Kingdom.RANK_OFFICER, 3, Kingdom.RANK_MEMBER, 2, Kingdom.RANK_ALLY, 1, Kingdom.RANK_OUTSIDER, 0);
        return rankOrder.getOrDefault(newRank, 0) > rankOrder.getOrDefault(currentRank, 0);
    }

    public static String formatCoins(long bronze) {
        long gold = bronze / 10000, silver = (bronze % 10000) / 100, rem = bronze % 100;
        return (gold > 0 ? gold + " gold " : "") + (silver > 0 ? silver + " silver " : "") + (rem > 0 || (gold == 0 && silver == 0) ? rem + " bronze" : "").trim();
    }

    private static String formatKingdomInfo(Kingdom k) {
        String owner = "Unknown";
        ServerPlayerEntity ownerPlayer = server.getPlayerManager().getPlayer(k.getOwner());
        if (ownerPlayer != null) owner = ownerPlayer.getName().getString();
        return "§b" + k.getName() + " §7- Owner: §a" + owner + " §7- Members: §e" + k.getMembers().size() + " §7- Claims: §6" + k.getClaimedChunks().size();
    }

    private static void notifyMembers(Kingdom kingdom, String message) {
        if (server != null) kingdom.getMembers().keySet().forEach(id -> {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(id);
            if (member != null) member.sendMessage(Text.of(message), false);
        });
    }

    private static void broadcast(String message) {
        if (server != null) server.getPlayerManager().broadcast(Text.of(message), false);
    }

    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(e -> now > e.getValue().expiryTime());
        pendingDeletions.entrySet().removeIf(e -> now > e.getValue().expiryTime());
        kingdoms.values().forEach(Kingdom::cleanupExpiredClaims);
    }
}