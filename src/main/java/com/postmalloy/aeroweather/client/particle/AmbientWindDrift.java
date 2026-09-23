package com.postmalloy.aeroweather.client.particle;

import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WindField;
import com.postmalloy.aeroweather.wind.WindHeightScaling;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Shared target-velocity computation for vanilla ambient particles (campfire smoke, falling
 * cherry leaves) that AeroWeather nudges toward the wind - see
 * {@link WindDriftingCampfireSmokeParticle} / {@link WindDriftingCherryParticle}. A plain
 * utility class has no subclass relationship to {@code Particle}, so it can't touch its
 * protected {@code xd}/{@code zd} fields directly - each particle class applies the returned
 * target itself via its own "ease toward a target, don't snap" step each tick, the same idiom
 * {@code WindState} already uses for {@code weatherBoost}. Strength is elevation-adjusted at
 * the particle's own (live, not spawn-cached) Y, matching how {@code AeronauticsWindForceApplier}
 * samples wind at a contraption's own position rather than the player's.
 */
@OnlyIn(Dist.CLIENT)
final class AmbientWindDrift {
    private AmbientWindDrift() {
    }

    /**
     * @return the wind-driven target horizontal velocity at the particle's own position in
     *         {@code level}, or {@code null} if the effect is disabled (0 intensity), wind hasn't
     *         synced for this dimension yet, or the elevation-adjusted strength there is 0.
     */
    static Vec3 targetVelocity(ClientLevel level, double x, double y, double z) {
        float intensity = (float) AeroWeatherClientConfig.AMBIENT_WIND_PARTICLE_INTENSITY.getAsDouble();
        if (intensity <= 0.0F) {
            return null;
        }

        ResourceLocation syncedDimension = ClientWindState.dimension();
        if (syncedDimension == null || !syncedDimension.equals(level.dimension().location())) {
            return null;
        }

        // Per position, not per dimension: a particle drifting over a forest should be
        // pushed less than one over the plains next to it.
        WindField.Sample field = WindField.at(level, x, z);

        float strength = WindHeightScaling.scale(ClientWindState.strength() * field.strengthFactor(), y, level.getSeaLevel(),
                AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_EXPONENT.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_MAX_MULTIPLIER.getAsDouble());
        if (strength <= 0.0F) {
            return null;
        }

        Vec3 travel = WindDirection.travelVector(
                WindDirection.normalizeDegrees(ClientWindState.directionDeg() + field.directionOffsetDeg()));
        return travel.scale(intensity * (strength / 100.0));
    }
}
