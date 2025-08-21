/**
 * NUMISMATIC OVERHAUL EVENT SYSTEM
 *
 * Event definitions for Numismatic Overhaul currency-related notifications.
 * Provides hooks for other mods or systems to react to balance changes
 * made through the NO integration API.
 *
 * EVENTS:
 * - BALANCE_CHANGED: Triggered after successful currency modifications
 *   Parameters: PlayerEntity, previous balance, new balance
 *
 * ARCHITECTURE:
 * - Uses Fabric's EventFactory for efficient event dispatching
 * - Array-backed listener system for performance
 * - Functional interface for clean event handling
 *
 * USE CASES:
 * - Audit logging of currency transactions
 * - Achievement systems tracking wealth
 * - Economy plugins monitoring cash flow
 * - Debugging and transaction verification
 *
 * INTEGRATION:
 * - Other mods can register listeners without direct API dependency
 * - Provides complete transaction context (player, before, after)
 * - Thread-safe event dispatching
 */

package com.odaishi.asheskingdoms.noapi;


import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.player.PlayerEntity;


/** Fired after a balance change is applied via this API. */
public final class NoEvents {
    private NoEvents() {}


    @FunctionalInterface
    public interface BalanceChanged {
        void onChanged(PlayerEntity player, long before, long after);
    }


    public static final Event<BalanceChanged> BALANCE_CHANGED =
            EventFactory.createArrayBacked(BalanceChanged.class, listeners -> (p, b, a) -> {
                for (var l : listeners) l.onChanged(p, b, a);
            });
}