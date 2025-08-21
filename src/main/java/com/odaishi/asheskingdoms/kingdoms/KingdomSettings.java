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

    public KingdomSettings() {
        settings = new HashMap<>();
        setDefaultSettings();
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

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Boolean> entry : settings.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    public static KingdomSettings fromJson(JsonObject obj) {
        KingdomSettings settings = new KingdomSettings();
        for (String key : obj.keySet()) {
            if (settings.settings.containsKey(key)) {
                settings.setSetting(key, obj.get(key).getAsBoolean());
            }
        }
        return settings;
    }

    public static String[] getAvailableSettings() {
        return new String[]{
                "mobSpawning", "fireSpread", "tntExplosion", "pvp",
                "mobGriefing", "friendlyFire", "publicAccess", "animalSpawning"
        };
    }
}