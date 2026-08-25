package com.postmalloy.aeroweather.client;

import net.minecraft.resources.ResourceLocation;

/**
 * Client-side cache of the most recently synced wind. Deliberately free
 * of any {@code net.minecraft.client.*} imports — it (and the network
 * handler that updates it) must stay safely classloadable on a
 * dedicated server, since payload registration runs on both sides. Only
 * the eventual particle-spawning code (a later milestone) is actually
 * Dist.CLIENT-gated.
 */
public final class ClientWindState {
    private static ResourceLocation dimension;
    private static float directionDeg;
    private static float strength;
    private static boolean gusting;

    private ClientWindState() {
    }

    public static void update(ResourceLocation dimension, float directionDeg, float strength, boolean gusting) {
        ClientWindState.dimension = dimension;
        ClientWindState.directionDeg = directionDeg;
        ClientWindState.strength = strength;
        ClientWindState.gusting = gusting;
    }

    /** The dimension the cached wind applies to, or null if nothing has been received yet. */
    public static ResourceLocation dimension() {
        return dimension;
    }

    public static float directionDeg() {
        return directionDeg;
    }

    public static float strength() {
        return strength;
    }

    public static boolean isGusting() {
        return gusting;
    }
}
