package com.postmalloy.aeroweather.config;

import java.util.List;

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

    public static final ModConfigSpec.BooleanValue PER_BIOME_WIND_ENABLED;
    public static final ModConfigSpec.DoubleValue BIOME_WIND_STRENGTH_INFLUENCE;
    public static final ModConfigSpec.DoubleValue BIOME_WIND_BLEND_RADIUS_BLOCKS;
    public static final ModConfigSpec.DoubleValue FLOW_MAP_MAX_DIRECTION_DEVIATION_DEGREES;
    public static final ModConfigSpec.DoubleValue FLOW_MAP_SCALE_BLOCKS;
    public static final ModConfigSpec.DoubleValue FLOW_MAP_STRENGTH_VARIATION;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_WIND_FACTORS;

    public static final ModConfigSpec.DoubleValue DRIFT_MAX_DIRECTION_DELTA_DEG;
    public static final ModConfigSpec.DoubleValue DRIFT_MAX_STRENGTH_DELTA;
    public static final ModConfigSpec.IntValue DRIFT_RETARGET_MIN_SECONDS;
    public static final ModConfigSpec.IntValue DRIFT_RETARGET_MAX_SECONDS;
    public static final ModConfigSpec.DoubleValue DRIFT_LERP_FACTOR;

    public static final ModConfigSpec.DoubleValue GUST_CHANCE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue GUST_MIN_MAGNITUDE;
    public static final ModConfigSpec.DoubleValue GUST_MAX_MAGNITUDE;
    public static final ModConfigSpec.IntValue GUST_DURATION_SECONDS;

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

    public static final ModConfigSpec.BooleanValue WINDMILLS_ENABLED;
    public static final ModConfigSpec.DoubleValue WINDMILL_FULL_SPEED_STRENGTH;
    public static final ModConfigSpec.DoubleValue WINDMILL_MAX_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WINDMILL_MIN_DIRECTIONAL_SCALE;
    public static final ModConfigSpec.BooleanValue WINDMILL_REVERSE_WHEN_BEHIND;

    public static final ModConfigSpec.DoubleValue WIND_VANE_FULL_SIGNAL_STRENGTH;

    public static final ModConfigSpec.IntValue BREEZE_MAKER_DURATION_SECONDS;
    public static final ModConfigSpec.DoubleValue BREEZE_MAKER_STRENGTH;
    public static final ModConfigSpec.DoubleValue BREEZE_MAKER_PUSH_RADIUS;
    public static final ModConfigSpec.DoubleValue BREEZE_MAKER_PUSH_STRENGTH;

    public static final ModConfigSpec.BooleanValue WIND_BEARING_ENABLED;
    public static final ModConfigSpec.DoubleValue WIND_BEARING_FACING_OFFSET_DEGREES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Natural wind drift: how the base direction/strength wander over time.").push("drift");
        DRIFT_MAX_DIRECTION_DELTA_DEG = builder
                .comment("Maximum degrees a newly rolled drift target may differ from the current direction.")
                .defineInRange("maxDirectionDeltaDegrees", 20.0, 0.0, 180.0);
        DRIFT_MAX_STRENGTH_DELTA = builder
                .comment("Maximum a newly rolled drift target may differ from the current strength (0-100 scale).")
                .defineInRange("maxStrengthDelta", 10.0, 0.0, 100.0);
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

        builder.comment(
                "How weather sets the range wind wanders in. Natural wind drifts evenly between 0 and",
                "the current weather's cap, so each weather averages half its cap: 25, 37.5 and 50 by default."
        ).push("weather");
        WEATHER_EASE_FACTOR = builder
                .comment("How quickly the range widens or narrows when the weather changes, as a fraction of the remaining difference per second. Higher responds faster; lower eases in more gently.")
                .defineInRange("easeFactor", 0.1, 0.001, 1.0);
        STRENGTH_CAP_CLEAR = builder
                .comment("Top of the range natural wind wanders in while it's neither raining nor thundering, before height scaling.")
                .defineInRange("strengthCapClear", 50.0, 0.0, 100.0);
        STRENGTH_CAP_RAIN = builder
                .comment("Top of the range natural wind wanders in while raining (and not thundering), before height scaling.")
                .defineInRange("strengthCapRain", 75.0, 0.0, 100.0);
        STRENGTH_CAP_THUNDER = builder
                .comment("Top of the range natural wind wanders in while thundering, before height scaling.")
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

        builder.comment(
                "How wind drives Create windmill bearings (M9). Their normal sail-count speed is",
                "multiplied by the elevation-adjusted wind strength at the windmill, then by how",
                "squarely the wind meets the windmill's front face. Requires Create to be installed;",
                "has no effect otherwise. Server-authoritative: the client computes the same",
                "multiplier to spin the sails visually, so both sides must agree on these values."
        ).push("windmills");
        WINDMILLS_ENABLED = builder
                .comment("Whether wind affects Create windmill speed at all. When false, windmills behave exactly as vanilla Create.")
                .define("windmillsEnabled", true);
        WINDMILL_FULL_SPEED_STRENGTH = builder
                .comment("Elevation-adjusted wind strength at which a windmill spins at its normal (unmodified) Create speed. Defaults to half the clear-weather strength cap, so ordinary clear-day wind already drives a windmill somewhat above its normal speed.")
                .defineInRange("windmillFullSpeedStrength", 25.0, 1.0, 100.0);
        WINDMILL_MAX_SPEED_MULTIPLIER = builder
                .comment("Upper bound on the wind speed multiplier. Above 1.0, strong wind overspeeds windmills past their sail rating (1.5 is reached at adjusted strength 75, the rain cap).")
                .defineInRange("windmillMaxSpeedMultiplier", 1.5, 0.0, 5.0);
        WINDMILL_MIN_DIRECTIONAL_SCALE = builder
                .comment("What the speed multiplier falls to when wind runs exactly parallel to the windmill's face. 0 stops it completely; 1 disables directional dampening. Wind within 45 degrees of the face normal is never dampened.")
                .defineInRange("windmillMinDirectionalScale", 0.0, 0.0, 1.0);
        WINDMILL_REVERSE_WHEN_BEHIND = builder
                .comment("Whether wind arriving at the back of a windmill spins it in the opposite direction, instead of driving it forwards regardless of which face it hits.")
                .define("windmillReverseWhenBehind", false);
        builder.pop();

        builder.comment(
                "How the brass wind vane turns wind into a redstone signal (M10). Server-authoritative:",
                "the server computes the signal, and the client reads the same value to pace both vanes'",
                "swing, so a brass vane's swing agrees with its signal."
        ).push("windVane");
        WIND_VANE_FULL_SIGNAL_STRENGTH = builder
                .comment("Elevation-adjusted wind strength at which a brass wind vane outputs a full redstone signal of 15, scaling linearly down to 0 in calm air. Also the strength at which either vane swings toward the wind at full speed. Defaults to the clear-weather strength cap, so a full signal means genuinely strong wind rather than an ordinary day.")
                .defineInRange("windVaneFullSignalStrength", 50.0, 1.0, 300.0);
        builder.pop();

        builder.comment(
                "The breeze maker: right-clicking the air sets the wind to blow the way the player faces,",
                "for a while. Server-authoritative. An operator's /aeroweather override takes precedence."
        ).push("breezeMaker");
        BREEZE_MAKER_DURATION_SECONDS = builder
                .comment("How long a breeze lasts, in seconds, before natural wind resumes. Using the breeze maker again starts a new breeze.")
                .defineInRange("breezeMakerDurationSeconds", 60, 1, 3600);
        BREEZE_MAKER_STRENGTH = builder
                .comment("Wind strength (0-100, before elevation scaling) a breeze sets.")
                .defineInRange("breezeMakerStrength", 50.0, 0.0, 100.0);
        BREEZE_MAKER_PUSH_RADIUS = builder
                .comment("Mobs and other players within this many blocks of the user are blown away from them when a breeze is summoned. 0 disables the push.")
                .defineInRange("breezeMakerPushRadius", 8.0, 0.0, 32.0);
        BREEZE_MAKER_PUSH_STRENGTH = builder
                .comment("How hard they're pushed, on vanilla's knockback scale (an ordinary melee hit is 0.4). Knockback resistance still applies.")
                .defineInRange("breezeMakerPushStrength", 1.0, 0.0, 5.0);
        builder.pop();

        builder.comment(
                "The wind bearing, which turns its assembled contraption to face the wind while it has",
                "rotational power. Ignored unless Create, Create Simulated and Sable are all installed."
        ).push("windBearing");
        WIND_BEARING_ENABLED = builder
                .comment("Whether wind bearings turn toward the wind. When off they hold their contraption still, since nothing else drives them.")
                .define("windBearingEnabled", true);
        WIND_BEARING_FACING_OFFSET_DEGREES = builder
                .comment("Added to the wind's bearing before the contraption is turned to it. A contraption has no inherent front - angle 0 is however it was assembled - so build yours facing north and leave this at 0, or set it to whichever bearing its front faced when assembled (90 for east, and so on).")
                .defineInRange("windBearingFacingOffsetDegrees", 0.0, -180.0, 180.0);
        builder.pop();

        builder.comment(
                "Wind that varies from place to place instead of being one value per dimension.",
                "Direction is bent by a smooth flow map; strength is scaled by the biome, blended",
                "across borders so neighbouring biomes never jump."
        ).push("biomeWind");
        PER_BIOME_WIND_ENABLED = builder
                .comment("Whether wind varies by position at all. Off falls back to one wind direction and strength for the whole dimension, exactly as the mod behaved before 1.5.0.")
                .define("perBiomeWindEnabled", true);
        BIOME_WIND_STRENGTH_INFLUENCE = builder
                .comment("How strongly a biome's own factor counts. 1 applies it in full, 0.5 halves the difference from normal, 0 ignores biomes entirely and leaves only the flow map.")
                .defineInRange("biomeWindStrengthInfluence", 1.0, 0.0, 1.0);
        BIOME_WIND_BLEND_RADIUS_BLOCKS = builder
                .comment("How far a biome's wind bleeds past its border. Larger values make the change more gradual and cost slightly more to compute; a border eases over roughly twice this distance.")
                .defineInRange("biomeWindBlendRadiusBlocks", 48.0, 0.0, 256.0);
        FLOW_MAP_MAX_DIRECTION_DEVIATION_DEGREES = builder
                .comment("The furthest the flow map can bend wind away from the dimension's prevailing direction. 0 keeps direction uniform everywhere and leaves only biome strength variation.")
                .defineInRange("flowMapMaxDirectionDeviationDegrees", 60.0, 0.0, 180.0);
        FLOW_MAP_SCALE_BLOCKS = builder
                .comment("Roughly how many blocks across one feature of the flow map is. Larger values give broad, slowly turning weather systems; smaller ones make wind change direction over shorter distances.")
                .defineInRange("flowMapScaleBlocks", 768.0, 32.0, 8192.0);
        FLOW_MAP_STRENGTH_VARIATION = builder
                .comment("How much the flow map varies strength on top of the biome factor, as a fraction. 0.15 means plus or minus 15 percent, so wind isn't perfectly even within one biome.")
                .defineInRange("flowMapStrengthVariation", 0.15, 0.0, 1.0);
        BIOME_WIND_FACTORS = builder
                .comment(
                        "Per-biome wind strength multipliers, as 'namespace:biome=1.0' entries.",
                        "Above 1 is windier than normal, below 1 is more sheltered.",
                        "This list fills itself in: every biome the game has loaded, modded ones included, is",
                        "added on world load with a starting value guessed from its tags, so you can edit it here.",
                        "Delete an entry to have it guessed again."
                )
                .defineListAllowEmpty("biomeWindFactors", List.of(),
                        () -> "minecraft:plains=1.15",
                        entry -> entry instanceof String text && text.indexOf('=') > 0);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroWeatherCommonConfig() {
    }
}
