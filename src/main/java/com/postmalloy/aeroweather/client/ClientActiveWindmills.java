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

    private static final Map<BlockPos, Long> LAST_SEEN_TICK = new ConcurrentHashMap<>();

    private static ResourceLocation dimension;

    private ClientActiveWindmills() {
    }

    /** Records that the windmill at {@code pos} is currently being turned by the wind. */
    public static void report(ResourceLocation dimension, BlockPos pos, long gameTime) {
        if (!dimension.equals(ClientActiveWindmills.dimension)) {
            ClientActiveWindmills.dimension = dimension;
            LAST_SEEN_TICK.clear();
        }
        LAST_SEEN_TICK.put(pos.immutable(), gameTime);
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
        Iterator<Map.Entry<BlockPos, Long>> entries = LAST_SEEN_TICK.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<BlockPos, Long> entry = entries.next();
            if (gameTime - entry.getValue() > STALE_AFTER_TICKS) {
                entries.remove();
                continue;
            }
            if (!near && isWithin(entry.getKey(), position, radiusSq)) {
                // Keep iterating rather than returning early, so the prune finishes.
                near = true;
            }
        }
        return near;
    }

    private static boolean isWithin(BlockPos pos, Vec3 position, double radiusSq) {
        double dx = pos.getX() + 0.5 - position.x;
        double dy = pos.getY() + 0.5 - position.y;
        double dz = pos.getZ() + 0.5 - position.z;
        return dx * dx + dy * dy + dz * dz <= radiusSq;
    }
}
