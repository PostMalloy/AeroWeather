package com.postmalloy.aeroweather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-authoritative wind simulation tunables (drift, gusts, weather
 * coupling, network sync thresholds). Loaded as {@code ModConfig.Type.COMMON},
 * so it ships with the world/server, not the client. See
 * {@link com.postmalloy.aeroweather.wind.WindState} and
 * {@link com.postmalloy.aeroweather.network.WindSync} for how these are
 * consumed — this class only defines the spec.
 */
public final class AeroWeatherCommonConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue DRIFT_MAX_DIRECTION_DELTA_DEG;
    public static final ModConfigSpec.DoubleValue DRIFT_MAX_STRENGTH_DELTA;
    public static final ModConfigSpec.IntValue DRIFT_RETARGET_MIN_SECONDS;
    public static final ModConfigSpec.IntValue DRIFT_RETARGET_MAX_SECONDS;
    public static final ModConfigSpec.DoubleValue DRIFT_LERP_FACTOR;

    public static final ModConfigSpec.DoubleValue GUST_CHANCE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue GUST_MIN_MAGNITUDE;
    public static final ModConfigSpec.DoubleValue GUST_MAX_MAGNITUDE;
    public static final ModConfigSpec.IntValue GUST_DURATION_SECONDS;

    public static final ModConfigSpec.DoubleValue RAIN_BOOST;
    public static final ModConfigSpec.DoubleValue THUNDER_BOOST;
    public static final ModConfigSpec.DoubleValue WEATHER_EASE_FACTOR;
    public static final ModConfigSpec.DoubleValue STRENGTH_CAP_CLEAR;
    public static final ModConfigSpec.DoubleValue STRENGTH_CAP_RAIN;
    public static final ModConfigSpec.DoubleValue STRENGTH_CAP_THUNDER;

    public static final ModConfigSpec.DoubleValue SYNC_DIRECTION_THRESHOLD_DEG;
    public static final ModConfigSpec.DoubleValue SYNC_STRENGTH_THRESHOLD;
    public static final ModConfigSpec.IntValue SYNC_HEARTBEAT_SECONDS;

    public static final ModConfigSpec.DoubleValue HEIGHT_REFERENCE_ABOVE_SEA_LEVEL;
    public static final ModConfigSpec.DoubleValue HEIGHT_EXPONENT;
    public static final ModConfigSpec.DoubleValue HEIGHT_MAX_MULTIPLIER;

    public static final ModConfigSpec.DoubleValue AERONAUTICS_PRESSURE_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue AERONAUTICS_MAX_FORCE;
    public static final ModConfigSpec.DoubleValue AERONAUTICS_OSCILLATION_AMPLITUDE;
    public static final ModConfigSpec.DoubleValue AERONAUTICS_OSCILLATION_PERIOD_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Natural wind drift: how the base direction/strength wander over time.").push("drift");
        DRIFT_MAX_DIRECTION_DELTA_DEG = builder
                .comment("Maximum degrees a newly rolled drift target may differ from the current direction.")
                .defineInRange("maxDirectionDeltaDegrees", 60.0, 0.0, 180.0);
        DRIFT_MAX_STRENGTH_DELTA = builder
                .comment("Maximum a newly rolled drift target may differ from the current strength (0-100 scale).")
                .defineInRange("maxStrengthDelta", 30.0, 0.0, 100.0);
        DRIFT_RETARGET_MIN_SECONDS = builder
                .comment("Minimum seconds between drift target re-rolls.")
                .defineInRange("retargetMinSeconds", 15, 1, 3600);
        DRIFT_RETARGET_MAX_SECONDS = builder
                .comment("Maximum seconds between drift target re-rolls.")
                .defineInRange("retargetMaxSeconds", 45, 1, 3600);
        DRIFT_LERP_FACTOR = builder
                .comment("Fraction of the remaining distance to the drift target covered each simulation step (higher = faster drift).")
                .defineInRange("lerpFactor", 0.08, 0.001, 1.0);
        builder.pop();

        builder.comment("Short-lived strength spikes layered on top of the drift.").push("gust");
        GUST_CHANCE_PER_SECOND = builder
                .comment("Chance per simulation step (roughly once per second) that a new gust starts.")
                .defineInRange("chancePerSecond", 0.02, 0.0, 1.0);
        GUST_MIN_MAGNITUDE = builder
                .comment("Minimum additive strength a gust spikes to.")
                .defineInRange("minMagnitude", 10.0, 0.0, 100.0);
        GUST_MAX_MAGNITUDE = builder
                .comment("Maximum additive strength a gust spikes to.")
                .defineInRange("maxMagnitude", 25.0, 0.0, 100.0);
        GUST_DURATION_SECONDS = builder
                .comment("How many seconds a gust takes to ramp back down to 0.")
                .defineInRange("durationSeconds", 4, 1, 600);
        builder.pop();

        builder.comment("How rain and thunderstorms boost wind strength.").push("weather");
        RAIN_BOOST = builder
                .comment("Additive strength boost while it's raining (and not thundering).")
                .defineInRange("rainBoost", 15.0, 0.0, 100.0);
        THUNDER_BOOST = builder
                .comment("Additive strength boost while thundering.")
                .defineInRange("thunderBoost", 35.0, 0.0, 100.0);
        WEATHER_EASE_FACTOR = builder
                .comment("Fraction of the remaining distance to the target weather boost covered each simulation step (higher = faster response to weather changes).")
                .defineInRange("easeFactor", 0.1, 0.001, 1.0);
        STRENGTH_CAP_CLEAR = builder
                .comment("Maximum non-height-adjusted wind strength while it's neither raining nor thundering.")
                .defineInRange("strengthCapClear", 50.0, 0.0, 100.0);
        STRENGTH_CAP_RAIN = builder
                .comment("Maximum non-height-adjusted wind strength while raining (and not thundering).")
                .defineInRange("strengthCapRain", 75.0, 0.0, 100.0);
        STRENGTH_CAP_THUNDER = builder
                .comment("Maximum non-height-adjusted wind strength while thundering.")
                .defineInRange("strengthCapThunder", 100.0, 0.0, 100.0);
        builder.pop();

        builder.comment("When to broadcast wind updates to clients.").push("sync");
        SYNC_DIRECTION_THRESHOLD_DEG = builder
                .comment("Minimum direction change (degrees) that triggers an immediate sync outside the heartbeat.")
                .defineInRange("directionThresholdDegrees", 2.0, 0.0, 180.0);
        SYNC_STRENGTH_THRESHOLD = builder
                .comment("Minimum strength change that triggers an immediate sync outside the heartbeat.")
                .defineInRange("strengthThreshold", 1.0, 0.0, 100.0);
        SYNC_HEARTBEAT_SECONDS = builder
                .comment("Maximum seconds between syncs even if nothing changed enough to trigger one on its own.")
                .defineInRange("heartbeatSeconds", 5, 1, 600);
        builder.pop();

        builder.comment(
                "How wind strength scales with elevation, approximating the real-world",
                "wind profile power law (wind speed increasing with height above the",
                "surface): 0 at or below sea level, ramping up above it. Server-authoritative",
                "since /aeroweather wind info reports the elevation-adjusted value, and the",
                "aeronautics wind force below samples it at a contraption's own altitude."
        ).push("height");
        HEIGHT_REFERENCE_ABOVE_SEA_LEVEL = builder
                .comment("Blocks above sea level where the height multiplier reaches 1.0 (unmodified base strength).")
                .defineInRange("referenceAboveSeaLevel", 100.0, 1.0, 4064.0);
        HEIGHT_EXPONENT = builder
                .comment("Power-law exponent controlling how quickly the multiplier ramps up with height.")
                .defineInRange("exponent", 0.3, 0.0, 5.0);
        HEIGHT_MAX_MULTIPLIER = builder
                .comment("Upper bound on the height multiplier, so extreme altitudes don't become absurd.")
                .defineInRange("maxMultiplier", 3.0, 1.0, 20.0);
        builder.pop();

        builder.comment(
                "Tuning for the wind force applied to Create Aeronautics / Sable contraptions (M7).",
                "Force = pressureCoefficient * sectionalArea * windSpeed^2 * liftRatio, then eased by",
                "a slow oscillation, applied at the contraption's center of mass. Requires Sable to be",
                "installed; has no effect otherwise."
        ).push("aeronautics");
        AERONAUTICS_PRESSURE_COEFFICIENT = builder
                .comment("Scales sectional area * elevation-adjusted wind strength^2 into a force magnitude.")
                .defineInRange("pressureCoefficient", 0.00001, 0.0, 1000.0);
        AERONAUTICS_MAX_FORCE = builder
                .comment("Safety clamp on the computed force magnitude, regardless of contraption size or wind strength.")
                .defineInRange("maxForce", 5000.0, 0.0, 1000000.0);
        AERONAUTICS_OSCILLATION_AMPLITUDE = builder
                .comment("Fractional force oscillation (e.g. 0.05 = force varies +/-5%) - a subtle gust-like ripple, not a separate force.")
                .defineInRange("oscillationAmplitude", 0.6, 0.0, 1.0);
        AERONAUTICS_OSCILLATION_PERIOD_SECONDS = builder
                .comment("Seconds for one full oscillation cycle.")
                .defineInRange("oscillationPeriodSeconds", 2.0, 0.1, 600.0);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroWeatherCommonConfig() {
    }
}
