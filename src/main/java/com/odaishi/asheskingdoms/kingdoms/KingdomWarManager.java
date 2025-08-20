package com.odaishi.asheskingdoms.kingdoms;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.*;

/**
 * Very small war manager:
 * - tracks simple War objects (attackerKingdomName, defenderKingdomName, startTime, uuid)
 * - can serialize/deserialize to/from NbtList so KingdomManager can include it in its save().
 *
 * Expand as needed: add war states, timers, participants, PvP rules, logging, victory conditions, etc.
 */
public class KingdomWarManager {

    public static class War {
        public final UUID id;
        public final String attacker;
        public final String defender;
        public final long startMillis;
        public boolean active;

        public War(UUID id, String attacker, String defender, long startMillis, boolean active) {
            this.id = id;
            this.attacker = attacker;
            this.defender = defender;
            this.startMillis = startMillis;
            this.active = active;
        }

        public War(String attacker, String defender) {
            this(UUID.randomUUID(), attacker, defender, System.currentTimeMillis(), true);
        }

        public NbtCompound toNbt() {
            NbtCompound c = new NbtCompound();
            c.putUuid("Id", id);
            c.putString("Attacker", attacker);
            c.putString("Defender", defender);
            c.putLong("StartMillis", startMillis);
            c.putBoolean("Active", active);
            return c;
        }

        public static War fromNbt(NbtCompound c) {
            UUID id = c.getUuid("Id");
            String attacker = c.getString("Attacker");
            String defender = c.getString("Defender");
            long start = c.contains("StartMillis") ? c.getLong("StartMillis") : System.currentTimeMillis();
            boolean active = c.contains("Active") ? c.getBoolean("Active") : true;
            return new War(id, attacker, defender, start, active);
        }
    }

    // Map from war id -> War
    private static final Map<UUID, War> wars = new LinkedHashMap<>();

    /***********************
     * Public API
     ***********************/
    public static Collection<War> getAllWars() {
        return Collections.unmodifiableCollection(wars.values());
    }

    public static Optional<War> getWarById(UUID id) {
        return Optional.ofNullable(wars.get(id));
    }

    public static Optional<War> getWarBetween(String attacker, String defender) {
        for (War w : wars.values()) {
            if (w.attacker.equals(attacker) && w.defender.equals(defender)) return Optional.of(w);
            if (w.attacker.equals(defender) && w.defender.equals(attacker)) return Optional.of(w);
        }
        return Optional.empty();
    }

    public static War declareWar(String attacker, String defender) {
        War existing = getWarBetween(attacker, defender).orElse(null);
        if (existing != null) {
            // If there is already a war between them, just return it (caller decides how to message)
            return existing;
        }
        War w = new War(attacker, defender);
        wars.put(w.id, w);
        // Trigger a save so wars persist
        try {
            KingdomManager.saveToFile();
        } catch (Exception e) {
            System.err.println("Failed to save war data: " + e.getMessage());
        }
        return w;
    }

    public static void endWar(UUID warId) {
        War w = wars.get(warId);
        if (w != null) {
            w.active = false;
            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                System.err.println("Failed to save war data: " + e.getMessage());
            }
        }
    }

    public static void removeWar(UUID warId) {
        if (wars.remove(warId) != null) {
            try {
                KingdomManager.saveToFile();
            } catch (Exception e) {
                System.err.println("Failed to save war data: " + e.getMessage());
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

    // Helper used by KingdomManager saving/loading: pass its NbtCompound and we will handle
    public static void saveInto(NbtCompound root) {
        root.put("Wars", saveToNbt());
    }

    public static void loadFrom(NbtCompound root) {
        if (root == null) return;
        if (!root.contains("Wars")) return;
        NbtList list = root.getList("Wars", 10); // 10 = compound
        loadFromNbt(list);
    }
}