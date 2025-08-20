package com.odaishi.asheskingdoms.kingdoms;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class Kingdom {
    private final String name;
    private final UUID owner;
    private final Map<UUID, String> members; // player UUID -> rank
    private final Set<ChunkPos> claimedChunks;

    public Kingdom(String name, PlayerEntity owner, ChunkPos startingChunk) {
        this.name = name;
        this.owner = owner.getUuid();
        this.members = new HashMap<>();
        this.claimedChunks = new HashSet<>();

        // First claimed chunk
        this.addClaim(startingChunk);

        // Owner automatically gets "leader" rank
        members.put(owner.getUuid(), "leader");
    }

    // Alternate constructor for loading
    public Kingdom(String name, UUID owner, Map<UUID, String> members, Set<ChunkPos> claims) {
        this.name = name;
        this.owner = owner;
        this.members = new HashMap<>(members);
        this.claimedChunks = new HashSet<>(claims);
    }

    /* -------------------- CLAIMS -------------------- */

    /** Check if a given chunk is directly adjacent (N, S, E, W) to an already claimed chunk. */
    public boolean isAdjacent(ChunkPos newChunk) {
        for (ChunkPos claimed : claimedChunks) {
            if (Math.abs(claimed.x - newChunk.x) + Math.abs(claimed.z - newChunk.z) == 1) {
                return true;
            }
        }
        return false;
    }

    /** @return how many chunks this kingdom has claimed */
    public int getClaimCount() {
        return claimedChunks.size();
    }

    /** Try to claim a chunk */
    public boolean claimChunk(ChunkPos chunk) {
        return claimedChunks.add(chunk);
    }

    /** Force-add a claim (no checks) */
    public void addClaim(ChunkPos chunk) {
        claimedChunks.add(chunk);
    }

    /** ✅ Fix: method alias for external calls */
    public Set<ChunkPos> getClaims() {
        return claimedChunks;
    }

    public Set<ChunkPos> getClaimedChunks() {
        return claimedChunks;
    }

    /* -------------------- MEMBERS -------------------- */

    public Map<UUID, String> getMembers() {
        return members;
    }

    public boolean addMember(PlayerEntity player, String rank) {
        if (members.containsKey(player.getUuid())) return false;
        members.put(player.getUuid(), rank);
        return true;
    }

    public boolean removeMember(PlayerEntity player) {
        return members.remove(player.getUuid()) != null;
    }

    public boolean setRank(PlayerEntity player, String rank) {
        if (!members.containsKey(player.getUuid())) return false;
        members.put(player.getUuid(), rank);
        return true;
    }

    public String getRank(PlayerEntity player) {
        return members.get(player.getUuid());
    }

    public boolean isOwner(PlayerEntity player) {
        return player.getUuid().equals(owner);
    }

    public boolean hasPermission(PlayerEntity player, String requiredRank) {
        String rank = getRank(player);
        if (rank == null) return false;
        if (rank.equals("leader")) return true;
        return rank.equals(requiredRank);
    }

    /* -------------------- BASICS -------------------- */

    public String getName() { return name; }
    public UUID getOwner() { return owner; }

    /* -------------------- UTILS -------------------- */

    /** Find the kingdom a player belongs to */
    public static Kingdom getPlayerKingdom(PlayerEntity player) {
        UUID playerUUID = player.getUuid();
        for (Kingdom kingdom : KingdomManager.getAllKingdoms()) { // ✅ FIXED: call proper accessor
            if (kingdom.getMembers().containsKey(playerUUID)) {
                return kingdom;
            }
        }
        return null; // player is not in any kingdom
    }

    /* -------------------- SAVING & LOADING -------------------- */

    // Convert this kingdom into JSON
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("owner", owner.toString());

        // Members
        JsonObject membersJson = new JsonObject();
        for (Map.Entry<UUID, String> entry : members.entrySet()) {
            membersJson.addProperty(entry.getKey().toString(), entry.getValue());
        }
        obj.add("members", membersJson);

        // Claims
        JsonArray claimsArray = new JsonArray();
        for (ChunkPos pos : claimedChunks) {
            JsonObject claim = new JsonObject();
            claim.addProperty("x", pos.x);
            claim.addProperty("z", pos.z);
            claimsArray.add(claim);
        }
        obj.add("claims", claimsArray);

        return obj;
    }

    // Create a Kingdom from JSON
    public static Kingdom fromJson(JsonObject obj) {
        String name = obj.get("name").getAsString();
        UUID owner = UUID.fromString(obj.get("owner").getAsString());

        // Members
        Map<UUID, String> members = new HashMap<>();
        JsonObject membersJson = obj.getAsJsonObject("members");
        for (String key : membersJson.keySet()) {
            members.put(UUID.fromString(key), membersJson.get(key).getAsString());
        }

        // Claims
        Set<ChunkPos> claims = new HashSet<>();
        JsonArray claimsArray = obj.getAsJsonArray("claims");
        claimsArray.forEach(el -> {
            JsonObject claim = el.getAsJsonObject();
            int x = claim.get("x").getAsInt();
            int z = claim.get("z").getAsInt();
            claims.add(new ChunkPos(x, z));
        });

        return new Kingdom(name, owner, members, claims);
    }
}
