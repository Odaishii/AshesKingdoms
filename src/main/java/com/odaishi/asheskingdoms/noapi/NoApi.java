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