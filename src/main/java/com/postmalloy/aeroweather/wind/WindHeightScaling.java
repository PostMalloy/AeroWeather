package com.postmalloy.aeroweather.wind;

/**
 * Scales a base wind strength by elevation, approximating the
 * atmospheric "wind profile power law" (wind speed increasing with
 * height above the surface): 0 at or below sea level, ramping up above
 * it, reaching the unmodified base strength at a configurable reference
 * height above sea level, and continuing to grow (capped by a
 * configurable multiplier) further above that.
 * <p>
 * Pure math, kept separate from any config class so it's reusable
 * wherever wind needs to be sampled at a specific position — currently
 * {@code WindParticleSpawner} (client-side rendering); a server-side
 * consumer is expected once the Create Aeronautics force application
 * milestone (M7) needs to sample wind at a contraption's position too.
 */
public final class WindHeightScaling {
    private WindHeightScaling() {
    }

    /**
     * @param baseStrength          the unmodified wind strength (0-100 scale)
     * @param y                     the position's Y coordinate
     * @param seaLevel              the dimension's sea level (see {@code Level#getSeaLevel()})
     * @param referenceHeight       blocks above sea level where the multiplier reaches 1.0 (unmodified base strength)
     * @param exponent              power-law exponent controlling how quickly the multiplier ramps up
     * @param maxMultiplier         upper bound on the multiplier, to keep extreme altitudes from becoming absurd
     * @return the height-scaled strength; 0 at or below sea level
     */
    public static float scale(float baseStrength, double y, double seaLevel, double referenceHeight, double exponent, double maxMultiplier) {
        double heightAboveSeaLevel = y - seaLevel;
        if (heightAboveSeaLevel <= 0.0 || referenceHeight <= 0.0) {
            return 0.0f;
        }
        double multiplier = Math.min(Math.pow(heightAboveSeaLevel / referenceHeight, exponent), maxMultiplier);
        return (float) Math.max(0.0, baseStrength * multiplier);
    }
}
