/**
 * NUMISMATIC OVERHAUL API INTERFACE
 *
 * Core interface defining currency operations for the AshesKingdoms mod.
 * Provides a consistent API for interacting with Numismatic Overhaul's economy system.
 *
 * OPERATIONS:
 * - Balance checking for players and UUIDs
 * - Currency deposits and withdrawals
 * - Balance modification with transaction safety
 * - Availability checking for NO integration
 *
 * CURRENCY UNITS:
 * - All amounts are in bronze units (base currency)
 * - 100 bronze = 1 silver
 * - 100 silver = 1 gold (10,000 bronze)
 *
 * ERROR HANDLING:
 * - Methods return false on failure rather than throwing exceptions
 * - Safe defaults for missing NO dependency
 * - Graceful degradation when NO not installed
 *
 * IMPLEMENTATIONS:
 * - Reflection-based bridge for NO integration
 * - Stub implementation for fallback operation
 * - Runtime selection based on NO availability
 */

package com.odaishi.asheskingdoms.noapi;

import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public interface NoApi {
    // PlayerEntity-based methods
    long getBalance(PlayerEntity player);
    boolean deposit(PlayerEntity player, long bronze);
    boolean withdraw(PlayerEntity player, long bronze);
    boolean setBalance(PlayerEntity player, long bronze);

    // UUID-based methods
    long getBalance(UUID playerId);
    boolean deposit(UUID playerId, long bronze);
    boolean withdraw(UUID playerId, long bronze);
    boolean setBalance(UUID playerId, long bronze);

    // Transaction methods
    boolean tryAdd(UUID player, long amount);
    boolean tryRemove(UUID player, long amount);
    boolean tryRemove(PlayerEntity player, long bronze);
    boolean tryAdd(PlayerEntity player, long bronze);

    // Availability check
    boolean isAvailable();
}