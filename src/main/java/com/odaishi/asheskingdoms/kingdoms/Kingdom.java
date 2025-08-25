package com.odaishi.asheskingdoms.kingdoms;

import com.google.gson.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import java.util.*;

public class Kingdom {
    private final String name;
    private final UUID owner;
    private final Map<UUID, String> members;
    private final Set<ChunkPos> claimedChunks;
    private ChunkPos homeChunk;
    private final KingdomSettings settings;
    private final Map<ChunkPos, PersonalClaim> personalClaims;

    private long treasury;
    public long lastUpkeepCollection;
    private Map<UUID, Long> taxContributions;

    // ==================== DIPLOMACY FIELDS ====================
    private final Set<String> allies = new HashSet<>();
    private final Set<String> enemies = new HashSet<>();

    public static final String RANK_LEADER = "leader", RANK_ASSISTANT = "assistant", RANK_OFFICER = "officer",
            RANK_MEMBER = "member", RANK_ALLY = "ally", RANK_OUTSIDER = "outsider";

    public Kingdom(String name, PlayerEntity owner, ChunkPos startingChunk) {
        this(name, owner.getUuid(), new HashMap<>(), new HashSet<>(), startingChunk, new KingdomSettings(), new HashMap<>());
        members.put(owner.getUuid(), RANK_LEADER);
        addClaim(startingChunk);
    }

    public Kingdom(String name, UUID owner, Map<UUID, String> members, Set<ChunkPos> claims,
                   ChunkPos homeChunk, KingdomSettings settings, Map<ChunkPos, PersonalClaim> personalClaims) {
        this.name = name; this.owner = owner; this.members = new HashMap<>(members);
        this.claimedChunks = new HashSet<>(claims); this.homeChunk = homeChunk;
        this.settings = settings != null ? settings : new KingdomSettings();
        this.personalClaims = personalClaims != null ? new HashMap<>(personalClaims) : new HashMap<>();
        this.treasury = 0; this.lastUpkeepCollection = System.currentTimeMillis(); this.taxContributions = new HashMap<>();
    }

    public Kingdom(String name, UUID owner, Map<UUID, String> members, Set<ChunkPos> claims, ChunkPos homeChunk) {
        this(name, owner, members, claims, homeChunk, new KingdomSettings(), new HashMap<>());
    }

    // ==================== DIPLOMACY METHODS ====================
    public Set<String> getAllies() {
        return Collections.unmodifiableSet(allies);
    }

    public Set<String> getEnemies() {
        return Collections.unmodifiableSet(enemies);
    }

    public boolean addAlly(String kingdomName) {
        return allies.add(kingdomName);
    }

    public boolean removeAlly(String kingdomName) {
        return allies.remove(kingdomName);
    }

    public boolean isAlly(String kingdomName) {
        return allies.contains(kingdomName);
    }

    public boolean addEnemy(String kingdomName) {
        return enemies.add(kingdomName);
    }

    public boolean removeEnemy(String kingdomName) {
        return enemies.remove(kingdomName);
    }

    public boolean isEnemy(String kingdomName) {
        return enemies.contains(kingdomName);
    }

    /**
     * Gets the diplomatic relation between this kingdom and another.
     * Returns: "Member", "Ally", "Enemy", or "Neutral"
     */
    public String getRelationTo(String otherKingdomName) {
        if (this.name.equals(otherKingdomName)) {
            return "Member";
        } else if (this.allies.contains(otherKingdomName)) {
            return "Ally";
        } else if (this.enemies.contains(otherKingdomName)) {
            return "Enemy";
        } else {
            return "Neutral";
        }
    }

    // ==================== TREASURY SYSTEM ====================
    public long getTreasury() { return treasury; }
    public boolean deposit(long amount) { if (amount <= 0) return false; treasury += amount; return true; }
    public boolean withdraw(long amount) { if (amount <= 0 || treasury < amount) return false; treasury -= amount; return true; }

