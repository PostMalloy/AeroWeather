package com.postmalloy.aeroweather.integration.adchimneys;

import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WorldWind;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The horizontal velocity Advanced Chimneys' smoke is blown at, replacing the wind its
 * library (ForgeEndertech) simulates for itself — see {@code AdvancedChimneysSmokeMixin},
 * the only caller.
 * <p>
 * Advanced Chimneys doesn't ease its smoke toward the wind: on every tick a puff is out
 * in the open, it <em>sets</em> the puff's horizontal velocity to the wind's motion, in
 * blocks per tick. So this returns a velocity in the same units, and the mixin sets it
 * the same way. Sampled at the puff's own live position, as {@code AmbientWindDrift} does
 * for campfire smoke, so a puff rising off a tall chimney picks up the stronger wind
 * aloft, and one drifting over a forest slows.
 * <p>
 * References no Advanced Chimneys or ForgeEndertech types, so it's always safe to
 * classload. Client-side only: the smoke is a client particle, and this reads the synced
 * {@link ClientWindState}.
 */
@OnlyIn(Dist.CLIENT)
public final class ChimneySmokeWind {
    private ChimneySmokeWind() {
    }

    /**
     * @return the smoke's horizontal velocity at this point, in blocks per tick (zero in
     *         calm air, so smoke rises straight up); or {@code null} when the integration
     *         is off or no wind has synced for this dimension, which leaves Advanced
     *         Chimneys' own wind in place.
     */
    public static Vec3 velocity(ClientLevel level, double x, double y, double z) {
        if (!AeroWeatherClientConfig.ADVANCED_CHIMNEYS_ENABLED.get()) {
            return null;
        }
        ResourceLocation syncedDimension = ClientWindState.dimension();
        if (syncedDimension == null || !syncedDimension.equals(level.dimension().location())) {
            return null;
        }

        WorldWind.Sample wind = WorldWind.at(level, x, y, z, ClientWindState.directionDeg(), ClientWindState.strength());
        if (wind.strength() <= 0.0F) {
            return Vec3.ZERO;
        }
        double blocksPerTick = AeroWeatherClientConfig.ADVANCED_CHIMNEYS_SMOKE_SPEED.getAsDouble()
                * (wind.strength() / 100.0) / 20.0;
        return WindDirection.travelVector(wind.directionDeg()).scale(blocksPerTick);
    }
}
