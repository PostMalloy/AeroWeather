package com.postmalloy.aeroweather.integration.aeronautics;

/**
 * Resolved once by {@link AeronauticsIntegration} and started exactly
 * once from {@code AeroWeather}'s constructor. Deliberately just a
 * lifecycle hook, not a per-tick poll target: the real implementation
 * drives itself off Sable's own physics-tick event once started, rather
 * than being ticked externally.
 */
public interface WindForceApplier {
    void start();
}