    public long calculateDailyUpkeep() {
        return !settings.isUpkeepEnabled() ? 0 : settings.getBaseUpkeep() + (claimedChunks.size() * settings.getClaimUpkeep());
    }

    private boolean isFalling = false;
    private long fallingStartTime = 0;
    private UUID fallenReclaimedBy = null;

    // Add these methods to Kingdom.java:
    public boolean isFalling() { return isFalling; }
    public long getFallingStartTime() { return fallingStartTime; }
    public UUID getFallenReclaimedBy() { return fallenReclaimedBy; }

    public void setFalling(boolean falling) {
        this.isFalling = falling;
        this.fallingStartTime = falling ? System.currentTimeMillis() : 0;
        this.fallenReclaimedBy = null;
    }

    public boolean canReclaim(UUID playerId) {
        if (!isFalling) return false;

        long timeFallen = System.currentTimeMillis() - fallingStartTime;
        boolean isFirst12Hours = timeFallen <= (12 * 60 * 60 * 1000);

        if (isFirst12Hours) {
            // Only leader can reclaim in first 12 hours
            return isOwner(playerId);
        } else {
            // Any member can reclaim in last 12 hours
            return isMember(playerId);
        }
    }

    public boolean reclaim(UUID playerId, long cost) {
        if (!canReclaim(playerId) || treasury < cost) return false;

        withdraw(cost);
        isFalling = false;
        fallingStartTime = 0;
        fallenReclaimedBy = playerId;
        return true;
    }

    public boolean canAffordUpkeep() { return treasury >= calculateDailyUpkeep(); }

    public boolean processUpkeep() {
        long cost = calculateDailyUpkeep();
        if (cost <= 0) return true;
        if (withdraw(cost)) { lastUpkeepCollection = System.currentTimeMillis(); return true; }
        return false;
    }

    public void handleInsufficientFunds() {
        List<ChunkPos> claims = new ArrayList<>(claimedChunks);
        claims.sort((a, b) -> Integer.compare(
                Math.abs(b.x - homeChunk.x) + Math.abs(b.z - homeChunk.z),
                Math.abs(a.x - homeChunk.x) + Math.abs(a.z - homeChunk.z)
        ));

        while (!canAffordUpkeep() && !claimedChunks.isEmpty()) {
            ChunkPos claim = claims.remove(0);
            claimedChunks.remove(claim);
            personalClaims.remove(claim);
        }
    }

    public void addTaxContribution(UUID playerId, long amount) {
        taxContributions.put(playerId, taxContributions.getOrDefault(playerId, 0L) + amount);
    }

    public long getTotalTaxContributions(UUID playerId) { return taxContributions.getOrDefault(playerId, 0L); }
    public long getLastUpkeepCollection() { return lastUpkeepCollection; }

    public String getTreasuryFormatted() { return formatCurrency(treasury); }
    public String getUpkeepFormatted() { return formatCurrency(calculateDailyUpkeep()); }
    public String getTaxContributionsFormatted(UUID playerId) { return formatCurrency(getTotalTaxContributions(playerId)); }

    private String formatCurrency(long amount) {
        long gold = amount / 10000, silver = (amount % 10000) / 100, bronze = amount % 100;
        if (gold > 0) return String.format("%d gold, %d silver, %d bronze", gold, silver, bronze);
        if (silver > 0) return String.format("%d silver, %d bronze", silver, bronze);
        return String.format("%d bronze", bronze);
    }

    // ==================== SETTINGS & PERMISSIONS ====================
    public KingdomSettings getSettings() { return settings; }
    public boolean hasSettingPermission(UUID playerId, String setting) {
        return isOwner(playerId) || getRank(playerId).equals(RANK_ASSISTANT);
    }

    // ==================== PERSONAL CLAIMS ====================
    public boolean addPersonalClaim(ChunkPos chunk, UUID playerId) {
        return !claimedChunks.contains(chunk) || personalClaims.containsKey(chunk) ? false :
                personalClaims.put(chunk, new PersonalClaim(playerId, chunk)) == null;
    }

