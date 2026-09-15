package com.postmalloy.aeroweather.wind;

/**
 * A pinned wind direction and strength: set indefinitely by an operator's
 * /aeroweather command, or for a limited time by the breeze maker. While
 * active, natural simulation is frozen and this pins the effective wind
 * exactly (see {@link WindState#applyOverride(WindOverride)} and
 * {@link WindState#applyTimedOverride(WindOverride, long)}).
 */
public record WindOverride(float directionDeg, float strength) {
    public WindOverride {
        directionDeg = WindDirection.normalizeDegrees(directionDeg);
        strength = Math.clamp(strength, 0.0f, 100.0f);
    }
}
