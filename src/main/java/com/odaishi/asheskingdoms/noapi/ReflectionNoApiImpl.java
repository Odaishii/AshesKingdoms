package com.odaishi.asheskingdoms.noapi;

import net.minecraft.entity.player.PlayerEntity;

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

    /**
     * Optional helper for consistency: safely add money.
     */
    @Override
    public boolean tryAdd(PlayerEntity player, long bronze) {
        return deposit(player, bronze);
    }
}
