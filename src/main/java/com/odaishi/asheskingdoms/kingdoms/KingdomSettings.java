/**
 * KINGDOM SETTINGS MANAGEMENT
 *
 * This class handles all configurable settings for kingdoms in the AshesKingdoms mod.
 * It provides a centralized system for managing kingdom-wide rules and permissions.
 *
 * FEATURES:
 * - Boolean-based settings system with default values
 * - JSON serialization/deserialization for persistence
 * - Default settings optimized for balanced gameplay
 * - Extensible design for adding new settings
 *
 * AVAILABLE SETTINGS:
 * - mobSpawning: Controls whether mobs can spawn in kingdom territory
 * - fireSpread: Determines if fire can spread within kingdom claims
 * - tntExplosion: Allows or prevents TNT explosions in kingdom land
 * - pvp: Enables/disables player vs player combat within kingdom
 * - mobGriefing: Controls whether mobs can grief (creeper explosions, enderman pickup, etc.)
 * - friendlyFire: Allows players to damage other kingdom members
 * - publicAccess: Grants outsiders basic access to kingdom territory
 * - animalSpawning: Controls passive animal spawning in kingdom claims
 *
 * TREASURY SETTINGS:
 * - baseUpkeep: Daily base upkeep cost in bronze units
 * - claimUpkeep: Daily upkeep cost per claim in bronze units
 * - enableUpkeep: Whether the upkeep system is enabled
 *
 * USAGE:
 * Settings can be modified by kingdom leaders and assistants through commands,
 * affecting all territory owned by the kingdom.
 */
package com.odaishi.asheskingdoms.kingdoms;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class KingdomSettings {
    private final Map<String, Boolean> settings;
    private long baseUpkeep; // Daily base upkeep cost in bronze units
    private long claimUpkeep; // Daily upkeep cost per claim in bronze units
    private boolean enableUpkeep; // Whether upkeep system is enabled

    public KingdomSettings() {
        settings = new HashMap<>();
        setDefaultSettings();

        // Default treasury settings
        this.baseUpkeep = 1000; // 10 copper (1000 bronze) daily base upkeep
        this.claimUpkeep = 100; // 1 copper (100 bronze) per claim daily
        this.enableUpkeep = true; // Upkeep system enabled by default
    }

    private void setDefaultSettings() {
        settings.put("mobSpawning", true);
        settings.put("fireSpread", true);
        settings.put("tntExplosion", true);
        settings.put("pvp", false);
        settings.put("mobGriefing", true);
        settings.put("friendlyFire", false);
        settings.put("publicAccess", false);
        settings.put("animalSpawning", true);
    }

    public boolean getSetting(String key) {
        return settings.getOrDefault(key, false);
    }

    public void setSetting(String key, boolean value) {
        if (settings.containsKey(key)) {
            settings.put(key, value);
        }
    }

    /* -------------------- TREASURY SETTINGS -------------------- */

    public long getBaseUpkeep() {
        return baseUpkeep;
    }

    public void setBaseUpkeep(long baseUpkeep) {
        this.baseUpkeep = baseUpkeep;
    }

    public long getClaimUpkeep() {
        return claimUpkeep;
    }

    public void setClaimUpkeep(long claimUpkeep) {
        this.claimUpkeep = claimUpkeep;
    }

    public boolean isUpkeepEnabled() {
        return enableUpkeep;
    }

    public void setUpkeepEnabled(boolean enableUpkeep) {
        this.enableUpkeep = enableUpkeep;
    }

    /* -------------------- SERIALIZATION -------------------- */

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();

        // Boolean settings
        for (Map.Entry<String, Boolean> entry : settings.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }

        // Treasury settings
        obj.addProperty("baseUpkeep", baseUpkeep);
        obj.addProperty("claimUpkeep", claimUpkeep);
        obj.addProperty("enableUpkeep", enableUpkeep);

        return obj;
    }

    public static KingdomSettings fromJson(JsonObject obj) {
        KingdomSettings settings = new KingdomSettings();

        // Load boolean settings
        for (String key : obj.keySet()) {
            if (settings.settings.containsKey(key)) {
                settings.setSetting(key, obj.get(key).getAsBoolean());
            }
        }

        // Load treasury settings (with backward compatibility)
        if (obj.has("baseUpkeep")) {
            settings.setBaseUpkeep(obj.get("baseUpkeep").getAsLong());
        }
        if (obj.has("claimUpkeep")) {
            settings.setClaimUpkeep(obj.get("claimUpkeep").getAsLong());
        }
        if (obj.has("enableUpkeep")) {
            settings.setUpkeepEnabled(obj.get("enableUpkeep").getAsBoolean());
        }

        return settings;
    }

    public static String[] getAvailableSettings() {
        return new String[]{
                "mobSpawning", "fireSpread", "tntExplosion", "pvp",
                "mobGriefing", "friendlyFire", "publicAccess", "animalSpawning"
        };
    }

    public static String[] getTreasurySettings() {
        return new String[]{
                "baseUpkeep", "claimUpkeep", "enableUpkeep"
        };
    }
}