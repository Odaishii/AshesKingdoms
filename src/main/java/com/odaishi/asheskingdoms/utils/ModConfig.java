/**
 * CONFIGURATION MANAGER FOR ASHES KINGDOMS
 *
 * Handles mod configuration including default values, limits,
 * and economy settings. Loads from config file on startup.
 */
package com.odaishi.asheskingdoms.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static ModConfig INSTANCE;
    private File configFile;

    // Configuration values
    public int maxPersonalClaimsPerPlayer = 5;
    public long personalClaimCost = 200; // bronze coins
    public boolean allowPersonalClaims = true;
    public int personalClaimDurationDays = 30; // 0 for permanent

    public static void loadConfig(File configDir) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(configDir, "asheskingdoms.json");

        try {
            if (configFile.exists()) {
                INSTANCE = gson.fromJson(new FileReader(configFile), ModConfig.class);
            } else {
                INSTANCE = new ModConfig();
                configDir.mkdirs();
                try (FileWriter writer = new FileWriter(configFile)) {
                    gson.toJson(INSTANCE, writer);
                }
            }
            INSTANCE.configFile = configFile;
        } catch (IOException e) {
            // Handle error
        }
    }

    public static ModConfig getInstance() {
        return INSTANCE;
    }

    public void save() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            // Handle error
        }
    }
}