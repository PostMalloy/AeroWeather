package com.postmalloy.aeroweather.wind;

/**
 * An operator-set wind override applied via the /aeroweather command.
 * While active, natural simulation is frozen and this pins the effective
 * wind exactly (see {@link WindState#applyOverride(WindOverride)}).
 */
public record WindOverride(float directionDeg, float strength) {
    public WindOverride {
        directionDeg = WindDirection.normalizeDegrees(directionDeg);
        strength = Math.clamp(strength, 0.0f, 100.0f);
    }
}
