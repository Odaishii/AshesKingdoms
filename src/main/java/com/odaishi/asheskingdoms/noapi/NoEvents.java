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