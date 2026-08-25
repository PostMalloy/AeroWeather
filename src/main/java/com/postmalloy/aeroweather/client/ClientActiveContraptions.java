package com.postmalloy.aeroweather.client;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side cache of the most recently synced world-space positions
 * where wind force is currently being applied to a Sable sub-level, for
 * one dimension. Deliberately free of any {@code net.minecraft.client.*}
 * imports, mirroring {@link ClientWindState} — see that class for why.
 * Separate from {@link ClientWindState} since this tracks contraption
 * positions (a different concern, sourced from the Aeronautics
 * integration), not wind state itself.
 */
public final class ClientActiveContraptions {
    private static ResourceLocation dimension;
    private static List<Vec3> positions = List.of();

    private ClientActiveContraptions() {
    }

    public static void update(ResourceLocation dimension, List<Vec3> positions) {
        ClientActiveContraptions.dimension = dimension;
        ClientActiveContraptions.positions = positions;
    }

    /** The dimension the cached positions apply to, or null if nothing has been received yet. */
    public static ResourceLocation dimension() {
        return dimension;
    }

    /** World-space positions of Sable sub-levels currently experiencing wind force, in the synced dimension. */
    public static List<Vec3> positions() {
        return positions;
    }
}
