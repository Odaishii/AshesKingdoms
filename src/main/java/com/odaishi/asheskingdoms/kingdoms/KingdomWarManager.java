package com.odaishi.asheskingdoms.kingdoms;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.util.Formatting;

import java.util.*;

import static com.odaishi.asheskingdoms.commands.KingdomCommand.notifyKingdom;

public class KingdomWarManager {

    public static class War {
        public final UUID id;
        public final String attacker;
        public final String defender;
        public final long declarationTime;
        public final long gracePeriodEnd;
        public boolean active;
        public final Set<ChunkPos> capturedClaims;
        public final ChunkPos defenderHomeblock;
        public boolean attackerVictory = false;
        public boolean defenderSurrendered = false;
        public boolean warEndedEarly = false;
        public UUID endedBy = null; // Who ended the war early

        public boolean isConquestComplete() {
            return isHomeblockCaptured() || defenderSurrendered;
        }

        public War(UUID id, String attacker, String defender, long declarationTime,
                   long gracePeriodEnd, boolean active, ChunkPos defenderHomeblock) {
            this.id = id;
            this.attacker = attacker;
            this.defender = defender;
            this.declarationTime = declarationTime;
            this.gracePeriodEnd = gracePeriodEnd;
            this.active = active;
            this.capturedClaims = new HashSet<>();
            this.defenderHomeblock = defenderHomeblock;
        }

        public War(String attacker, String defender, ChunkPos defenderHomeblock) {
            this(UUID.randomUUID(), attacker, defender, System.currentTimeMillis(),
                    System.currentTimeMillis() + 10000, true, defenderHomeblock); // 48-hour grace period
        }

        public boolean isInGracePeriod() {
            return System.currentTimeMillis() < gracePeriodEnd;
        }

        public boolean isHomeblockCaptured() {
            return capturedClaims.contains(defenderHomeblock);
        }

        public double getConquestPercentage() {
            Kingdom defenderKingdom = KingdomManager.getKingdom(defender);
            if (defenderKingdom == null) return 0;
            int totalClaims = defenderKingdom.getClaimCount();
            return totalClaims > 0 ? (double) capturedClaims.size() / totalClaims : 0;
        }

        public static void updateActiveCaptures(MinecraftServer server) {
            if (server == null) return;

            // Clean up old boss bars for players who left
            Iterator<Map.Entry<UUID, ServerBossBar>> bossBarIterator = captureBossBars.entrySet().iterator();
            while (bossBarIterator.hasNext()) {
                Map.Entry<UUID, ServerBossBar> entry = bossBarIterator.next();
                if (server.getPlayerManager().getPlayer(entry.getKey()) == null) {
                    entry.getValue().clearPlayers();
                    bossBarIterator.remove();
                }
            }

            // Update all active captures and boss bars
            Iterator<Map.Entry<ChunkPos, CaptureProgress>> captureIterator = activeCaptures.entrySet().iterator();
            while (captureIterator.hasNext()) {
                Map.Entry<ChunkPos, CaptureProgress> entry = captureIterator.next();
                ChunkPos chunk = entry.getKey();
                CaptureProgress progress = entry.getValue();

                // Update players in the capture zone
                updatePlayersInCaptureZone(chunk, server);

                // Update boss bars for players in this capture zone
                updateBossBarsForChunk(chunk, progress, server);

                // Check if capture is complete
                if (progress.updateProgress()) {
                    // Capture finished
                    captureIterator.remove();
                    completeCapture(chunk, progress);
                    cleanupBossBarsForChunk(chunk);
                }
            }
        }

        private static void updatePlayersInCaptureZone(ChunkPos chunk, MinecraftServer server) {
            Set<UUID> playersInZone = playersInCaptureZones.computeIfAbsent(chunk, k -> new HashSet<>());
            Set<UUID> currentPlayers = new HashSet<>();

            // Find all players currently in this chunk
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ChunkPos playerChunk = new ChunkPos(player.getBlockPos());
                if (playerChunk.equals(chunk)) {
                    currentPlayers.add(player.getUuid());
                    if (!playersInZone.contains(player.getUuid())) {
                        // Player entered capture zone
                        player.sendMessage(Text.literal("§6You entered a contested territory!"), false);
                    }
                }
            }

