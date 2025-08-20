package com.odaishi.asheskingdoms;

import com.odaishi.asheskingdoms.commands.KingdomClaimCommand;
import com.odaishi.asheskingdoms.commands.KingdomCommand;
import com.odaishi.asheskingdoms.commands.KingdomMemberCommand;
import com.odaishi.asheskingdoms.noapi.NoApi;
import com.odaishi.asheskingdoms.noapi.NORuntimeAdapter;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import com.odaishi.asheskingdoms.commands.KingdomWarCommand;

import java.io.File;
import java.util.UUID;

public class AshesKingdoms implements ModInitializer {

	public static NoApi noApi;
	public static final String MOD_ID = "asheskingdoms";
	private static AshesKingdoms INSTANCE;
	private MinecraftServer server;

	public static AshesKingdoms getInstance() {
		return INSTANCE;
	}

	// Remove the NoApi initialization completely:
	@Override
	public void onInitialize() {
		INSTANCE = this;

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			KingdomCommand.register(dispatcher);
			KingdomMemberCommand.register(dispatcher);
			KingdomClaimCommand.register(dispatcher);
			KingdomWarCommand.register(dispatcher);
		});

		// Server lifecycle hooks
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			this.server = server;
			KingdomManager.setServer(server);
			loadData();
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			saveData();
		});

		System.out.println("[AshesKingdoms] Using item-based economy system.");
		System.out.println("[AshesKingdoms] Mod initialized successfully.");
	}

	/** Save kingdoms data to file */
	public void saveData() {
		if (server != null) {
			try {
				KingdomManager.saveToFile();
				System.out.println("[AshesKingdoms] Kingdom data saved.");
			} catch (Exception e) {
				System.err.println("[AshesKingdoms] Failed to save kingdom data: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	/** Load kingdoms data from file */
	public void loadData() {
		if (server != null) {
			try {
				KingdomManager.loadFromFile();
				System.out.println("[AshesKingdoms] Kingdom data loaded.");
			} catch (Exception e) {
				System.err.println("[AshesKingdoms] Failed to load kingdom data: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public MinecraftServer getServer() {
		return server;
	}
}