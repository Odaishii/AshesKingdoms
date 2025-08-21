/**
 * NUMISMATIC OVERHAUL COMPATIBILITY INITIALIZER
 *
 * Bootstrap class for Numismatic Overhaul integration system. Handles
 * automatic detection and initialization of NO currency API adapters
 * during mod loading phase.
 *
 * RESPONSIBILITIES:
 * - Detects Numismatic Overhaul mod presence at runtime
 * - Initializes appropriate API adapter (MethodHandle or reflection)
 * - Provides early warning for missing NO dependency
 * - Coordinates integration component initialization
 *
 * INITIALIZATION FLOW:
 * - Checks FabricLoader for NO mod presence
 * - Initializes NORuntimeAdapter if NO available
 * - Logs appropriate status messages for debugging
 * - Falls back gracefully when NO not installed
 *
 * DEPENDENCY HANDLING:
 * - Soft dependency - mod functions without NO
 * - Automatic feature degradation when missing
 * - Clear logging to inform users/moderators
 *
 * INTEGRATION POINT:
 * Serves as the entry point for NO currency system integration,
 * ensuring proper initialization order and dependency management.
 */

package com.odaishi.asheskingdoms.noapi;


import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;


public class NOCompatAPI implements ModInitializer {
    public static final String MOD_ID = "no-compat-api";


    @Override public void onInitialize() {
        if (FabricLoader.getInstance().isModLoaded("numismatic-overhaul")) {
            NOLog.info("Numismatic Overhaul detected — wiring runtime adapter");
            NORuntimeAdapter.get(); // initialize; logs resolution
        } else {
            NOLog.warn("Numismatic Overhaul not present. API will be inert.");
        }
    }
}