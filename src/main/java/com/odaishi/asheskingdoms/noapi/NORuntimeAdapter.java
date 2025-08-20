package com.odaishi.asheskingdoms.noapi;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * Reflection/MethodHandle bridge into Numismatic Overhaul runtime.
 *
 * Resolves multiple NO versions without a hard compile-time dependency.
 */
public final class NORuntimeAdapter implements NoApi {
    private static final NORuntimeAdapter INSTANCE = new NORuntimeAdapter();
    private static MinecraftServer server;

    public static NORuntimeAdapter get() { return INSTANCE; }
    public static void setServer(MinecraftServer server) { NORuntimeAdapter.server = server; }

    private final boolean present;

    // Handles into NO internals
    private Class<?> componentsClass;
    private Object currencyComponentKey;
    private MethodHandle currencyGetFromPlayer;

    private Class<?> currencyComponentClass;
    private MethodHandle getBalance;
    private MethodHandle setBalance;
    private MethodHandle deposit;
    private MethodHandle withdraw;

    private NORuntimeAdapter() {
        this.present = FabricLoader.getInstance().isModLoaded("numismatic-overhaul");
        if (!present) return;

        try {
            var lookup = MethodHandles.lookup();

            String[] roots = new String[]{
                    "com.glisco.numismaticoverhaul",
                    "io.wispforest.numismaticoverhaul"
            };

            Class<?> playerClass = PlayerEntity.class;

            boolean resolved = false;
            for (String root : roots) {
                try {
                    componentsClass = Class.forName(root + ".NumismaticOverhaulComponents");
                } catch (ClassNotFoundException ignore) { continue; }

                String[] keyFieldNames = new String[]{"CURRENCY", "PLAYER_CURRENCY", "CURRENCY_COMPONENT"};
                Object key = null;
                for (String f : keyFieldNames) {
                    try {
                        var field = componentsClass.getDeclaredField(f);
                        field.setAccessible(true);
                        key = field.get(null);
                        if (key != null) break;
                    } catch (Throwable ignored) {}
                }
                if (key == null) continue;

                currencyComponentKey = key;

                Class<?> componentKeyClass = currencyComponentKey.getClass().getInterfaces().length > 0
                        ? currencyComponentKey.getClass().getInterfaces()[0]
                        : currencyComponentKey.getClass();
                try {
                    currencyGetFromPlayer = lookup.findVirtual(componentKeyClass, "get",
                            MethodType.methodType(Object.class, playerClass));
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    currencyGetFromPlayer = lookup.findVirtual(componentKeyClass, "get",
                            MethodType.methodType(Object.class, Object.class));
                }

                String[] ccCandidates = new String[]{
                        root + ".currency.CurrencyComponent",
                        root + ".currency.PlayerCurrencyComponent",
                        root + ".component.CurrencyComponent"
                };
                for (String cn : ccCandidates) {
                    try { currencyComponentClass = Class.forName(cn); break; }
                    catch (ClassNotFoundException ignored) {}
                }
                if (currencyComponentClass == null) continue;

                MethodHandle getBal = null, setBal = null, dep = null, wit = null;
                try { getBal = lookup.findVirtual(currencyComponentClass, "getBalance", MethodType.methodType(long.class)); } catch (Throwable ignored) {}
                try { setBal = lookup.findVirtual(currencyComponentClass, "setBalance", MethodType.methodType(void.class, long.class)); } catch (Throwable ignored) {}
                try { dep = lookup.findVirtual(currencyComponentClass, "deposit", MethodType.methodType(void.class, long.class)); } catch (Throwable ignored) {}
                try { wit = lookup.findVirtual(currencyComponentClass, "withdraw", MethodType.methodType(boolean.class, long.class)); } catch (Throwable ignored) {}

                if (getBal == null) try { getBal = lookup.findVirtual(currencyComponentClass, "get", MethodType.methodType(long.class)); } catch (Throwable ignored) {}
                if (setBal == null) try { setBal = lookup.findVirtual(currencyComponentClass, "set", MethodType.methodType(void.class, long.class)); } catch (Throwable ignored) {}

                if (getBal != null && (setBal != null || (dep != null && wit != null))) {
                    this.getBalance = getBal;
                    this.setBalance = setBal;
                    this.deposit = dep;
                    this.withdraw = wit;
                    resolved = true;
                }

                if (resolved) break;
            }

            if (!resolved) NOLog.warn("Could not resolve NO internals. API will be inert.");
            else NOLog.info("Resolved NO internals: component=%s", currencyComponentClass.getName());

        } catch (Throwable t) {
            NOLog.error("Failed initializing NO adapter: %s", t);
        }
    }

    private Object purse(PlayerEntity player) {
        try {
            if (currencyGetFromPlayer == null) return null;
            return currencyGetFromPlayer.invoke(currencyComponentKey, player);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public long getBalance(PlayerEntity player) {
        if (!present) return 0;
        Object p = purse(player);
        if (p == null || getBalance == null) return 0;
        try { return (long) getBalance.invoke(p); } catch (Throwable t) { return 0; }
    }

    @Override
    public boolean setBalance(PlayerEntity player, long totalBronze) {
        if (!present) return false;
        Object p = purse(player);
        if (p == null) return false;
        long before = getBalance(player);
        try {
            if (setBalance != null) setBalance.invoke(p, totalBronze);
            else if (withdraw != null && deposit != null) {
                long cur = before;
                if (cur > 0) withdraw.invoke(p, cur);
                if (totalBronze > 0) deposit.invoke(p, totalBronze);
            } else return false;
        } catch (Throwable t) { return false; }
        NoEvents.BALANCE_CHANGED.invoker().onChanged(player, before, totalBronze);
        return true;
    }

    @Override
    public boolean tryAdd(UUID playerId, long amount) {
        if (server == null || !present || amount <= 0) return false;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) return false;

        return deposit(player, amount);
    }

    @Override
    public boolean tryRemove(UUID playerId, long amount) {
        if (server == null || !present || amount <= 0) return false;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) return false;

        return withdraw(player, amount);
    }

    @Override
    public long getBalance(UUID playerId) {
        if (server == null || !present) return 0;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) return 0;

        return getBalance(player);
    }

    @Override
    public boolean tryRemove(PlayerEntity player, long amount) {
        return withdraw(player, amount);
    }

    @Override
    public boolean tryAdd(PlayerEntity player, long amount) {
        return deposit(player, amount);
    }

    @Override
    public boolean deposit(PlayerEntity player, long bronzeAmount) {
        if (!present || bronzeAmount <= 0) return false;
        Object p = purse(player);
        if (p == null || deposit == null) return false;
        long before = getBalance(player);
        try { deposit.invoke(p, bronzeAmount); } catch (Throwable t) { return false; }
        long after = getBalance(player);
        NoEvents.BALANCE_CHANGED.invoker().onChanged(player, before, after);
        return true;
    }

    @Override
    public boolean withdraw(PlayerEntity player, long bronzeAmount) {
        if (!present || bronzeAmount <= 0) return false;
        Object p = purse(player);
        if (p == null || withdraw == null) return false;
        long before = getBalance(player);
        boolean ok;
        try { ok = (boolean) withdraw.invoke(p, bronzeAmount); } catch (Throwable t) { return false; }
        if (ok) {
            long after = getBalance(player);
            NoEvents.BALANCE_CHANGED.invoker().onChanged(player, before, after);
        }
        return ok;
    }
}