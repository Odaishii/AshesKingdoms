package com.odaishi.asheskingdoms.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;

public class InventoryCoins {

    private static final String BRONZE_ID = "numismatic-overhaul:bronze_coin";
    private static final String SILVER_ID = "numismatic-overhaul:silver_coin";
    private static final String GOLD_ID   = "numismatic-overhaul:gold_coin";

    public static long countCoins(PlayerEntity player) {
        long totalBronze = 0;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            // FIXED: Use proper item ID comparison
            Identifier itemId = Registry.ITEM.getId(stack.getItem());
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
     * Removes a specific amount of coins (in bronze units) from the player's inventory.
     */
    public static void removeCoins(PlayerEntity player, long bronzeAmount) {
        long remaining = bronzeAmount;

        // Remove gold coins first (most valuable)
        remaining = removeCoinType(player, GOLD_ID, remaining, 10000L);
        if (remaining <= 0) return;

        // Then silver coins
        remaining = removeCoinType(player, SILVER_ID, remaining, 100L);
        if (remaining <= 0) return;

        // Finally bronze coins
        removeCoinType(player, BRONZE_ID, remaining, 1L);
    }

    private static long removeCoinType(PlayerEntity player, String coinId, long remaining, long coinValue) {
        if (remaining <= 0) return 0;

        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Identifier itemId = Registry.ITEM.getId(stack.getItem());
            String itemString = itemId.toString();

            if (itemString.equals(coinId)) {
                // Calculate how many coins of this type we need to remove
                int coinsToRemove = (int) Math.min(stack.getCount(), (remaining + coinValue - 1) / coinValue);

                if (coinsToRemove > 0) {
                    stack.decrement(coinsToRemove);
                    remaining -= coinsToRemove * coinValue;
                }
            }
        }

        return remaining;
    }
}