            // Remove players who left the zone from tracking
            playersInZone.removeIf(playerId -> !currentPlayers.contains(playerId));
            playersInCaptureZones.put(chunk, currentPlayers);
        }

        private static void updateBossBarsForChunk(ChunkPos chunk, CaptureProgress progress, MinecraftServer server) {
            Set<UUID> playersInZone = playersInCaptureZones.getOrDefault(chunk, new HashSet<>());

            for (UUID playerId : playersInZone) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    ServerBossBar bossBar = captureBossBars.computeIfAbsent(playerId, id -> {
                        ServerBossBar newBossBar = new ServerBossBar(
                                Text.literal("Capturing Territory"),
                                BossBar.Color.RED,
                                BossBar.Style.PROGRESS
                        );
                        newBossBar.addPlayer(player);
                        return newBossBar;
                    });

                    // Update boss bar progress and title
                    double progressPercent = progress.getProgressPercentage();
                    bossBar.setPercent((float) progressPercent);

                    Kingdom capturingKingdom = KingdomManager.getKingdom(progress.capturingKingdom.toString());
                    String kingdomName = capturingKingdom != null ? capturingKingdom.getName() : "Unknown";

                    bossBar.setName(Text.literal("Capturing for " + kingdomName + ": " +
                            String.format("%.0f%%", progressPercent * 100)));
                }
            }
        }

        private static void cleanupBossBarsForChunk(ChunkPos chunk) {
            Set<UUID> playersInZone = playersInCaptureZones.remove(chunk);
            if (playersInZone != null) {
                for (UUID playerId : playersInZone) {
                    ServerBossBar bossBar = captureBossBars.remove(playerId);
                    if (bossBar != null) {
                        bossBar.clearPlayers();
                    }
                }
            }
        }

        private static void completeCapture(ChunkPos chunk, CaptureProgress progress) {
            // Find the war that this capture belongs to
            for (War war : wars.values()) {
                if (war.active) {
                    Kingdom capturingKingdom = KingdomManager.getKingdom(progress.capturingKingdom.toString());
                    if (capturingKingdom != null && war.attacker.equals(capturingKingdom.getName())) {
                        war.capturedClaims.add(chunk);

                        // Check if homeblock was captured (victory condition)
                        if (chunk.equals(war.defenderHomeblock)) {
                            endWar(war.id, true);
                            broadcastVictory(war);
                        }

                        try {
                            KingdomManager.saveToFile();
                        } catch (Exception e) {
                            System.err.println("Failed to save war data: " + e.getMessage());
                        }
                        break;
                    }
                }
            }
        }

        public NbtCompound toNbt() {
            NbtCompound c = new NbtCompound();
            c.putUuid("Id", id);
            c.putString("Attacker", attacker);
            c.putString("Defender", defender);
            c.putLong("DeclarationTime", declarationTime);
            c.putLong("GracePeriodEnd", gracePeriodEnd);
            c.putBoolean("Active", active);
            c.putInt("HomeblockX", defenderHomeblock.x);
            c.putInt("HomeblockZ", defenderHomeblock.z);

            // Save captured claims
            NbtList capturedList = new NbtList();
            for (ChunkPos claim : capturedClaims) {
                NbtCompound claimComp = new NbtCompound();
                claimComp.putInt("X", claim.x);
                claimComp.putInt("Z", claim.z);
                capturedList.add(claimComp);
            }
            c.put("CapturedClaims", capturedList);

            return c;
        }

        public static War fromNbt(NbtCompound c) {
            UUID id = c.getUuid("Id");
            String attacker = c.getString("Attacker");
            String defender = c.getString("Defender");
            long declarationTime = c.getLong("DeclarationTime");
            long gracePeriodEnd = c.getLong("GracePeriodEnd");
            boolean active = c.getBoolean("Active");
            ChunkPos homeblock = new ChunkPos(c.getInt("HomeblockX"), c.getInt("HomeblockZ"));

            War war = new War(id, attacker, defender, declarationTime, gracePeriodEnd, active, homeblock);

            // Load captured claims
            if (c.contains("CapturedClaims")) {
                NbtList capturedList = c.getList("CapturedClaims", 10);
                for (int i = 0; i < capturedList.size(); i++) {
                    NbtCompound claimComp = capturedList.getCompound(i);
                    war.capturedClaims.add(new ChunkPos(claimComp.getInt("X"), claimComp.getInt("Z")));
                }
            }

            return war;
        }
    }

    public static class CaptureProgress {
        public final ChunkPos chunk;
        public final UUID capturingKingdom;
        public long startTime;
        public long progress;
        public final long captureDuration = 120000; // 2 minutes to capture

        public CaptureProgress(ChunkPos chunk, UUID capturingKingdom) {
            this.chunk = chunk;
            this.capturingKingdom = capturingKingdom;
            this.startTime = System.currentTimeMillis();
            this.progress = 0;
        }

        public boolean updateProgress() {
            long elapsed = System.currentTimeMillis() - startTime;
            progress = Math.min(elapsed, captureDuration);
            return progress >= captureDuration;
        }

        public double getProgressPercentage() {
            return (double) progress / captureDuration;
        }

        public void reset() {
            startTime = System.currentTimeMillis();
            progress = 0;
        }
    }

    private static final Map<UUID, War> wars = new HashMap<>();
    private static final Map<ChunkPos, CaptureProgress> activeCaptures = new HashMap<>();
    private static final Map<UUID, ServerBossBar> captureBossBars = new HashMap<>();
    private static final Map<ChunkPos, Set<UUID>> playersInCaptureZones = new HashMap<>();

    /***********************
     * Public API
     ***********************/
    public static Collection<War> getAllWars() {
        return Collections.unmodifiableCollection(wars.values());
    }

    public static Optional<War> getWarById(UUID id) {
        return Optional.ofNullable(wars.get(id));
    }

    public static Optional<War> getWarBetween(String kingdom1, String kingdom2) {
        return wars.values().stream()
                .filter(war -> war.active &&
                        ((war.attacker.equals(kingdom1) && war.defender.equals(kingdom2)) ||
                                (war.attacker.equals(kingdom2) && war.defender.equals(kingdom1))))
                .findFirst();
    }

    public static Optional<War> declareWar(String attacker, String defender, long cost) {
        // Check if already at war
        if (getWarBetween(attacker, defender).isPresent()) {
            return Optional.empty();
        }

        // Check if defender is set as enemy
        Kingdom attackerKingdom = KingdomManager.getKingdom(attacker);
        if (attackerKingdom == null || !attackerKingdom.isEnemy(defender)) {
            return Optional.empty();
        }

        // Check if attacker can afford war cost
        if (attackerKingdom.getTreasury() < cost) {
            return Optional.empty();
        }

        // Pay war cost
        if (!attackerKingdom.withdraw(cost)) {
            return Optional.empty();
        }

        Kingdom defenderKingdom = KingdomManager.getKingdom(defender);
        if (defenderKingdom == null) {
            return Optional.empty();
        }

        // Create war with defender's homeblock
        War war = new War(attacker, defender, defenderKingdom.getHomeChunk());
        wars.put(war.id, war);

        // Notify both kingdoms
        notifyWarDeclaration(war);

        try {
            KingdomManager.saveToFile();
        } catch (Exception e) {
            System.err.println("Failed to save war data: " + e.getMessage());
        }

        return Optional.of(war);
    }

    public static boolean captureClaim(ChunkPos chunk, String capturingKingdom, ServerPlayerEntity capturer) {
        Kingdom kingdom = KingdomManager.getKingdomAt(chunk);
        if (kingdom == null) return false;

        Optional<War> warOpt = getWarBetween(capturingKingdom, kingdom.getName());
        if (warOpt.isEmpty()) return false;

        War war = warOpt.get();

        // Check if claim is already captured
        if (war.capturedClaims.contains(chunk)) return false;

        // Check adjacency - must be adjacent to already captured claims or attacker's territory
        if (!isCaptureAllowed(chunk, war)) return false;

        // Start or update capture progress
        CaptureProgress progress = activeCaptures.computeIfAbsent(chunk,
                k -> new CaptureProgress(chunk, KingdomManager.getKingdom(capturingKingdom).getOwner()));

        if (progress.updateProgress()) {
            // Capture complete
            war.capturedClaims.add(chunk);
            activeCaptures.remove(chunk);

            // Check for victory
            if (war.isHomeblockCaptured()) {
                endWar(war.id, true);
                broadcastVictory(war);
            }

            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                System.err.println("Failed to save war data: " + e.getMessage());
            }
            return true;
        }

        return false;
    }

    private static boolean isCaptureAllowed(ChunkPos chunk, War war) {
        // Can capture if adjacent to attacker's territory or already captured claims
        Kingdom attackerKingdom = KingdomManager.getKingdom(war.attacker);
        if (attackerKingdom != null && attackerKingdom.isAdjacent(chunk)) {
            return true;
        }

        return war.capturedClaims.stream().anyMatch(captured ->
                Math.abs(captured.x - chunk.x) + Math.abs(captured.z - chunk.z) == 1);
    }

    public static void endWar(UUID warId, boolean attackerVictory) {
        War war = wars.get(warId);
        if (war != null) {
            war.active = false;

            if (attackerVictory) {
                handleWarVictory(war);
            }

            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                System.err.println("Failed to save war data: " + e.getMessage());
            }
        }
    }

    private static void handleWarVictory(War war) {
        Kingdom defenderKingdom = KingdomManager.getKingdom(war.defender);
        if (defenderKingdom != null) {
            if (war.attackerVictory) {
                // Attacker wins - defender kingdom goes into falling state
                defenderKingdom.setFalling(true);
                defenderKingdom.markDirty();

                // Notify both kingdoms
                notifyKingdom(KingdomManager.getKingdom(war.attacker),
                        "§aVictory! " + war.defender + " has fallen and can now be claimed!");
                notifyKingdom(defenderKingdom,
                        "§cDefeat! Your kingdom has fallen. It can now be claimed by " + war.attacker);
            }
        }
    }

    private static void notifyWarDeclaration(War war) {
        MinecraftServer server = KingdomManager.getServer();
        if (server != null) {
            String message = "§cWar declared! " + war.attacker + " has declared war on " + war.defender +
                    ". Grace period ends in 48 hours.";
            server.getPlayerManager().broadcast(Text.literal(message), false);
        }
    }

    private static void broadcastVictory(War war) {
        MinecraftServer server = KingdomManager.getServer();
        if (server != null) {
            String message = "§6VICTORY! " + war.attacker + " has conquered " + war.defender +
                    " by capturing their homeblock!";
            server.getPlayerManager().broadcast(Text.literal(message), false);
        }
    }

    /***********************
     * Capture Management Methods
     ***********************/
    public static boolean isChunkBeingCaptured(ChunkPos chunk) {
        return activeCaptures.containsKey(chunk);
    }

    public static void updateActiveCaptures(MinecraftServer server) {
        if (server == null) return;

        // Clean up old boss bars for players who left
        Iterator<Map.Entry<UUID, ServerBossBar>> bossBarIterator = captureBossBars.entrySet().iterator();
        while (bossBarIterator.hasNext()) {
            Map.Entry<UUID, ServerBossBar> entry = bossBarIterator.next();
            if (server.getPlayerManager().getPlayer(entry.getKey()) == null) {
                entry.getValue().clearPlayers();
                bossBarIterator.remove();
            }
        }

        // Update all active captures
        Iterator<Map.Entry<ChunkPos, CaptureProgress>> captureIterator = activeCaptures.entrySet().iterator();
        while (captureIterator.hasNext()) {
            Map.Entry<ChunkPos, CaptureProgress> entry = captureIterator.next();
            ChunkPos chunk = entry.getKey();
            CaptureProgress progress = entry.getValue();

            // Update the capture progress
            boolean captureComplete = progress.updateProgress();

            if (captureComplete) {
                // Capture finished - remove from active captures
                captureIterator.remove();

                // Find the war this capture belongs to and mark the claim as captured
                completeCapture(chunk, progress);

                // Clean up boss bars for this chunk
                cleanupBossBarsForChunk(chunk);
            } else {
                // Update boss bars for players in this capture zone
                updateBossBarsForChunk(chunk, progress, server);
            }
        }
    }

    private static void completeCapture(ChunkPos chunk, CaptureProgress progress) {
        // Find the war that this capture belongs to
        for (War war : wars.values()) {
            if (war.active) {
                Kingdom capturingKingdom = KingdomManager.getKingdom(progress.capturingKingdom.toString());
                if (capturingKingdom != null && war.attacker.equals(capturingKingdom.getName())) {
                    war.capturedClaims.add(chunk);

                    // Check if homeblock was captured (victory condition)
                    if (chunk.equals(war.defenderHomeblock)) {
                        endWar(war.id, true);
                        broadcastVictory(war);
                    }

                    try {
                        KingdomManager.saveToFile();
                    } catch (Exception e) {
                        System.err.println("Failed to save war data: " + e.getMessage());
                    }
                    break;
                }
            }
        }
    }

    private static void updateBossBarsForChunk(ChunkPos chunk, CaptureProgress progress, MinecraftServer server) {
        Set<UUID> playersInZone = playersInCaptureZones.computeIfAbsent(chunk, k -> new HashSet<>());

        // Find players currently in this chunk
        Set<UUID> currentPlayers = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ChunkPos playerChunk = new ChunkPos(player.getBlockPos());
            if (playerChunk.equals(chunk)) {
                currentPlayers.add(player.getUuid());

                // Create or update boss bar for this player
                ServerBossBar bossBar = captureBossBars.computeIfAbsent(player.getUuid(), id -> {
                    ServerBossBar newBossBar = new ServerBossBar(
                            Text.literal("Capturing Territory"),
                            BossBar.Color.RED,
                            BossBar.Style.PROGRESS
                    );
                    newBossBar.addPlayer(player);
                    return newBossBar;
                });

                // Update boss bar progress
                double progressPercent = progress.getProgressPercentage();
                bossBar.setPercent((float) progressPercent);

                // Update boss bar title
                Kingdom capturingKingdom = KingdomManager.getKingdom(progress.capturingKingdom.toString());
                String kingdomName = capturingKingdom != null ? capturingKingdom.getName() : "Unknown";
                bossBar.setName(Text.literal("Capturing for " + kingdomName + ": " +
                        String.format("%.0f%%", progressPercent * 100)));
            }
        }

        // Remove players who left the chunk from boss bars
        Iterator<UUID> zoneIterator = playersInZone.iterator();
        while (zoneIterator.hasNext()) {
            UUID playerId = zoneIterator.next();
            if (!currentPlayers.contains(playerId)) {
                ServerBossBar bossBar = captureBossBars.remove(playerId);
                if (bossBar != null) {
                    bossBar.clearPlayers();
                }
                zoneIterator.remove();
            }
        }

        playersInCaptureZones.put(chunk, currentPlayers);
    }

    private static void cleanupBossBarsForChunk(ChunkPos chunk) {
        Set<UUID> playersInZone = playersInCaptureZones.remove(chunk);
        if (playersInZone != null) {
            for (UUID playerId : playersInZone) {
                ServerBossBar bossBar = captureBossBars.remove(playerId);
                if (bossBar != null) {
                    bossBar.clearPlayers();
                }
            }
        }
    }

    public static void onServerTick() {
        // Clean up expired captures and update progress
        Iterator<Map.Entry<ChunkPos, CaptureProgress>> iterator = activeCaptures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkPos, CaptureProgress> entry = iterator.next();
            CaptureProgress progress = entry.getValue();

            if (progress.updateProgress()) {
                // Capture completed
                iterator.remove();
                completeCapture(entry.getKey(), progress);
                cleanupBossBarsForChunk(entry.getKey());
            }
        }
    }

    /***********************
     * Serialization
     ***********************/
    public static NbtList saveToNbt() {
        NbtList list = new NbtList();
        for (War w : wars.values()) {
            list.add(w.toNbt());
        }
        return list;
    }

    public static void loadFromNbt(NbtList list) {
        wars.clear();
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            War w = War.fromNbt(c);
            wars.put(w.id, w);
        }
    }

    public static void saveInto(NbtCompound root) {
        root.put("Wars", saveToNbt());
    }

    public static void loadFrom(NbtCompound root) {
        if (root == null) return;
        if (!root.contains("Wars")) return;
        NbtList list = root.getList("Wars", 10);
        loadFromNbt(list);
    }
}