    public boolean addPersonalClaim(ChunkPos chunk, UUID playerId, UUID executorId) {
        if (!claimedChunks.contains(chunk) || personalClaims.containsKey(chunk)) return false;
        if (!playerId.equals(executorId) && !isOwner(executorId) && !getRank(executorId).equals(RANK_ASSISTANT)) return false;
        return personalClaims.put(chunk, new PersonalClaim(playerId, chunk)) == null;
    }

    public boolean removePersonalClaim(ChunkPos chunk) { return personalClaims.remove(chunk) != null; }
    public boolean removePersonalClaim(ChunkPos chunk, UUID executorId) {
        PersonalClaim claim = personalClaims.get(chunk);
        if (claim == null) return false;
        if (!executorId.equals(claim.getPlayerId()) && !isOwner(executorId) && !getRank(executorId).equals(RANK_ASSISTANT)) return false;
        return personalClaims.remove(chunk) != null;
    }

    public boolean transferPersonalClaim(ChunkPos chunk, UUID newPlayerId, UUID executorId) {
        if (!isOwner(executorId) && !getRank(executorId).equals(RANK_ASSISTANT)) return false;
        PersonalClaim claim = personalClaims.get(chunk);
        if (claim == null) return false;
        personalClaims.put(chunk, new PersonalClaim(newPlayerId, chunk));
        return true;
    }

    public UUID getPersonalClaimOwner(ChunkPos chunk) {
        PersonalClaim claim = personalClaims.get(chunk);
        return claim != null ? claim.getPlayerId() : null;
    }

