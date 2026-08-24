package com.postmalloy.aeroweather.network;

import java.util.HashMap;
import java.util.Map;

import com.postmalloy.aeroweather.network.payload.ClientboundWindSyncPayload;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WindSavedData;
import com.postmalloy.aeroweather.wind.WindState;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Decides when to broadcast wind updates to clients: immediately on
 * command overrides and full per-player syncs (join/dimension
 * change/respawn), otherwise only when the effective wind has drifted
 * past a small threshold or a heartbeat interval has elapsed — never
 * every tick. See CLAUDE.md's "Wind system design" for the thresholds.
 */
public final class WindSync {
    private static final float DIRECTION_THRESHOLD_DEG = 2.0f;
    private static final float STRENGTH_THRESHOLD = 1.0f;
    private static final long HEARTBEAT_TICKS = 100; // ~5s

    private static final Map<ResourceKey<Level>, Tracker> TRACKERS = new HashMap<>();

    private WindSync() {
    }

    /** Called once per simulation step; broadcasts to the dimension only if warranted. */
    public static void maybeSync(ServerLevel level, WindSavedData savedData) {
        Tracker tracker = TRACKERS.computeIfAbsent(level.dimension(), key -> new Tracker());
        WindState wind = savedData.wind();
        long now = level.getGameTime();

        boolean directionChanged = WindDirection.angularDifference(wind.directionDeg(), tracker.directionDeg) > DIRECTION_THRESHOLD_DEG;
        boolean strengthChanged = Math.abs(wind.strength() - tracker.strength) >= STRENGTH_THRESHOLD;
        boolean heartbeatElapsed = now - tracker.lastSyncTick >= HEARTBEAT_TICKS;

        if (tracker.lastSyncTick < 0 || directionChanged || strengthChanged || heartbeatElapsed) {
            broadcast(level, wind);
            tracker.update(wind, now);
        }
    }

    /** Broadcasts to everyone in the dimension immediately, bypassing thresholds. Used after command overrides. */
    public static void forceSync(ServerLevel level, WindSavedData savedData) {
        WindState wind = savedData.wind();
        broadcast(level, wind);
        TRACKERS.computeIfAbsent(level.dimension(), key -> new Tracker()).update(wind, level.getGameTime());
    }

    /** Sends the current wind for a level to a single player (join/dimension-change/respawn). */
    public static void sendTo(ServerPlayer player, ServerLevel level, WindSavedData savedData) {
        PacketDistributor.sendToPlayer(player, payloadFor(level, savedData.wind()));
    }

    private static void broadcast(ServerLevel level, WindState wind) {
        PacketDistributor.sendToPlayersInDimension(level, payloadFor(level, wind));
    }

    private static ClientboundWindSyncPayload payloadFor(ServerLevel level, WindState wind) {
        return new ClientboundWindSyncPayload(level.dimension().location(), wind.directionDeg(), wind.strength());
    }

    private static final class Tracker {
        private float directionDeg;
        private float strength;
        private long lastSyncTick = -1;

        private void update(WindState wind, long tick) {
            this.directionDeg = wind.directionDeg();
            this.strength = wind.strength();
            this.lastSyncTick = tick;
        }
    }
}
