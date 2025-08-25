/**
 * REFLECTION-BASED NUMISMATIC OVERHAUL ADAPTER
 *
 * Provides a fallback implementation for Numismatic Overhaul integration using
 * Java reflection. This class serves as an alternative to the MethodHandle-based
 * implementation, providing compatibility with different NO versions.
 *
 * FEATURES:
 * - Reflection-based access to Numismatic Overhaul currency system
 * - Graceful fallback when NO is not installed
 * - Safe currency operations with error handling
 * - Compatibility with multiple NO versions
 *
 * OPERATIONS:
 * - Balance checking and modification
 * - Deposit and withdrawal with validation
 * - Player currency component access via reflection
 *
 * USAGE:
 * Automatically falls back to this implementation if the primary MethodHandle
 * adapter fails or when NO uses older package structures.
 *
 * ERROR HANDLING:
 * - Silently fails when NO is not available
 * - Returns false for failed operations
 * - Maintains gameplay continuity without NO dependency
 */

package com.odaishi.asheskingdoms.noapi;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

import java.lang.reflect.Method;

public abstract class ReflectionNoApiImpl implements NoApi {

    private Class<?> purseComponentClass;
    private Method getMethod;
    private Method setBalanceMethod;
    private Method addMoneyMethod;
    private Method removeMoneyMethod;

    private boolean noAvailable = false;

    public ReflectionNoApiImpl() {
        try {
            purseComponentClass = Class.forName("com.glisco.numismaticoverhaul.currency.PlayerCurrencyComponent");
            getMethod = purseComponentClass.getMethod("get", PlayerEntity.class);
            setBalanceMethod = purseComponentClass.getMethod("setBalance", long.class);
            addMoneyMethod = purseComponentClass.getMethod("add", long.class);
            removeMoneyMethod = purseComponentClass.getMethod("remove", long.class);
            noAvailable = true; // NO is present
        } catch (ClassNotFoundException e) {
            noAvailable = false; // NO not installed, fallback to internal system
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Object getPurse(PlayerEntity player) {
        if (!noAvailable) return null;
        try {
            return getMethod.invoke(null, player);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public long getBalance(PlayerEntity player) {
        if (!noAvailable) {
            // fallback: your own NoAPI storage or return 0
            return 0;
        }
        try {
            Object purse = getPurse(player);
            Method getBalanceMethod = purseComponentClass.getMethod("getBalance");
            return (long) getBalanceMethod.invoke(purse);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean deposit(PlayerEntity player, long bronze) {
        if (!noAvailable) return false;
        try {
            Object purse = getPurse(player);
            addMoneyMethod.invoke(purse, bronze);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean withdraw(PlayerEntity player, long bronze) {
        if (!noAvailable) return false;
        try {
            Object purse = getPurse(player);
            removeMoneyMethod.invoke(purse, bronze);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean setBalance(PlayerEntity player, long bronze) {
        if (!noAvailable) return false;
        try {
            Object purse = getPurse(player);
            setBalanceMethod.invoke(purse, bronze);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Safely try to remove money from a player.
     * Returns false if insufficient funds.
     */
    @Override
    public boolean tryRemove(PlayerEntity player, long bronze) {
        if (!noAvailable) return false;
        long balance = getBalance(player);
        if (balance < bronze) {
            return false; // not enough money
        }
        return withdraw(player, bronze);
    }

    @Override
    public long getBalance(UUID playerId) {
        PlayerEntity player = getPlayerFromUUID(playerId);
        if (player != null) {
            return getBalance(player);
        }
        return 0;
    }

    @Override
    public boolean deposit(UUID playerId, long bronze) {
        PlayerEntity player = getPlayerFromUUID(playerId);
        if (player != null) {
            return deposit(player, bronze);
        }
        return false;
    }

    @Override
    public boolean withdraw(UUID playerId, long bronze) {
        PlayerEntity player = getPlayerFromUUID(playerId);
        if (player != null) {
            return withdraw(player, bronze);
        }
        return false;
    }

    @Override
    public boolean setBalance(UUID playerId, long bronze) {
        PlayerEntity player = getPlayerFromUUID(playerId);
        if (player != null) {
            return setBalance(player, bronze);
        }
        return false;
    }

    @Override
    public boolean tryRemove(UUID player, long amount) {
        PlayerEntity playerEntity = getPlayerFromUUID(player);
        if (playerEntity != null) {
            return tryRemove(playerEntity, amount);
        }
        return false;
    }

    @Override
    public boolean tryAdd(UUID player, long amount) {
        PlayerEntity playerEntity = getPlayerFromUUID(player);
        if (playerEntity != null) {
            return tryAdd(playerEntity, amount);
        }
        return false;
    }

    @Override
    public boolean isAvailable() {
        return noAvailable;
    }

    // Helper method to get PlayerEntity from UUID
    private PlayerEntity getPlayerFromUUID(UUID playerId) {
        return null;
    }

    /**
     * Optional helper for consistency: safely add money.
     */
    @Override
    public boolean tryAdd(PlayerEntity player, long bronze) {
        return deposit(player, bronze);
    }
}
