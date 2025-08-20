package com.odaishi.asheskingdoms.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class InventoryCoins {

    private static final String BRONZE_ID = "numismatic-overhaul:bronze_coin";
    private static final String SILVER_ID = "numismatic-overhaul:silver_coin";
    private static final String GOLD_ID   = "numismatic-overhaul:gold_coin";

    public static long countCoins(PlayerEntity player) {
        long totalBronze = 0;

        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) continue;

            String itemId = stack.getItem().toString();
            if (itemId.equals(BRONZE_ID)) totalBronze += stack.getCount();
            else if (itemId.equals(SILVER_ID)) totalBronze += stack.getCount() * 100L;
            else if (itemId.equals(GOLD_ID)) totalBronze += stack.getCount() * 10000L;
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
        long[] coins = getCoinBreakdown(player);
        long gold = coins[0];
        long silver = coins[1];
        long bronze = coins[2];

        // Convert requested amount to gold/silver/bronze
        long remaining = bronzeAmount;

        // Deduct gold first
        long goldValue = gold * 10000L;
        if (remaining >= goldValue) {
            remaining -= goldValue;
            gold = 0;
        } else {
            long goldUsed = remaining / 10000;
            remaining -= goldUsed * 10000;
            gold -= goldUsed;
        }

        // Deduct silver next
        long silverValue = silver * 100L;
        if (remaining >= silverValue) {
            remaining -= silverValue;
            silver = 0;
        } else {
            long silverUsed = remaining / 100;
            remaining -= silverUsed * 100;
            silver -= silverUsed;
        }

        // Deduct bronze
        if (remaining >= bronze) {
            remaining -= bronze;
            bronze = 0;
        } else {
            bronze -= remaining;
            remaining = 0;
        }

        // Now update inventory
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) continue;

            String itemId = stack.getItem().toString();
            if (itemId.equals(GOLD_ID) && gold < stack.getCount()) {
                stack.decrement(stack.getCount() - (int) gold);
                gold = 0;
            } else if (itemId.equals(GOLD_ID)) {
                stack.decrement((int) stack.getCount());
            }

            if (itemId.equals(SILVER_ID) && silver < stack.getCount()) {
                stack.decrement(stack.getCount() - (int) silver);
                silver = 0;
            } else if (itemId.equals(SILVER_ID)) {
                stack.decrement((int) stack.getCount());
            }

            if (itemId.equals(BRONZE_ID) && bronze < stack.getCount()) {
                stack.decrement(stack.getCount() - (int) bronze);
                bronze = 0;
            } else if (itemId.equals(BRONZE_ID)) {
                stack.decrement((int) stack.getCount());
            }
        }
    }
}
