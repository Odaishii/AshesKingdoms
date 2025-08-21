/**
 * LEGACY CURRENCY INVENTORY MANAGEMENT UTILITY
 *
 * DEPRECATED - This class provides item-based coin handling as a fallback
 * when Numismatic Overhaul is not available. Primary economy operations should
 * use the NORuntimeAdapter for direct currency component access.
 *
 * FUNCTIONALITY:
 * - Coin counting and conversion between denominations
 * - Coin removal with optimal denomination selection
 * - Coin addition with proper denomination distribution
 * - Inventory and overflow handling
 *
 * DENOMINATION SYSTEM:
 * - 1 Gold = 10,000 Bronze
 * - 1 Silver = 100 Bronze
 * - 1 Bronze = 1 Bronze (base unit)
 *
 * OPERATIONS:
 * - countCoins(): Total bronze value of all coins in inventory
 * - removeCoins(): Removes coins up to specified bronze amount
 * - addCoins(): Adds coins using optimal denomination distribution
 * - getCoinBreakdown(): Returns gold/silver/bronze composition
 *
 * USAGE SCENARIOS:
 * - Fallback when Numismatic Overhaul is not installed
 * - Legacy save file compatibility
 * - Testing environments without NO dependency
 * - Emergency fallback for currency operations
 *
 * LIMITATIONS:
 * - Item-based (requires physical coin items in inventory)
 * - No support for NO's currency component features
 * - Limited to 3 coin types (gold, silver, bronze)
 * - No event system for balance changes
 *
 * INTEGRATION:
 * - Automatically falls back when NO not detected
 * - Used by older economy system implementations
 * - Maintains backward compatibility
 */

package com.odaishi.asheskingdoms.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public class InventoryCoins {

    private static final String BRONZE_ID = "numismatic-overhaul:bronze_coin";
    private static final String SILVER_ID = "numismatic-overhaul:silver_coin";
    private static final String GOLD_ID   = "numismatic-overhaul:gold_coin";

    public static long countCoins(PlayerEntity player) {
        long totalBronze = 0;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            String itemString = itemId.toString();

            if (itemString.equals(BRONZE_ID)) {
                totalBronze += stack.getCount();
            } else if (itemString.equals(SILVER_ID)) {
                totalBronze += stack.getCount() * 100L;
            } else if (itemString.equals(GOLD_ID)) {
                totalBronze += stack.getCount() * 10000L;
            }
        }

        return totalBronze;
    }

    public static long[] getCoinBreakdown(PlayerEntity player) {
        long totalBronze = countCoins(player);
        long gold = totalBronze / 10000;
        long silver = (totalBronze % 10000) / 100;
        long bronze = totalBronze % 100;

        return new long[]{gold, silver, bronze};
    }

    /**
     * Removes up to a specific amount of coins (in bronze units) from the player's inventory.
     * Returns the actual amount removed (may be more than requested due to coin denominations).
     */
    /**
     * Removes up to a specific amount of coins (in bronze units) from the player's inventory.
     * Returns the actual amount removed (may be more than requested due to coin denominations).
     */
    public static long removeCoins(PlayerEntity player, long bronzeAmount) {
        long coinsBefore = countCoins(player);
        long remaining = bronzeAmount;

        // Remove gold coins first (most valuable) - use ceiling division to ensure we remove enough
        remaining = removeCoinType(player, GOLD_ID, remaining, 10000L);
        if (remaining <= 0) {
            long coinsAfter = countCoins(player);
            return coinsBefore - coinsAfter; // Return actual amount removed
        }

        // Then silver coins
        remaining = removeCoinType(player, SILVER_ID, remaining, 100L);
        if (remaining <= 0) {
            long coinsAfter = countCoins(player);
            return coinsBefore - coinsAfter; // Return actual amount removed
        }

        // Finally bronze coins
        remaining = removeCoinType(player, BRONZE_ID, remaining, 1L);

        long coinsAfter = countCoins(player);
        return coinsBefore - coinsAfter; // Return actual amount removed
    }

    private static long removeCoinType(PlayerEntity player, String coinId, long remaining, long coinValue) {
        if (remaining <= 0) return 0;

        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            String itemString = itemId.toString();

            if (itemString.equals(coinId)) {
                // Use ceiling division to ensure we remove at least the required amount
                int coinsToRemove = (int) Math.min(stack.getCount(), (remaining + coinValue - 1) / coinValue);

                if (coinsToRemove > 0) {
                    stack.decrement(coinsToRemove);
                    remaining -= coinsToRemove * coinValue;
                }
            }
        }

        return remaining;
    }

    /**
     * Helper method to add coins to player inventory (for giving change)
     */
    public static void addCoins(PlayerEntity player, long bronzeAmount) {
        long remaining = bronzeAmount;

        // Add gold coins first
        if (remaining >= 10000) {
            int goldCoins = (int)(remaining / 10000);
            addCoinItem(player, GOLD_ID, goldCoins);
            remaining -= goldCoins * 10000L;
        }

        // Add silver coins
        if (remaining >= 100) {
            int silverCoins = (int)(remaining / 100);
            addCoinItem(player, SILVER_ID, silverCoins);
            remaining -= silverCoins * 100L;
        }

        // Add bronze coins
        if (remaining > 0) {
            addCoinItem(player, BRONZE_ID, (int)remaining);
        }
    }

    private static void addCoinItem(PlayerEntity player, String coinId, int count) {
        if (count <= 0) return;

        try {
            Identifier identifier = Identifier.of(coinId);
            net.minecraft.item.Item coinItem = Registries.ITEM.get(identifier);

            if (coinItem != null) {
                net.minecraft.item.ItemStack coinStack = new net.minecraft.item.ItemStack(coinItem, count);

                // Try to add to inventory first
                if (!player.getInventory().insertStack(coinStack)) {
                    // If inventory is full, drop at player's position
                    player.dropItem(coinStack, false);
                }
            }
        } catch (Exception e) {
            // Silently fail - we don't want to spam error messages for coin adding
        }
    }
}