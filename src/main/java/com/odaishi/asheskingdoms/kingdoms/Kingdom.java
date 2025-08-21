/**
 * CORE KINGDOM DATA STRUCTURE
 *
 * Represents a player kingdom with territory, members, ranks, and governance systems.
 * Serves as the central data model for all kingdom-related operations and state management.
 *
 * DATA COMPONENTS:
 * - Identity: Name, UUID owner, home chunk location
 * - Territory: Claimed chunks with adjacency validation
 * - Membership: Player UUIDs with hierarchical ranks
 * - Governance: Settings system and permission matrix
 * - Economy: Personal claim system with ownership tracking
 *
 * RANK HIERARCHY:
 * - Leader: Full administrative control and ownership
 * - Assistant: Settings management and rank assignments
 * - Officer: Member management and territory operations
 * - Member: Basic interactions and personal claims
 * - Ally: Limited territory access permissions
 * - Outsider: No permissions (default for non-members)
 *
 * PERMISSION SYSTEM:
 * - Rank-based permission inheritance
 * - Personal claim overrides for granular access
 * - Settings-controlled kingdom-wide behaviors
 * - Adjacency rules for territorial expansion
 *
 * PERSISTENCE:
 * - JSON serialization for all kingdom data
 * - Backward compatibility with legacy save formats
 * - Automatic migration of old data structures
 * - Personal claim storage with expiration tracking
 *
 * OPERATIONAL FEATURES:
 * - Chunk claiming with adjacency validation
 * - Member invitation and rank management
 * - Settings modification with permission checks
 * - Personal claim creation and management
 * - Automatic cleanup of expired personal claims
 *
 * INTEGRATION POINTS:
 * - KingdomManager for global kingdom operations
 * - War system for inter-kingdom conflicts
 * - Economy system for claim costs and transactions
 * - Event system for protection and permission enforcement
 */

