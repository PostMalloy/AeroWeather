package com.postmalloy.aeroweather.integration.aeronautics;

/** Used when Sable isn't loaded, or when {@link AeronauticsWindForceApplier} failed to initialize. */
public final class NoopWindForceApplier implements WindForceApplier {
    @Override
    public void start() {
    }
}
