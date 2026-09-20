package com.postmalloy.aeroweather.integration.particlerain;

import org.joml.Vector3f;

import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WindHeightScaling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The wind Particle Rain's weather particles are blown by, replacing the
 * position/time noise it generates for itself — see
 * {@code ParticleRainWindMixin}, which is the only caller.
 * <p>
 * Despite living under {@code integration/}, this class references <em>no</em>
 * Particle Rain types (only vanilla, JOML and AeroWeather), so it's always safe
 * to classload; the mixin names Particle Rain purely by string, and
 * {@code AeroWeatherMixinPlugin} withholds that mixin entirely when the mod is
 * absent. Client-side only in practice: Particle Rain is a client mod, and this
 * reads the synced {@link ClientWindState}.
 * <p>
 * <b>Calibration.</b> Particle Rain applies this vector as a per-tick
 * horizontal acceleration scaled by each particle type's own
 * {@code windStrength}, on top of vanilla physics ({@code yd -= 0.04 * gravity},
 * then all three axes multiplied by friction). At terminal velocity the friction
 * term cancels, so a particle settles at
 * {@code atan(|W| * windStrength / (0.04 * gravity))}. Feeding
 * {@code |W| = RAIN_REFERENCE * tan(configured angle) * share} therefore puts
 * <em>rain</em> at exactly the configured angle when {@code share} is 1, i.e.
 * when local strength equals the configured reference strength. Because the
 * vector scales linearly with strength while the angle is its arctangent,
 * stronger wind keeps steepening the slant but only ever approaches horizontal
 * — no cap needed, and elevation scaling (up to 3x) can't produce a past-90°
 * nonsense angle. Every other particle type keeps its own response relative to
 * rain: snow stays gentle, dust blows nearly sideways, fog and mist drift.
 */
@OnlyIn(Dist.CLIENT)
public final class ParticleRainWind {
    /**
     * {@code 0.04 * gravity / windStrength} for Particle Rain's shipped rain
     * preset (gravity 0.9, windStrength 0.4). If a pack retunes rain, the slant
     * stays proportional to wind but no longer lands exactly on the configured
     * angle.
     */
    private static final double RAIN_REFERENCE = 0.04 * 0.9 / 0.4;

    /**
     * The same figure for the shipped {@code dust} preset (gravity 0.1,
     * windStrength 0.7), used for the sandstorm floor below. Dust responds to
     * wind about 15x more strongly than rain, which is what makes that floor
     * practical: enough wind to hold sand at 45° barely tilts rain at all.
     */
    private static final double DUST_REFERENCE = 0.04 * 0.1 / 0.7;

    // Recomputed once per client tick rather than per call: getWind runs for every weather
    // particle every tick (thousands in heavy rain), but direction and base strength only
    // change when wind syncs (~1Hz). Client thread only, so no synchronisation.
    private static long cachedTick = Long.MIN_VALUE;
    private static boolean cachedActive;
    private static double cachedTravelX;
    private static double cachedTravelZ;
    private static float cachedBaseStrength;
    private static double cachedMagnitudePerStrength;
    private static double cachedMinimumMagnitude;
    private static double cachedSeaLevel;
    private static double cachedReferenceHeight;
    private static double cachedExponent;
    private static double cachedMaxMultiplier;

    private ParticleRainWind() {
    }

    /**
     * The wind at a position, or {@code null} to leave Particle Rain's own wind
     * alone — when the integration is switched off, or no wind has synced for
     * the dimension being rendered. A calm AeroWeather wind returns a zero
     * vector rather than null: calm means rain falls straight down, not that the
     * mod's noise should take over.
     */
    public static Vector3f windAt(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        refreshCache(level);
        if (!cachedActive) {
            return null;
        }

        float adjustedStrength = WindHeightScaling.scale(cachedBaseStrength, y, cachedSeaLevel,
                cachedReferenceHeight, cachedExponent, cachedMaxMultiplier);
        // The floor keeps sandstorm dust blowing sideways even in dead calm - a sandstorm that
        // falls straight down like rain isn't a sandstorm. It's a floor on the wind vector, not
        // on any one particle's angle, because getWind is shared by every particle type and
        // isn't told which one is asking.
        double magnitude = Math.max(cachedMagnitudePerStrength * adjustedStrength, cachedMinimumMagnitude);
        return new Vector3f((float) (cachedTravelX * magnitude), 0.0f, (float) (cachedTravelZ * magnitude));
    }

    private static void refreshCache(ClientLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime == cachedTick) {
            return;
        }
        cachedTick = gameTime;

        ResourceLocation syncedDimension = ClientWindState.dimension();
        cachedActive = AeroWeatherClientConfig.PARTICLE_RAIN_ENABLED.get()
                && syncedDimension != null
                && syncedDimension.equals(level.dimension().location());
        if (!cachedActive) {
            return;
        }

        double angleDegrees = AeroWeatherClientConfig.PARTICLE_RAIN_ANGLE_DEGREES.getAsDouble();
        double referenceStrength = AeroWeatherClientConfig.PARTICLE_RAIN_REFERENCE_STRENGTH.getAsDouble();
        cachedMagnitudePerStrength = RAIN_REFERENCE * Math.tan(Math.toRadians(angleDegrees)) / referenceStrength;

        double sandMinAngleDegrees = AeroWeatherClientConfig.PARTICLE_RAIN_SAND_MIN_ANGLE_DEGREES.getAsDouble();
        cachedMinimumMagnitude = DUST_REFERENCE * Math.tan(Math.toRadians(sandMinAngleDegrees));

        Vec3 travel = WindDirection.travelVector(ClientWindState.directionDeg());
        cachedTravelX = travel.x;
        cachedTravelZ = travel.z;
        cachedBaseStrength = ClientWindState.strength();

        cachedSeaLevel = level.getSeaLevel();
        cachedReferenceHeight = AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL.getAsDouble();
        cachedExponent = AeroWeatherCommonConfig.HEIGHT_EXPONENT.getAsDouble();
        cachedMaxMultiplier = AeroWeatherCommonConfig.HEIGHT_MAX_MULTIPLIER.getAsDouble();
    }
}
