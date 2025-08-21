/**
 * ASHES KINGDOMS MOD - FABRIC 1.21.1
 * ====================================
 *
 * MOD OVERVIEW:
 * A comprehensive TownyAdvanced-style territory claiming system with advanced kingdom management,
 * economy integration, and warfare mechanics. Built for Fabric 1.21.1 with Numismatic Overhaul
 * as the primary currency system.
 *
 * CORE SYSTEMS:
 * - Territory Claiming: Chunk-based claiming system with adjacency requirements and claim limits
 * - Kingdom Management: Create, manage, and disband kingdoms with hierarchical member ranks
 * - Economy Integration: Full Numismatic Overhaul support for all transactions (bronze/silver/gold)
 * - War & Alliances: Planned warfare system with kingdom alliances, enemies, and diplomatic relations
 * - Personal Claims: Individual player permissions within kingdom territory
 * - Rank Hierarchy: Leader > Assistant > Officer > Member > Ally > Outsider permission system
 * - Settings System: Configurable kingdom-wide settings for mob spawning, fire spread, TNT, etc.
 * - Personal Claims: Player-specific chunk ownership within kingdom territory
 *
 * KEY FEATURES:
 * - Kingdom creation with configurable costs
 * - Chunk claiming with adjacency rules and economy costs
 * - Member invitation system with expiration
 * - Rank-based permissions system
 * - Personal claims for fine-grained access control
 * - Kingdom settings and customization
 * - JSON-based persistence
 * - Deletion confirmation system
 * - Automatic cleanup of expired personal claims
 *
 * ECONOMY INTEGRATION:
 * - Uses Numismatic Overhaul currency (bronze coins as base unit)
 * - Creation cost: Configurable amount (typically gold-based)
 * - Claiming cost: 10 silver (1000 bronze) per chunk after initial claim
 * - Personal claim costs: Configurable per-claim fees
 * - Future planned: War costs, alliance fees, upkeep costs
 *
 * PLANNED WAR SYSTEM:
 * - Kingdom vs kingdom warfare declarations
 * - Siege mechanics for claimed territory
 * - War chests and resource requirements
 * - Temporary truces and peace treaties
 * - War score and victory conditions
 *
 * PLANNED ALLIANCE SYSTEM:
 * - Formal alliance agreements
 * - Shared territory access between allies
 * - Mutual defense pacts
 * - Alliance-specific permissions
 * - Alliance-wide economy contributions
 *
 * TECHNICAL ARCHITECTURE:
 * - Server-side only implementation
 * - Thread-safe for multiplayer environments
 * - JSON persistence with automatic save/load
 * - Event-driven architecture for mod compatibility
 * - Permission-based command system
 * - Regular cleanup of expired invites and personal claims
 *
 * DEPENDENCIES:
 * - Fabric API 1.21.1
 * - Numismatic Overhaul (currency system)
 * - Minecraft 1.21.1
 *
 * PERMISSION HIERARCHY:
 * Leader: Full permissions, kingdom deletion, rank assignments, settings management
 * Assistant: Settings management, rank assignments (except leader), claiming, personal claim oversight
 * Officer: Member management, claiming, basic administration
 * Member: Basic interactions, personal claims within their limits
 * Ally: Limited territory access (planned)
 * Outsider: No permissions
 *
 * FILE PURPOSE:
 * This is the main mod initialization class that coordinates all systems including:
 * - Command registration
 * - Event handling for protection and settings enforcement
 * - Server lifecycle management
 * - Data persistence
 * - Regular maintenance tasks (cleanup of expired claims/invites)
 */

package com.odaishi.asheskingdoms;

