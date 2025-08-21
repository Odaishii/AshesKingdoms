/**
 * NUMISMATIC OVERHAUL API ACCESS FACADE
 *
 * Singleton access point for Numismatic Overhaul currency operations.
 * Provides a unified interface that automatically selects the appropriate
 * implementation based on NO availability at runtime.
 *
 * FEATURES:
 * - Automatic implementation selection (Reflection vs Stub)
 * - Singleton pattern for consistent API access
 * - Runtime dependency detection and adaptation
 * - Fallback to inert stub when NO not available
 *
 * IMPLEMENTATION STRATEGY:
 * - Reflection-based implementation when NO present
 * - Null-object pattern stub when NO absent
 * - Lazy initialization for performance
 *
 * ERROR HANDLING:
 * - Returns safe defaults (0 balance, false for operations)
 * - No exceptions thrown for missing dependency
 * - Graceful degradation of functionality
 *
 * USAGE:
 * Primary access point for all currency operations throughout the mod.
 * Ensures consistent behavior regardless of NO installation status.
 */

package com.odaishi.asheskingdoms.noapi;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public class NoApiAccess {
    private static NoApi INSTANCE;

    public static NoApi get() {
        if (INSTANCE == null) {
            if (FabricLoader.getInstance().isModLoaded("numismatic-overhaul")) {
                INSTANCE = new ReflectionNoApiImpl() {
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
                }; // The reflective bridge we wrote
            } else {
                INSTANCE = new NoApi() {
                    @Override public long getBalance(net.minecraft.entity.player.PlayerEntity player) { return 0; }
                    @Override public boolean deposit(net.minecraft.entity.player.PlayerEntity player, long bronze) { return false; }
                    @Override public boolean withdraw(net.minecraft.entity.player.PlayerEntity player, long bronze) { return false; }
                    @Override public boolean setBalance(net.minecraft.entity.player.PlayerEntity player, long bronze) { return false; }

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

                    @Override
                    public boolean tryRemove(PlayerEntity player, long bronze) {
                        return false;
                    }

                    @Override
                    public boolean tryAdd(PlayerEntity player, long bronze) {
                        return false;
                    }
                };
            }
        }
        return INSTANCE;
    }
}