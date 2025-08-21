/**
 * NUMISMATIC OVERHAUL API INTERFACE
 *
 * Core interface defining all currency operations for Numismatic Overhaul integration.
 * Provides a contract for both player-based and UUID-based currency transactions
 * with comprehensive balance management capabilities.
 *
 * OPERATION CATEGORIES:
 * - Balance queries: getBalance() methods
 * - Direct transactions: deposit(), withdraw(), setBalance()
 * - Safe transactions: tryAdd(), tryRemove() with validation
 * - Dual identification: Support for both PlayerEntity and UUID targets
 *
 * DESIGN PRINCIPLES:
 * - Player-centric and UUID-centric method overloads
 * - Transaction safety with boolean return codes
 * - Bronze-based currency unit consistency
 * - Null-safe operation semantics
 *
 * IMPLEMENTATION NOTES:
 * - All amounts are in bronze units (base currency)
 * - Methods return false on failure (insufficient funds, player offline)
 * - Balance operations return 0 for invalid/unavailable players
 * - Designed for multiple implementation strategies
 *
 * USAGE CONTRACT:
 * Implementing classes must provide graceful fallback behavior
 * when Numismatic Overhaul is not available or players are offline.
 */

package com.odaishi.asheskingdoms.noapi;

import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public interface NoApi {
    long getBalance(PlayerEntity player);
    boolean deposit(PlayerEntity player, long bronze);
    boolean withdraw(PlayerEntity player, long bronze);
    boolean setBalance(PlayerEntity player, long bronze);
    boolean tryAdd(UUID player, long amount);   // add coins
    boolean tryRemove(UUID player, long amount); // remove coins
    long getBalance(UUID player);               // check balance

    boolean tryRemove(PlayerEntity player, long bronze);

    boolean tryAdd(PlayerEntity player, long bronze);
}