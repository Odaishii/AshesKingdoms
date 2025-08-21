/**
 * RANK PERMISSION MANAGEMENT SYSTEM
 *
 * Defines hierarchical roles within kingdoms with configurable permissions.
 * Each rank has a set of boolean flags controlling what actions members
 * of that rank can perform within kingdom territory.
 *
 * PERMISSION TYPES:
 * - Territory Management: claim, unclaim, set_home
 * - Build Permissions: build, destroy, container access
 * - Member Management: invite, kick, promote, manage_ranks
 * - Interaction Permissions: switch, door, pvp, mobDamage
 *
 * DEFAULT PERMISSIONS:
 * All permissions default to false, must be explicitly enabled for each rank
 * to follow the principle of least privilege.
 *
 * FEATURES:
 * - JSON serialization for persistence
 * - Dynamic permission configuration
 * - Default permission set consistency
 * - Rank-based access control
 *
 * USAGE:
 * Kingdom leaders can customize permissions for each rank, creating
 * granular access control systems tailored to their kingdom's needs.
 */

package com.odaishi.asheskingdoms.kingdoms;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class Rank {
    private String name;
    private Map<String, Boolean> permissions;

    public Rank(String name) {
        this.name = name;
        this.permissions = new HashMap<>();
        // Set default permissions to false
        for (String perm : getDefaultPermissions()) {
            permissions.put(perm, false);
        }
    }

    public Rank(String name, Map<String, Boolean> permissions) {
        this.name = name;
        this.permissions = new HashMap<>(permissions);
    }

    public void setPermission(String permission, boolean value) {
        permissions.put(permission, value);
    }

    public boolean hasPermission(String permission) {
        return permissions.getOrDefault(permission, false);
    }

    public String getName() {
        return name;
    }

    public Map<String, Boolean> getPermissions() {
        return new HashMap<>(permissions);
    }

    public static String[] getDefaultPermissions() {
        return new String[]{
                "build", "destroy", "switch", "container", "door",
                "pvp", "mobDamage", "claim", "invite", "promote",
                "manage_ranks", "kick", "set_home", "unclaim"
        };
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);

        JsonObject permsObj = new JsonObject();
        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
            permsObj.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("permissions", permsObj);

        return obj;
    }

    public static Rank fromJson(JsonObject obj) {
        String name = obj.get("name").getAsString();
        JsonObject permsObj = obj.getAsJsonObject("permissions");

        Map<String, Boolean> permissions = new HashMap<>();
        for (String key : permsObj.keySet()) {
            permissions.put(key, permsObj.get(key).getAsBoolean());
        }

        return new Rank(name, permissions);
    }
}