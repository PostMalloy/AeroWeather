package com.postmalloy.aeroweather.wind;

/**
 * Scales a base wind strength by elevation, approximating the
 * atmospheric "wind profile power law" (wind speed increasing with
 * height above the surface): a fixed share of the base strength at sea
 * level, following the power law once it overtakes that share, reaching
 * the unmodified base strength at a configurable reference height above
 * sea level, and continuing to grow (capped by a configurable multiplier)
 * further above that. Below sea level there is no wind at all.
 * <p>
 * The sea-level share is a floor under the power law, not a blend into it.
 * The pure power law is 0 at sea level, which isn't how real wind behaves:
 * a beach or a boat on open water is windy, just less so than a hilltop.
 * Taking the larger of the two leaves the curve above the crossover (about
 * 4 blocks up at the defaults) exactly as tuned, whereas blending would
 * weaken the wind at every altitude.
 * <p>
 * Pure math, kept separate from any config class so it's reusable
 * wherever wind needs to be sampled at a specific position.
 */
public final class WindHeightScaling {
    /**
     * How far below sea level still counts as "at" it. Sea level is the top of
     * the water, so the topmost water block sits just under it: a boat, a
     * player swimming at the surface or a raft on the ocean are all at y
     * slightly under sea level, and should still feel the sea-level wind.
     */
    private static final double SURFACE_MARGIN = 1.0;

    private WindHeightScaling() {
    }

    /**
     * @param baseStrength          the unmodified wind strength (0-100 scale)
     * @param y                     the position's Y coordinate
     * @param seaLevel              the dimension's sea level (see {@code Level#getSeaLevel()})
     * @param referenceHeight       blocks above sea level where the multiplier reaches 1.0 (unmodified base strength)
     * @param exponent              power-law exponent controlling how quickly the multiplier ramps up
     * @param maxMultiplier         upper bound on the multiplier, to keep extreme altitudes from becoming absurd
     * @param seaLevelMultiplier    the multiplier at sea level, a floor under the power law (0 restores a pure power law)
     * @return the height-scaled strength; 0 more than {@link #SURFACE_MARGIN} below sea level
     */
    public static float scale(float baseStrength, double y, double seaLevel, double referenceHeight, double exponent,
            double maxMultiplier, double seaLevelMultiplier) {
        double heightAboveSeaLevel = y - seaLevel;
        if (heightAboveSeaLevel < -SURFACE_MARGIN || referenceHeight <= 0.0) {
            return 0.0f;
        }
        double powerLaw = Math.pow(Math.max(heightAboveSeaLevel, 0.0) / referenceHeight, exponent);
        double multiplier = Math.min(Math.max(powerLaw, seaLevelMultiplier), maxMultiplier);
        return (float) Math.max(0.0, baseStrength * multiplier);
    }
}