import com.odaishi.asheskingdoms.commands.KingdomClaimCommand;
import com.odaishi.asheskingdoms.commands.KingdomCommand;
import com.odaishi.asheskingdoms.commands.KingdomMemberCommand;
import com.odaishi.asheskingdoms.commands.KingdomSettingsCommand;
import com.odaishi.asheskingdoms.commands.KingdomPersonalClaimCommand;
import com.odaishi.asheskingdoms.kingdoms.Kingdom;
import com.odaishi.asheskingdoms.noapi.NoApi;
import com.odaishi.asheskingdoms.noapi.NORuntimeAdapter;
import com.odaishi.asheskingdoms.kingdoms.KingdomManager;
import com.odaishi.asheskingdoms.utils.ModConfig;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.WorldSavePath;
import com.odaishi.asheskingdoms.commands.KingdomWarCommand;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AshesKingdoms implements ModInitializer {

	public static NoApi noApi;
	public static final String MOD_ID = "asheskingdoms";
	private static AshesKingdoms INSTANCE;
	private MinecraftServer server;
	private ScheduledExecutorService scheduler;

	public static AshesKingdoms getInstance() {
		return INSTANCE;
	}

	@Override
	public void onInitialize() {
		INSTANCE = this;

		// Load configuration
		ModConfig.loadConfig(new File("config"));

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			KingdomCommand.register(dispatcher);
			KingdomMemberCommand.register(dispatcher);
			KingdomClaimCommand.register(dispatcher);
			KingdomWarCommand.register(dispatcher);
			KingdomSettingsCommand.register(dispatcher);
			KingdomPersonalClaimCommand.register(dispatcher);
		});

		// Server lifecycle hooks
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			this.server = server;
			KingdomManager.setServer(server);
			loadData();
			registerProtectionEvents();
			startCleanupScheduler();
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			stopCleanupScheduler();
			saveData();
		});

		System.out.println("[AshesKingdoms] Using item-based economy system.");
		System.out.println("[AshesKingdoms] Mod initialized successfully.");
	}

	private void startCleanupScheduler() {
		scheduler = Executors.newScheduledThreadPool(1);
		// Run cleanup every 5 minutes
		scheduler.scheduleAtFixedRate(() -> {
			if (server != null && !server.isStopped()) {
				server.execute(() -> {
					KingdomManager.cleanupExpired();
					System.out.println("[AshesKingdoms] Cleaned up expired invites and personal claims");
				});
			}
		}, 5, 5, TimeUnit.MINUTES);
	}

	private void stopCleanupScheduler() {
		if (scheduler != null) {
			scheduler.shutdown();
			try {
				if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
					scheduler.shutdownNow();
				}
			} catch (InterruptedException e) {
				scheduler.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	private void registerProtectionEvents() {
		// Enhanced protection events that respect personal claims and settings

		// Block placement protection with personal claim support
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.isCreative()) return ActionResult.PASS;

			Kingdom kingdom = KingdomManager.getKingdomAt(new net.minecraft.util.math.ChunkPos(pos));
			if (kingdom != null) {
				// Check personal claims first
				if (kingdom.hasPersonalClaim(new net.minecraft.util.math.ChunkPos(pos))) {
					if (!kingdom.hasPersonalClaimAccess(player.getUuid(), new net.minecraft.util.math.ChunkPos(pos))) {
						player.sendMessage(net.minecraft.text.Text.of("§cThis area is personally claimed by someone else!"), false);
						return ActionResult.FAIL;
					}
					return ActionResult.PASS; // Personal claim access granted
				}

				// Then check kingdom permissions
				if (!kingdom.hasPermission(player, "build")) {
					player.sendMessage(net.minecraft.text.Text.of("§cYou don't have permission to build here!"), false);
					return ActionResult.FAIL;
				}
			}
			return ActionResult.PASS;
		});

		// Block breaking protection with personal claim support
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.isCreative()) return ActionResult.PASS;

			Kingdom kingdom = KingdomManager.getKingdomAt(new net.minecraft.util.math.ChunkPos(pos));
			if (kingdom != null) {
				// Check personal claims first
				if (kingdom.hasPersonalClaim(new net.minecraft.util.math.ChunkPos(pos))) {
					if (!kingdom.hasPersonalClaimAccess(player.getUuid(), new net.minecraft.util.math.ChunkPos(pos))) {
						player.sendMessage(net.minecraft.text.Text.of("§cThis area is personally claimed by someone else!"), false);
						return ActionResult.FAIL;
					}
					return ActionResult.PASS; // Personal claim access granted
				}

				// Then check kingdom permissions
				if (!kingdom.hasPermission(player, "destroy")) {
					player.sendMessage(net.minecraft.text.Text.of("§cYou don't have permission to break blocks here!"), false);
					return ActionResult.FAIL;
				}
			}
			return ActionResult.PASS;
		});

		// Enhanced container protection with settings check
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player.isCreative()) return ActionResult.PASS;

			net.minecraft.util.math.BlockPos pos = hitResult.getBlockPos();
			net.minecraft.block.BlockState state = world.getBlockState(pos);

			Kingdom kingdom = KingdomManager.getKingdomAt(new net.minecraft.util.math.ChunkPos(pos));
			if (kingdom != null) {
				// Check personal claims first
				if (kingdom.hasPersonalClaim(new net.minecraft.util.math.ChunkPos(pos))) {
					if (!kingdom.hasPersonalClaimAccess(player.getUuid(), new net.minecraft.util.math.ChunkPos(pos))) {
						return ActionResult.FAIL;
					}
					return ActionResult.PASS; // Personal claim access granted
				}

				// Check if it's a container and kingdom settings allow access
				if (state.getBlock() instanceof net.minecraft.block.ChestBlock ||
						state.getBlock() instanceof net.minecraft.block.BarrelBlock ||
						state.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock ||
						state.getBlock() instanceof net.minecraft.block.HopperBlock ||
						state.getBlock() instanceof net.minecraft.block.DispenserBlock ||
						state.getBlock() instanceof net.minecraft.block.DropperBlock) {

					if (!kingdom.hasPermission(player, "container")) {
						player.sendMessage(net.minecraft.text.Text.of("§cYou don't have permission to open containers here!"), false);
						return ActionResult.FAIL;
					}
				}

				// Door protection with settings check
				if (state.getBlock() instanceof net.minecraft.block.DoorBlock ||
						state.getBlock() instanceof net.minecraft.block.FenceGateBlock ||
						state.getBlock() instanceof net.minecraft.block.TrapdoorBlock) {

					if (!kingdom.hasPermission(player, "door")) {
						player.sendMessage(net.minecraft.text.Text.of("§cYou don't have permission to use doors here!"), false);
						return ActionResult.FAIL;
					}
				}

				// Switch/button protection
				if (state.getBlock() instanceof net.minecraft.block.LeverBlock ||
						state.getBlock() instanceof net.minecraft.block.ButtonBlock) {

					if (!kingdom.hasPermission(player, "switch")) {
						player.sendMessage(net.minecraft.text.Text.of("§cYou don't have permission to use switches here!"), false);
						return ActionResult.FAIL;
					}
				}
			}
			return ActionResult.PASS;
		});

		// PVP protection with kingdom settings enforcement
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.isCreative()) return ActionResult.PASS;
			if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) return ActionResult.PASS;

			Kingdom kingdom = KingdomManager.getKingdomAt(new net.minecraft.util.math.ChunkPos(entity.getBlockPos()));
			if (kingdom != null) {
				// Check kingdom settings for PVP
				if (!kingdom.getSettings().getSetting("pvp")) {
					player.sendMessage(net.minecraft.text.Text.of("§cPVP is disabled in this kingdom!"), false);
					return ActionResult.FAIL;
				}
			}
			return ActionResult.PASS;
		});

		// Mob damage protection with settings check
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.isCreative()) return ActionResult.PASS;
			if (entity instanceof net.minecraft.entity.player.PlayerEntity) return ActionResult.PASS;

			Kingdom kingdom = KingdomManager.getKingdomAt(new net.minecraft.util.math.ChunkPos(entity.getBlockPos()));
			if (kingdom != null && !kingdom.hasPermission(player, "mobDamage")) {
				player.sendMessage(net.minecraft.text.Text.of("§cYou can't damage mobs here!"), false);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
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