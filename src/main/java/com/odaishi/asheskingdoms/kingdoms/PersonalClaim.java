/**
 * PERSONAL CLAIM SYSTEM
 *
 * This class represents an individual player's claim within kingdom territory.
 * Personal claims allow specific players to have additional permissions in
 * designated chunks of their kingdom's land.
 *
 * FEATURES:
 * - Player-specific chunk ownership within kingdom territory
 * - Time-based expiration system for temporary claims
 * - Integration with kingdom permission system
 * - Automatic cleanup of expired claims
 *
 * PERMISSIONS GRANTED:
 * - Full build and destroy rights in personal claim
 * - Container access and door usage
 * - Ability to modify blocks without kingdom rank restrictions
 * - Overrides kingdom-wide settings for the specific chunk
 *
 * USAGE:
 * Kingdom members can claim chunks for personal use, giving them
 * enhanced permissions in that specific area while still being
 * subject to overall kingdom rules and protection.
 */
package com.odaishi.asheskingdoms.kingdoms;

import net.minecraft.util.math.ChunkPos;
import java.util.UUID;

public class PersonalClaim {
    private final UUID playerId;
    private final ChunkPos chunk;
    private final long creationTime;
    private long expirationTime; // -1 for permanent claims

    public PersonalClaim(UUID playerId, ChunkPos chunk) {
        this(playerId, chunk, -1); // Default to permanent claim
    }

    public PersonalClaim(UUID playerId, ChunkPos chunk, long durationMillis) {
        this.playerId = playerId;
        this.chunk = chunk;
        this.creationTime = System.currentTimeMillis();
        this.expirationTime = durationMillis > 0 ? creationTime + durationMillis : -1;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ChunkPos getChunk() {
        return chunk;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public boolean isExpired() {
        return expirationTime != -1 && System.currentTimeMillis() > expirationTime;
    }

    public boolean isPermanent() {
        return expirationTime == -1;
    }

    public void setPermanent() {
        this.expirationTime = -1;
    }

    public void setExpiration(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public void extendDuration(long additionalMillis) {
        if (expirationTime != -1) {
            this.expirationTime += additionalMillis;
        } else {
            this.expirationTime = System.currentTimeMillis() + additionalMillis;
        }
    }
}