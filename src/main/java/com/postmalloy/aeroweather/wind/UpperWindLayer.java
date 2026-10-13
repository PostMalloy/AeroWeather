package com.postmalloy.aeroweather.wind;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * A second wind layer above cloud level (M17): where it applies, how much of it
 * applies at a given height, and — in {@link UpperWindMode#COMPLEX} — what it
 * blows like. {@link WorldWind} combines it with the wind below.
 * <p>
 * Like {@link WindField}, everything here is a function of position, config and
 * the synced flow seed, plus game time for the walk. Nothing is synced of its own:
 * the client's game time follows the server's, so both sides arrive at the same
 * offset independently. The walk moves a fraction of a degree a second, so the
 * tick or so the two clocks can disagree by is invisible.
 *
 * <h2>The walk</h2>
 * The complex layer's offset is {@code upperWindOffsetDegrees} plus a walk that
 * wanders between its configured minimum and maximum. It's one-dimensional value
 * noise over game time — {@link FlowNoise} sampled along a line — rather than a
 * stepped random walk, because a stepped walk would have to live in
 * {@link WindState} and be synced, where this needs no state at all and survives a
 * restart for free (game time is saved). Each lattice point is a fresh uniform
 * pick across the range, so the walk does reach both ends, and the eased
 * interpolation between them means it never turns abruptly.
 */
public final class UpperWindLayer {
    /** Salts the flow seed, so the upper flow map is uncorrelated with the one below. */
    private static final long DIRECTION_SALT = 0x2545F4914F6CDD1DL;
    private static final long STRENGTH_SALT = 0x6A09E667F3BCC909L;
    private static final long WALK_SALT = 0xBB67AE8584CAA73BL;

    private UpperWindLayer() {
    }

    /** The complex layer's wind before height scaling: a FROM bearing and a strength multiplier. */
    public record Flow(float directionDeg, float strengthFactor) {
    }

    public static UpperWindMode mode() {
        return AeroWeatherCommonConfig.UPPER_WIND_MODE.get();
    }

    /**
     * Whether the layer exists in this level at all: switched on, and a dimension
     * with a sky. "Above the clouds" means nothing in the Nether, whose roof players
     * can still stand above, or in the End, which has no clouds.
     */
    public static boolean activeIn(Level level) {
        if (!AeroWeatherCommonConfig.UPPER_WIND_ENABLED.get()) {
            return false;
        }
        DimensionType type = level.dimensionType();
        return type.hasSkyLight() && !type.hasCeiling();
    }

    /**
     * How much of the upper layer applies at {@code y}: 0 below the transition band
     * (where the wind is exactly what it was without this layer), 1 above it, eased
     * in between.
     * <p>
     * The complex layer also waits for the flow seed. Until the biome table has
     * synced, a client would otherwise turn the upper wind with a seed of 0, and
     * visibly disagree with the server for the moment that takes.
     */
    public static float blendAt(Level level, double y) {
        if (!appliesIn(level)) {
            return 0.0f;
        }
        return blend(y, AeroWeatherCommonConfig.UPPER_WIND_LAYER_HEIGHT.getAsDouble(),
                AeroWeatherCommonConfig.UPPER_WIND_TRANSITION_BLOCKS.getAsDouble());
    }

    /**
     * The Y at and below which {@link #blendAt} is 0, or positive infinity when the
     * layer doesn't apply in this level. For a hot loop that can cache it once a tick
     * and skip the layer entirely for anything beneath.
     */
    public static double lowestAffectedY(Level level) {
        if (!appliesIn(level)) {
            return Double.POSITIVE_INFINITY;
        }
        return AeroWeatherCommonConfig.UPPER_WIND_LAYER_HEIGHT.getAsDouble()
                - Math.max(0.0, AeroWeatherCommonConfig.UPPER_WIND_TRANSITION_BLOCKS.getAsDouble()) / 2.0;
    }

    private static boolean appliesIn(Level level) {
        return activeIn(level) && (mode() != UpperWindMode.COMPLEX || BiomeWindFactors.isReady());
    }

    /** Smoothstep across a band of {@code transition} blocks centred on {@code layerHeight}. */
    static float blend(double y, double layerHeight, double transition) {
        if (transition <= 0.0) {
            return y >= layerHeight ? 1.0f : 0.0f;
        }
        double t = (y - (layerHeight - transition / 2.0)) / transition;
        if (t <= 0.0) {
            return 0.0f;
        }
        if (t >= 1.0) {
            return 1.0f;
        }
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /**
     * The complex layer at a position. Its own flow map bends and scales it exactly
     * as {@link WindField}'s does the wind below — same scale, deviation and strength
     * variation, a different seed — and the whole layer is turned by
     * {@link #offsetDeg}. There's no biome factor up here: the ground is too far
     * below to matter.
     */
    public static Flow complexAt(Level level, double x, double z, float baseDirectionDeg) {
        long seed = BiomeWindFactors.flowSeed();
        double scale = AeroWeatherCommonConfig.FLOW_MAP_SCALE_BLOCKS.getAsDouble();
        double fieldX = x / scale;
        double fieldZ = z / scale;

        double bend = FlowNoise.sample(fieldX, fieldZ, seed ^ DIRECTION_SALT)
                * AeroWeatherCommonConfig.FLOW_MAP_MAX_DIRECTION_DEVIATION_DEGREES.getAsDouble();
        double strengthNoise = FlowNoise.sampleCoarse(fieldX, fieldZ, seed ^ STRENGTH_SALT)
                * AeroWeatherCommonConfig.FLOW_MAP_STRENGTH_VARIATION.getAsDouble();

        float direction = WindDirection.normalizeDegrees(
                (float) (baseDirectionDeg + offsetDeg(level.getGameTime()) + bend));
        return new Flow(direction, (float) Math.max(0.0, 1.0 + strengthNoise));
    }

    /** The complex layer's turn from the wind below, right now: the fixed offset plus the walk. */
    public static float offsetDeg(long gameTime) {
        double min = AeroWeatherCommonConfig.UPPER_WIND_OFFSET_WALK_MIN_DEGREES.getAsDouble();
        double max = AeroWeatherCommonConfig.UPPER_WIND_OFFSET_WALK_MAX_DEGREES.getAsDouble();
        return (float) (AeroWeatherCommonConfig.UPPER_WIND_OFFSET_DEGREES.getAsDouble()
                + walk(gameTime, BiomeWindFactors.flowSeed(), Math.min(min, max), Math.max(min, max),
                        AeroWeatherCommonConfig.UPPER_WIND_OFFSET_WALK_PERIOD_SECONDS.get()));
    }

    /** One octave of value noise along game time, one lattice step per period, mapped onto [min, max]. */
    static double walk(long gameTime, long seed, double min, double max, int periodSeconds) {
        double t = gameTime / (20.0 * periodSeconds);
        double noise = FlowNoise.sampleCoarse(t, 0.0, seed ^ WALK_SALT);
        return min + (noise + 1.0) * 0.5 * (max - min);
    }
}
