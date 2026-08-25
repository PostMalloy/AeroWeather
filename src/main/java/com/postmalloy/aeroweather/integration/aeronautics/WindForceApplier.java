package com.postmalloy.aeroweather.integration.aeronautics;

import net.neoforged.bus.api.IEventBus;

/**
 * Resolved once by {@link AeronauticsIntegration} and started exactly
 * once from {@code AeroWeather}'s constructor. Deliberately just a
 * lifecycle hook, not a per-tick poll target: the real implementation
 * drives itself off Sable's own physics-tick event once started, rather
 * than being ticked externally. Takes the mod event bus because
 * registering a {@code ForceGroup} into Sable's registry (so it shows
 * up correctly on the contraption diagram) needs a {@code RegisterEvent}
 * listener, which only fires on that bus.
 */
public interface WindForceApplier {
    void start(IEventBus modEventBus);
}
