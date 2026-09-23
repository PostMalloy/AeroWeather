package com.postmalloy.aeroweather.wind;

/**
 * The smooth 2D field that bends wind direction across the world — a "flow map".
 * A pure function of position and seed, in the same spirit as
 * {@link WindHeightScaling} and {@link WindmillWindResponse}.
 * <p>
 * Deliberately <em>not</em> one of vanilla's noise classes. This value has to
 * come out bit-identical on the client and the server, because each side
 * evaluates it independently rather than syncing it (see M14 in CLAUDE.md), and
 * a self-contained function is far easier to guarantee that for than Minecraft's
 * own noise plumbing, which is tied to {@code RandomSource} and to worldgen
 * seeding. Plain {@code double} arithmetic is exactly reproducible — Java 17
 * onward makes all floating point strict — so the same inputs always give the
 * same bits on every JVM.
 * <p>
 * Value noise rather than gradient/Perlin noise: the wind only needs a smoothly
 * wandering field, not the visual quality a terrain generator would want, and
 * value noise costs a handful of integer hashes.
 */
public final class FlowNoise {
    /** Fractional weight of the second octave; the first takes the rest. */
    private static final double DETAIL_WEIGHT = 0.35;

    /** How much finer the second octave is than the first. Deliberately not a whole number, so the two don't line up periodically. */
    private static final double DETAIL_FREQUENCY = 2.37;

    private FlowNoise() {
    }

    /**
     * Two octaves of value noise at {@code (x, z)}, in the range [-1, 1].
     * Coordinates are in field units: divide world blocks by the configured
     * scale before calling, so one unit is one feature of the flow map.
     * <p>
     * The two octaves are combined as a weighted average rather than a sum, so
     * the result needs no renormalising to stay inside [-1, 1].
     */
    public static double sample(double x, double z, long seed) {
        double base = octave(x, z, seed);
        double detail = octave(x * DETAIL_FREQUENCY, z * DETAIL_FREQUENCY, seed ^ 0x9E3779B97F4A7C15L);
        return (1.0 - DETAIL_WEIGHT) * base + DETAIL_WEIGHT * detail;
    }

    /** One octave only, for fields that don't need the detail layer. Range [-1, 1]. */
    public static double sampleCoarse(double x, double z, long seed) {
        return octave(x, z, seed);
    }

    /** One octave: bilinear interpolation of lattice values, eased so the field has no creases. */
    private static double octave(double x, double z, long seed) {
        int x0 = floor(x);
        int z0 = floor(z);
        double fx = ease(x - x0);
        double fz = ease(z - z0);

        double v00 = latticeValue(seed, x0, z0);
        double v10 = latticeValue(seed, x0 + 1, z0);
        double v01 = latticeValue(seed, x0, z0 + 1);
        double v11 = latticeValue(seed, x0 + 1, z0 + 1);

        return lerp(lerp(v00, v10, fx), lerp(v01, v11, fx), fz);
    }

    /**
     * A stable pseudo-random value in [-1, 1] for one lattice point, from a
     * SplitMix64 finaliser. Avalanches well enough that neighbouring lattice
     * points are uncorrelated, which is all the field needs.
     */
    private static double latticeValue(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        // The top 53 bits give a double in [0, 1); rescale to [-1, 1).
        return (h >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
    }

    /** Smoothstep. Zero first derivative at both ends, so octaves meet without visible seams. */
    private static double ease(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** {@code Math.floor} for the lattice, without the double round trip. */
    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
