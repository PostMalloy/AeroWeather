package com.postmalloy.aeroweather.integration.simulated;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.integration.ModCompat;

import net.neoforged.bus.api.IEventBus;

/**
 * The gate in front of the wind bearing (M13). Zero Create/Simulated/Sable
 * imports, so it's always safe to classload; {@link WindBearingRegistration} —
 * and through it the block, its item and its block entity — is only ever touched
 * once all three mods are confirmed present.
 * <p>
 * That ordering is the whole point. The wind bearing extends Create Simulated's
 * swivel bearing, so merely loading its class without those mods would throw
 * {@code NoClassDefFoundError} during registration. Same shape as
 * {@code AeronauticsIntegration}, which keeps Sable off the classloader the same
 * way.
 */
public final class WindBearingIntegration {
    private static final String CREATE_MODID = "create";
    private static final String SIMULATED_MODID = "simulated";

    private WindBearingIntegration() {
    }

    /** Registers the wind bearing, or does nothing at all if any of the three mods is missing. */
    public static void start(IEventBus modEventBus) {
        if (!ModCompat.isLoaded(CREATE_MODID)
                || !ModCompat.isLoaded(SIMULATED_MODID)
                || !ModCompat.isLoaded(ModCompat.SABLE_MODID)) {
            return;
        }

        try {
            WindBearingRegistration.register(modEventBus);
        } catch (Throwable throwable) {
            // Matches the degrade-don't-crash rule the Sable integration follows: a wind
            // bearing that fails to register costs a block, not the whole mod.
            AeroWeather.LOGGER.error("Failed to register the wind bearing; it will be unavailable", throwable);
        }
    }
}
