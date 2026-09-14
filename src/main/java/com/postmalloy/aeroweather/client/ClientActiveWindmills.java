package com.postmalloy.aeroweather.client;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side set of Create windmill bearings currently being turned by the
 * wind, the windmill counterpart to {@link ClientActiveContraptions}.
 * Deliberately free of any {@code net.minecraft.client.*} imports, mirroring
 * {@link ClientWindState} - see that class for why.
 * <p>
 * Unlike {@link ClientActiveContraptions} this needs no networking at all:
 * windmill bearings are ordinary block entities that tick on the client too, so
 * {@code WindmillBearingBlockEntityMixin} reports them here directly. Sable
 * sub-levels have no such client-side tick to hook, which is why those
 * positions have to be broadcast from the server instead.
 * <p>
 * Entries are timestamped rather than explicitly removed - a windmill that
 * stops turning, unloads, or is broken simply stops reporting, and ages out
 * after {@link #STALE_AFTER_TICKS}. That avoids needing removal hooks for every
 * way a block entity can go away.
 */
public final class ClientActiveWindmills {
    /**
     * How long an entry survives without being reported again. Reporting happens
     * every tick while a windmill is turning, so this only has to be long enough
     * to ride out the ordering between block entity ticks and the particle
     * spawner's own tick.
     */
    private static final long STALE_AFTER_TICKS = 20L;

    /**
     * Keyed by the bearing's own block position - a stable identity, even for a
     * windmill riding a Sable ship - while each sighting records where the
     * windmill actually is in the world. The two differ on a ship: there the
     * bearing's own BlockPos is a far-away plot coordinate, and the ship moves.
     */
    private static final Map<BlockPos, Sighting> SIGHTINGS = new ConcurrentHashMap<>();

    private static ResourceLocation dimension;

    private ClientActiveWindmills() {
    }

    private record Sighting(Vec3 worldPosition, long gameTime) {
    }

    /**
     * Records that the windmill whose bearing is at {@code pos} is currently
     * being turned by the wind, and is actually located at {@code worldPosition}.
     */
    public static void report(ResourceLocation dimension, BlockPos pos, Vec3 worldPosition, long gameTime) {
        if (!dimension.equals(ClientActiveWindmills.dimension)) {
            ClientActiveWindmills.dimension = dimension;
            SIGHTINGS.clear();
        }
        SIGHTINGS.put(pos.immutable(), new Sighting(worldPosition, gameTime));
    }

    /**
     * True if any currently-turning windmill is within {@code radius} of
     * {@code position}. Prunes stale entries as it goes, so this doubles as the
     * cleanup pass - there's no separate tick hook to do it in.
     */
    public static boolean isWithinRadiusOfAny(ResourceLocation dimension, Vec3 position, double radius, long gameTime) {
        if (ClientActiveWindmills.dimension == null || !ClientActiveWindmills.dimension.equals(dimension)) {
            return false;
        }

        double radiusSq = radius * radius;
        boolean near = false;
        Iterator<Sighting> sightings = SIGHTINGS.values().iterator();
        while (sightings.hasNext()) {
            Sighting sighting = sightings.next();
            if (gameTime - sighting.gameTime() > STALE_AFTER_TICKS) {
                sightings.remove();
                continue;
            }
            if (!near && sighting.worldPosition().distanceToSqr(position) <= radiusSq) {
                // Keep iterating rather than returning early, so the prune finishes.
                near = true;
            }
        }
        return near;
    }
}