    public void cleanupExpiredClaims() {
        personalClaims.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public void revokeAllPlayerClaims(UUID playerId) {
        personalClaims.entrySet().removeIf(entry -> entry.getValue().getPlayerId().equals(playerId));
    }

    public boolean hasPersonalClaimAccess(UUID playerId, ChunkPos chunk) {
        PersonalClaim claim = personalClaims.get(chunk);
        return claim != null && (claim.getPlayerId().equals(playerId) || isOwner(playerId) || getRank(playerId).equals(RANK_ASSISTANT));
    }

    public Map<ChunkPos, PersonalClaim> getPersonalClaims(UUID playerId) {
        Map<ChunkPos, PersonalClaim> result = new HashMap<>();
        personalClaims.entrySet().stream()
                .filter(entry -> entry.getValue().getPlayerId().equals(playerId))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    public int getPersonalClaimCount(UUID playerId) {
        return (int) personalClaims.values().stream().filter(claim -> claim.getPlayerId().equals(playerId)).count();
    }

    // ==================== CLAIMS & TERRITORY ====================
    public boolean isAdjacent(ChunkPos newChunk) {
        return claimedChunks.stream().anyMatch(claimed ->
                Math.abs(claimed.x - newChunk.x) + Math.abs(claimed.z - newChunk.z) == 1);
    }

    public int getClaimCount() { return claimedChunks.size(); }
    public boolean claimChunk(ChunkPos chunk) { return claimedChunks.add(chunk); }
    public void addClaim(ChunkPos chunk) { claimedChunks.add(chunk); }
    public Set<ChunkPos> getClaims() { return claimedChunks; }
    public Set<ChunkPos> getClaimedChunks() { return claimedChunks; }

    public boolean canClaimPersonally(UUID playerId, ChunkPos chunk) {
        return claimedChunks.contains(chunk) && !personalClaims.containsKey(chunk) && isMember(playerId);
    }

    public boolean hasPersonalClaim(ChunkPos chunk) { return personalClaims.containsKey(chunk); }

    // ==================== RANKS & PERMISSIONS ====================
    public boolean hasPermission(UUID playerId, String permission) {
        String rank = members.get(playerId);
        return rank != null && hasRankPermission(rank, permission);
    }

    public boolean hasPermission(PlayerEntity player, String permission) {
        return hasPermission(player.getUuid(), permission);
    }

    private boolean hasRankPermission(String rank, String permission) {
        return switch (rank) {
            case RANK_LEADER -> true;
            case RANK_ASSISTANT -> Arrays.asList("build", "destroy", "switch", "container", "door", "claim",
                    "invite", "kick", "set_home", "unclaim", "manage_ranks", "promote", "withdraw_treasury").contains(permission);
            case RANK_OFFICER -> Arrays.asList("build", "destroy", "switch", "container", "door", "claim", "invite", "kick").contains(permission);
            case RANK_MEMBER -> Arrays.asList("switch", "container", "door").contains(permission);
            case RANK_ALLY -> Arrays.asList("switch", "door").contains(permission);
            default -> false;
        };
    }

    public static String[] getAllRanks() {
        return new String[]{RANK_LEADER, RANK_ASSISTANT, RANK_OFFICER, RANK_MEMBER, RANK_ALLY, RANK_OUTSIDER};
    }

    public static boolean isValidRank(String rankName) {
        return Arrays.asList(getAllRanks()).contains(rankName);
    }

    // ==================== MEMBERSHIP ====================
    public Map<UUID, String> getMembers() { return members; }
    public boolean isMember(UUID playerId) { return members.containsKey(playerId); }
    public boolean isMember(PlayerEntity player) { return members.containsKey(player.getUuid()); }

    public boolean addMember(PlayerEntity player, String rank) {
        return addMember(player.getUuid(), rank);
    }

    public boolean addMember(UUID playerId, String rank) {
        if (members.containsKey(playerId) || !isValidRank(rank)) return false;
        members.put(playerId, rank);
        return true;
    }

    public boolean removeMember(UUID player) { return members.remove(player) != null; }
    public boolean removeMember(PlayerEntity player) { return members.remove(player.getUuid()) != null; }

    public boolean setRank(PlayerEntity player, String rank) {
        return setRank(player.getUuid(), rank);
    }

    public boolean setRank(UUID playerId, String rank) {
        if (!members.containsKey(playerId) || !isValidRank(rank)) return false;
        members.put(playerId, rank);
        return true;
    }

    public String getRank(PlayerEntity player) { return members.get(player.getUuid()); }
    public String getRank(UUID playerId) { return members.get(playerId); }
    public boolean isOwner(PlayerEntity player) { return player.getUuid().equals(owner); }
    public boolean isOwner(UUID playerId) { return playerId.equals(owner); }

    // ==================== BASIC INFO ====================
    public String getName() { return name; }
    public UUID getOwner() { return owner; }
    public ChunkPos getHomeChunk() { return homeChunk; }
    public void setHomeChunk(ChunkPos homeChunk) { this.homeChunk = homeChunk; }

    public static Kingdom getPlayerKingdom(PlayerEntity player) {
        UUID uuid = player.getUuid();
        return KingdomManager.getAllKingdoms().stream().filter(k -> k.isMember(uuid)).findFirst().orElse(null);
    }

    // ==================== PERSISTENCE ====================
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("owner", owner.toString());
        obj.addProperty("homeX", homeChunk.x);
        obj.addProperty("homeZ", homeChunk.z);
        obj.addProperty("lastUpkeepCollection", lastUpkeepCollection);
        obj.add("settings", settings.toJson());

        JsonObject membersJson = new JsonObject();
        members.forEach((id, rank) -> membersJson.addProperty(id.toString(), rank));
        obj.add("members", membersJson);

        JsonArray claimsArray = new JsonArray();
        claimedChunks.forEach(pos -> {
            JsonObject claim = new JsonObject();
            claim.addProperty("x", pos.x);
            claim.addProperty("z", pos.z);
            claimsArray.add(claim);
        });
        obj.add("claims", claimsArray);

        JsonObject personalClaimsObj = new JsonObject();
        personalClaims.forEach((pos, claim) -> {
            JsonObject claimObj = new JsonObject();
            claimObj.addProperty("player", claim.getPlayerId().toString());
            claimObj.addProperty("x", pos.x);
            claimObj.addProperty("z", pos.z);
            personalClaimsObj.add(pos.x + "," + pos.z, claimObj);
        });
        obj.add("personalClaims", personalClaimsObj);

        obj.addProperty("treasury", treasury);
        JsonObject contributionsJson = new JsonObject();
        taxContributions.forEach((id, amount) -> contributionsJson.addProperty(id.toString(), amount));
        obj.add("taxContributions", contributionsJson);

        // ==================== SAVE DIPLOMACY ====================
        JsonArray alliesArray = new JsonArray();
        allies.forEach(alliesArray::add);
        obj.add("allies", alliesArray);

        JsonArray enemiesArray = new JsonArray();
        enemies.forEach(enemiesArray::add);
        obj.add("enemies", enemiesArray);

        return obj;
    }

    public void markDirty() {
        // This will notify KingdomManager that this kingdom needs to be saved
        try {
            KingdomManager.saveToFile();
        } catch (Exception e) {
            // Handle error
        }
    }

    public static Kingdom fromJson(JsonObject obj) {
        String name = obj.get("name").getAsString();
        UUID owner = UUID.fromString(obj.get("owner").getAsString());

        ChunkPos homeChunk = obj.has("homeX") && obj.has("homeZ") ?
                new ChunkPos(obj.get("homeX").getAsInt(), obj.get("homeZ").getAsInt()) :
                getHomeChunkFromClaims(obj.getAsJsonArray("claims"));

        KingdomSettings settings = obj.has("settings") ?
                KingdomSettings.fromJson(obj.getAsJsonObject("settings")) : new KingdomSettings();

        Map<UUID, String> members = new HashMap<>();
        JsonObject membersJson = obj.getAsJsonObject("members");
        membersJson.keySet().forEach(key -> members.put(UUID.fromString(key), membersJson.get(key).getAsString()));

        Set<ChunkPos> claims = new HashSet<>();
        obj.getAsJsonArray("claims").forEach(el -> {
            JsonObject claim = el.getAsJsonObject();
            claims.add(new ChunkPos(claim.get("x").getAsInt(), claim.get("z").getAsInt()));
        });

        Map<ChunkPos, PersonalClaim> personalClaims = new HashMap<>();
        if (obj.has("personalClaims")) {
            obj.getAsJsonObject("personalClaims").keySet().forEach(key -> {
                JsonObject claimObj = obj.getAsJsonObject("personalClaims").getAsJsonObject(key);
                UUID playerId = UUID.fromString(claimObj.get("player").getAsString());
                int x = claimObj.get("x").getAsInt(), z = claimObj.get("z").getAsInt();
                personalClaims.put(new ChunkPos(x, z), new PersonalClaim(playerId, new ChunkPos(x, z)));
            });
        }

        Kingdom kingdom = new Kingdom(name, owner, members, claims, homeChunk, settings, personalClaims);

        if (obj.has("treasury")) kingdom.treasury = obj.get("treasury").getAsLong();
        if (obj.has("lastUpkeepCollection")) kingdom.lastUpkeepCollection = obj.get("lastUpkeepCollection").getAsLong();

        if (obj.has("taxContributions")) {
            obj.getAsJsonObject("taxContributions").keySet().forEach(key ->
                    kingdom.taxContributions.put(UUID.fromString(key), obj.getAsJsonObject("taxContributions").get(key).getAsLong()));
        }

        // ==================== LOAD DIPLOMACY ====================
        if (obj.has("allies")) {
            obj.getAsJsonArray("allies").forEach(element -> kingdom.allies.add(element.getAsString()));
        }
        if (obj.has("enemies")) {
            obj.getAsJsonArray("enemies").forEach(element -> kingdom.enemies.add(element.getAsString()));
        }

        return kingdom;
    }

    private static ChunkPos getHomeChunkFromClaims(JsonArray claimsArray) {
        if (claimsArray != null && !claimsArray.isEmpty()) {
            JsonObject firstClaim = claimsArray.get(0).getAsJsonObject();
            return new ChunkPos(firstClaim.get("x").getAsInt(), firstClaim.get("z").getAsInt());
        }
        return new ChunkPos(0, 0);
    }
}