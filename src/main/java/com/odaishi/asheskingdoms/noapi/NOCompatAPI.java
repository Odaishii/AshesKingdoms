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