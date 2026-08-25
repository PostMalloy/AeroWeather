package com.postmalloy.aeroweather.integration.aeronautics;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.integration.ModCompat;

/**
 * Resolves the active {@link WindForceApplier}. {@link #isLoaded} is
 * checked before {@code AeronauticsWindForceApplier} is ever
 * constructed, so its Sable-typed fields/imports are only resolved by
 * the JVM once we already know Sable is present — see CLAUDE.md's
 * soft-dependency isolation rule.
 */
public final class AeronauticsIntegration {
    private static final WindForceApplier INSTANCE = resolve();

    private AeronauticsIntegration() {
    }

    public static WindForceApplier get() {
        return INSTANCE;
    }

    private static WindForceApplier resolve() {
        if (!ModCompat.isLoaded(ModCompat.SABLE_MODID)) {
            return new NoopWindForceApplier();
        }
        try {
            return new AeronauticsWindForceApplier();
        } catch (Throwable t) {
            AeroWeather.LOGGER.error("Failed to initialize Aeronautics wind force integration; disabling.", t);
            return new NoopWindForceApplier();
        }
    }
}
