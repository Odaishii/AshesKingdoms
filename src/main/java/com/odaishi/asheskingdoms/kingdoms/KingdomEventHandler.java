/**
 * EVENT HANDLER FOR KINGDOM SETTINGS ENFORCEMENT
 *
 * This class handles Minecraft events to enforce kingdom settings and personal claims.
 * It intercepts various game events and checks if they should be allowed based on
 * kingdom rules and personal claim permissions.
 */
package com.odaishi.asheskingdoms.kingdoms;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

public class KingdomEventHandler {

    public static void registerEvents() {
        // Block interaction events
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;

            Kingdom kingdom = KingdomManager.getKingdomAt(new ChunkPos(hitResult.getBlockPos()));
            if (kingdom != null) {
                // Check personal claims first
                if (kingdom.hasPersonalClaim(new ChunkPos(hitResult.getBlockPos()))) {
                    if (!kingdom.hasPersonalClaimAccess(player.getUuid(), new ChunkPos(hitResult.getBlockPos()))) {
                        return ActionResult.FAIL;
                    }
                }
                // Then check kingdom permissions
                else if (!kingdom.hasPermission(player, "build")) {
                    player.sendMessage(net.minecraft.text.Text.of("§cYou don't have permission to build here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // PvP prevention
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || !(entity instanceof PlayerEntity)) return ActionResult.PASS;

            Kingdom kingdom = KingdomManager.getKingdomAt(new ChunkPos(entity.getBlockPos()));
            if (kingdom != null && !kingdom.getSettings().getSetting("pvp")) {
                player.sendMessage(net.minecraft.text.Text.of("§cPvP is disabled in this kingdom!"), false);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}