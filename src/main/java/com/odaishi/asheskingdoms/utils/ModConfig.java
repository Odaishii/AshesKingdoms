package com.odaishi.asheskingdoms.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    // Configuration values with defaults
    public int maxPersonalClaimsPerPlayer = 5;
    public long personalClaimCost = 200;
    public boolean allowPersonalClaims = true;
    public int personalClaimDurationDays = 30;

    // No static INSTANCE, just load and return a new instance
    public static ModConfig loadConfig(File configDir) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(configDir, "asheskingdoms.json");
        ModConfig config = new ModConfig();

        try {
            if (configFile.exists()) {
                config = gson.fromJson(new FileReader(configFile), ModConfig.class);
            } else {
                configDir.mkdirs();
                try (FileWriter writer = new FileWriter(configFile)) {
                    gson.toJson(config, writer);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
        return config;
    }
}