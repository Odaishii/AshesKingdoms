package com.odaishi.asheskingdoms;

import com.odaishi.asheskingdoms.commands.KingdomClaimCommand;
import com.odaishi.asheskingdoms.commands.KingdomCommand;
import com.odaishi.asheskingdoms.commands.KingdomMemberCommand;
import com.odaishi.asheskingdoms.noapi.NoApi;
import com.odaishi.asheskingdoms.noapi.ReflectionNoApiImpl;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import com.odaishi.asheskingdoms.kingdoms.*;
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
			loadData();
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			saveData();
		});

		// Initialize NoApi
		try {
			noApi = new ReflectionNoApiImpl() {
				@Override
				public boolean tryAdd(UUID player, long amount) {
					return false;
				}

				@Override
				public boolean tryRemove(UUID player, long amount) {
					return false;
				}

				@Override
				public long getBalance(UUID player) {
					return 0;
				}
			};
		} catch (Exception e) {
			noApi = null;
			System.out.println("[AshesKingdoms] Numismatic Overhaul not detected. NoApi disabled.");
		}

		System.out.println("[AshesKingdoms] Mod initialized successfully.");
	}

	/** Save kingdoms data to file */
	public void saveData() {
		if (server != null) {
			try {
				File saveFile = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "kingdoms.json");
				KingdomManager.saveToFile(saveFile);
				System.out.println("[AshesKingdoms] Kingdom data saved.");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/** Load kingdoms data from file */
	public void loadData() {
		if (server != null) {
			try {
				File saveFile = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "kingdoms.json");
				if (saveFile.exists()) {
					KingdomManager.loadFromFile(saveFile);
					System.out.println("[AshesKingdoms] Kingdom data loaded.");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public MinecraftServer getServer() {
		return server;
	}
}