package com.odaishi.asheskingdoms.kingdoms;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class Kingdom {
    private final String name;
    private final UUID owner;
    private final Map<UUID, String> members; // player UUID -> rank name
    private final Set<ChunkPos> claimedChunks;
    private ChunkPos homeChunk;
    private final KingdomSettings settings;
    private final Map<ChunkPos, PersonalClaim> personalClaims;

    // Fixed rank names
    public static final String RANK_LEADER = "leader";
    public static final String RANK_ASSISTANT = "assistant";
    public static final String RANK_OFFICER = "officer";
    public static final String RANK_MEMBER = "member";
    public static final String RANK_ALLY = "ally";
    public static final String RANK_OUTSIDER = "outsider";

    public Kingdom(String name, PlayerEntity owner, ChunkPos startingChunk) {
        this.settings = new KingdomSettings();
        this.personalClaims = new HashMap<>();
        this.name = name;
        this.owner = owner.getUuid();
        this.members = new HashMap<>();
        this.claimedChunks = new HashSet<>();
        this.homeChunk = startingChunk;

        // First claimed chunk
        this.addClaim(startingChunk);

        // Owner automatically gets "leader" rank
        members.put(owner.getUuid(), RANK_LEADER);
    }

    // Alternate constructor for loading
    public Kingdom(String name, UUID owner, Map<UUID, String> members, Set<ChunkPos> claims, ChunkPos homeChunk, KingdomSettings settings, Map<ChunkPos, PersonalClaim> personalClaims) {
        this.name = name;
        this.owner = owner;
        this.members = new HashMap<>(members);
        this.claimedChunks = new HashSet<>(claims);
        this.homeChunk = homeChunk;
        this.settings = settings != null ? settings : new KingdomSettings();
        this.personalClaims = personalClaims != null ? new HashMap<>(personalClaims) : new HashMap<>();
    }

    // Overload for backward compatibility
    public Kingdom(String name, UUID owner, Map<UUID, String> members, Set<ChunkPos> claims, ChunkPos homeChunk) {
        this(name, owner, members, claims, homeChunk, new KingdomSettings(), new HashMap<>());
    }

    /* -------------------- SETTINGS -------------------- */
    public KingdomSettings getSettings() {
        return settings;
    }

    public boolean hasSettingPermission(UUID playerId, String setting) {
        if (isOwner(playerId)) return true;
        if (getRank(playerId).equals(RANK_ASSISTANT)) return true;
        return false; // Only owner and assistants can change settings
    }

    /* -------------------- PERSONAL CLAIMS -------------------- */
    public boolean addPersonalClaim(ChunkPos chunk, UUID playerId) {
        if (!claimedChunks.contains(chunk)) return false;
        if (personalClaims.containsKey(chunk)) return false;

        personalClaims.put(chunk, new PersonalClaim(playerId, chunk));
        return true;
    }

    public boolean removePersonalClaim(ChunkPos chunk) {
        return personalClaims.remove(chunk) != null;
    }

    public UUID getPersonalClaimOwner(ChunkPos chunk) {
        PersonalClaim claim = personalClaims.get(chunk);
        return claim != null ? claim.getPlayerId() : null;
    }

    public void cleanupExpiredClaims() {
        personalClaims.entrySet().removeIf(entry -> entry.getValue().isExpired());
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

    /**
     * Check if a player can claim a specific chunk personally
     */
    public boolean canClaimPersonally(UUID playerId, ChunkPos chunk) {
        if (!claimedChunks.contains(chunk)) return false;
        if (personalClaims.containsKey(chunk)) return false;
        if (!isMember(playerId)) return false;
        return true;
    }

    /**
     * Get all personal claims for a specific player
     */
    public Map<ChunkPos, PersonalClaim> getPersonalClaims(UUID playerId) {
        Map<ChunkPos, PersonalClaim> playerClaims = new HashMap<>();
        for (Map.Entry<ChunkPos, PersonalClaim> entry : personalClaims.entrySet()) {
            if (entry.getValue().getPlayerId().equals(playerId)) {
                playerClaims.put(entry.getKey(), entry.getValue());
            }
        }
        return playerClaims;
    }

    /**
     * Check if a player has personal claim access in a chunk
     * (includes both direct ownership and kingdom owner override)
     */
    public boolean hasPersonalClaimAccess(UUID playerId, ChunkPos chunk) {
        PersonalClaim claim = personalClaims.get(chunk);
        if (claim == null) return false;
        return claim.getPlayerId().equals(playerId) || isOwner(playerId);
    }



    /**
     * Get the number of personal claims a player has
     */
    public int getPersonalClaimCount(UUID playerId) {
        return (int) personalClaims.values().stream()
                .filter(claim -> claim.getPlayerId().equals(playerId))
                .count();
    }

    /**
     * Check if a chunk has a personal claim
     */
    public boolean hasPersonalClaim(ChunkPos chunk) {
        return personalClaims.containsKey(chunk);
    }

    /* -------------------- RANKS & PERMISSIONS -------------------- */

    public boolean hasPermission(UUID playerId, String permission) {
        String rankName = members.get(playerId);
        if (rankName == null) return false;

        return hasRankPermission(rankName, permission);
    }

    public boolean hasPermission(PlayerEntity player, String permission) {
        return hasPermission(player.getUuid(), permission);
    }

    private boolean hasRankPermission(String rankName, String permission) {
        switch (rankName) {
            case RANK_LEADER:
                return hasLeaderPermission(permission);
            case RANK_ASSISTANT:
                return hasAssistantPermission(permission);
            case RANK_OFFICER:
                return hasOfficerPermission(permission);
            case RANK_MEMBER:
                return hasMemberPermission(permission);
            case RANK_ALLY:
                return hasAllyPermission(permission);
            case RANK_OUTSIDER:
                return hasOutsiderPermission(permission);
            default:
                return false;
        }
    }

    private boolean hasLeaderPermission(String permission) {
        // Leader has all permissions
        return true;
    }

    private boolean hasAssistantPermission(String permission) {
        switch (permission) {
            case "build": case "destroy": case "switch": case "container": case "door":
            case "claim": case "invite": case "kick": case "set_home": case "unclaim":
            case "manage_ranks": case "promote":
                return true;
            default:
                return false;
        }
    }

    private boolean hasOfficerPermission(String permission) {
        switch (permission) {
            case "build": case "destroy": case "switch": case "container": case "door":
            case "claim": case "invite": case "kick":
                return true;
            default:
                return false;
        }
    }

    private boolean hasMemberPermission(String permission) {
        switch (permission) {
            case "switch": case "container": case "door":
                return true;
            default:
                return false;
        }
    }

    private boolean hasAllyPermission(String permission) {
        switch (permission) {
            case "switch": case "door":
                return true;
            default:
                return false;
        }
    }

    private boolean hasOutsiderPermission(String permission) {
        // Outsiders have no permissions by default
        return false;
    }

    public static String[] getAllRanks() {
        return new String[]{RANK_LEADER, RANK_ASSISTANT, RANK_OFFICER, RANK_MEMBER, RANK_ALLY, RANK_OUTSIDER};
    }

    public static boolean isValidRank(String rankName) {
        for (String rank : getAllRanks()) {
            if (rank.equals(rankName)) {
                return true;
            }
        }
        return false;
    }

    /* -------------------- MEMBERS -------------------- */

    public Map<UUID, String> getMembers() {
        return members;
    }

    // FIXED: Added isMember method
    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean isMember(PlayerEntity player) {
        return members.containsKey(player.getUuid());
    }

    public boolean addMember(PlayerEntity player, String rank) {
        if (members.containsKey(player.getUuid())) return false;
        if (!isValidRank(rank)) return false;
        members.put(player.getUuid(), rank);
        return true;
    }

    public boolean addMember(UUID playerId, String rank) {
        if (members.containsKey(playerId)) return false;
        if (!isValidRank(rank)) return false;
        members.put(playerId, rank);
        return true;
    }

    public boolean removeMember(UUID player) {
        return members.remove(player) != null;
    }

    public boolean removeMember(PlayerEntity player) {
        return members.remove(player.getUuid()) != null;
    }

    public boolean setRank(PlayerEntity player, String rank) {
        if (!members.containsKey(player.getUuid())) return false;
        if (!isValidRank(rank)) return false;
        members.put(player.getUuid(), rank);
        return true;
    }

    public boolean setRank(UUID playerId, String rank) {
        if (!members.containsKey(playerId)) return false;
        if (!isValidRank(rank)) return false;
        members.put(playerId, rank);
        return true;
    }

    public String getRank(PlayerEntity player) {
        return members.get(player.getUuid());
    }

    public String getRank(UUID playerId) {
        return members.get(playerId);
    }

    public boolean isOwner(PlayerEntity player) {
        return player.getUuid().equals(owner);
    }

    public boolean isOwner(UUID playerId) {
        return playerId.equals(owner);
    }

    /* -------------------- BASICS -------------------- */

    public String getName() { return name; }
    public UUID getOwner() { return owner; }
    public ChunkPos getHomeChunk() { return homeChunk; }
    public void setHomeChunk(ChunkPos homeChunk) { this.homeChunk = homeChunk; }

    /* -------------------- UTILS -------------------- */

    /** Find the kingdom a player belongs to */
    public static Kingdom getPlayerKingdom(PlayerEntity player) {
        UUID playerUUID = player.getUuid();
        for (Kingdom kingdom : KingdomManager.getAllKingdoms()) {
            if (kingdom.isMember(playerUUID)) {
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
        obj.addProperty("homeX", homeChunk.x);
        obj.addProperty("homeZ", homeChunk.z);

        // Settings
        obj.add("settings", settings.toJson());

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

        // Personal claims
        JsonObject personalClaimsObj = new JsonObject();
        for (Map.Entry<ChunkPos, PersonalClaim> entry : personalClaims.entrySet()) {
            JsonObject claimObj = new JsonObject();
            claimObj.addProperty("player", entry.getValue().getPlayerId().toString());
            claimObj.addProperty("x", entry.getKey().x);
            claimObj.addProperty("z", entry.getKey().z);
            personalClaimsObj.add(entry.getKey().x + "," + entry.getKey().z, claimObj);
        }
        obj.add("personalClaims", personalClaimsObj);

        return obj;
    }

    // Create a Kingdom from JSON
    public static Kingdom fromJson(JsonObject obj) {
        String name = obj.get("name").getAsString();
        UUID owner = UUID.fromString(obj.get("owner").getAsString());

        // Handle old save files that don't have home coordinates
        ChunkPos homeChunk;
        if (obj.has("homeX") && obj.has("homeZ")) {
            int homeX = obj.get("homeX").getAsInt();
            int homeZ = obj.get("homeZ").getAsInt();
            homeChunk = new ChunkPos(homeX, homeZ);
        } else {
            // For old save files, use the first claim as home or default to (0,0)
            JsonArray claimsArray = obj.getAsJsonArray("claims");
            if (claimsArray != null && !claimsArray.isEmpty()) {
                JsonObject firstClaim = claimsArray.get(0).getAsJsonObject();
                int x = firstClaim.get("x").getAsInt();
                int z = firstClaim.get("z").getAsInt();
                homeChunk = new ChunkPos(x, z);
            } else {
                homeChunk = new ChunkPos(0, 0); // Fallback
            }
        }

        // Settings
        KingdomSettings settings = obj.has("settings") ?
                KingdomSettings.fromJson(obj.getAsJsonObject("settings")) :
                new KingdomSettings();

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

        // Personal claims
        Map<ChunkPos, PersonalClaim> personalClaims = new HashMap<>();
        if (obj.has("personalClaims")) {
            JsonObject claimsObj = obj.getAsJsonObject("personalClaims");
            for (String key : claimsObj.keySet()) {
                JsonObject claimObj = claimsObj.getAsJsonObject(key);
                UUID playerId = UUID.fromString(claimObj.get("player").getAsString());
                int x = claimObj.get("x").getAsInt();
                int z = claimObj.get("z").getAsInt();
                personalClaims.put(new ChunkPos(x, z), new PersonalClaim(playerId, new ChunkPos(x, z)));
            }
        }

        return new Kingdom(name, owner, members, claims, homeChunk, settings, personalClaims);
    }
}