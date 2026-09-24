package com.postmalloy.aeroweather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only wind particle tunables. Loaded as {@code ModConfig.Type.CLIENT},
 * so it's per-player, not shipped with the world/server. See
 * {@link com.postmalloy.aeroweather.client.particle.WindParticleSpawner}
 * for how these are consumed — this class only defines the spec.
 */
public final class AeroWeatherClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue PARTICLES_ENABLED;
    public static final ModConfigSpec.DoubleValue MIN_RADIUS;
    public static final ModConfigSpec.DoubleValue MAX_RADIUS;
    public static final ModConfigSpec.DoubleValue LATERAL_JITTER;
    public static final ModConfigSpec.DoubleValue HEIGHT_JITTER;
    public static final ModConfigSpec.DoubleValue MAX_PARTICLES_PER_TICK;
    public static final ModConfigSpec.DoubleValue MIN_SPEED;
    public static final ModConfigSpec.DoubleValue MAX_SPEED;
    public static final ModConfigSpec.DoubleValue DIRECTION_JITTER_DEG;
    public static final ModConfigSpec.BooleanValue OUTDOORS_ONLY;
    public static final ModConfigSpec.DoubleValue GUST_PARTICLE_MIN_STRENGTH;
    public static final ModConfigSpec.BooleanValue RESTRICT_TO_ACTIVE_CONTRAPTIONS;
    public static final ModConfigSpec.DoubleValue ACTIVE_CONTRAPTION_RADIUS;
    public static final ModConfigSpec.DoubleValue AMBIENT_WIND_PARTICLE_INTENSITY;

    public static final ModConfigSpec.BooleanValue WIND_SOUNDS_ENABLED;
    public static final ModConfigSpec.DoubleValue WIND_SOUND_VOLUME;
    public static final ModConfigSpec.DoubleValue WIND_SOUND_LIGHT_START_STRENGTH;
    public static final ModConfigSpec.DoubleValue WIND_SOUND_LIGHT_FULL_STRENGTH;
    public static final ModConfigSpec.DoubleValue WIND_SOUND_HEAVY_START_STRENGTH;
    public static final ModConfigSpec.DoubleValue WIND_SOUND_HEAVY_FULL_STRENGTH;
    public static final ModConfigSpec.DoubleValue WIND_SOUND_FADE_RATE;

    public static final ModConfigSpec.BooleanValue INTERACTIVE_FOLIAGE_ENABLED;
    public static final ModConfigSpec.BooleanValue INTERACTIVE_FOLIAGE_DIRECTION_ENABLED;
    public static final ModConfigSpec.DoubleValue INTERACTIVE_FOLIAGE_CALM_SWAY_FULL_STRENGTH;
    public static final ModConfigSpec.DoubleValue INTERACTIVE_FOLIAGE_FULL_LEAN_STRENGTH;
    public static final ModConfigSpec.DoubleValue INTERACTIVE_FOLIAGE_STORM_STRENGTH;
    public static final ModConfigSpec.DoubleValue INTERACTIVE_FOLIAGE_EASE_RATE;
    public static final ModConfigSpec.DoubleValue INTERACTIVE_FOLIAGE_MAX_TURN_RATE;

    public static final ModConfigSpec.BooleanValue PARTICLE_RAIN_ENABLED;
    public static final ModConfigSpec.DoubleValue PARTICLE_RAIN_ANGLE_DEGREES;
    public static final ModConfigSpec.DoubleValue PARTICLE_RAIN_REFERENCE_STRENGTH;
    public static final ModConfigSpec.DoubleValue PARTICLE_RAIN_SAND_MIN_ANGLE_DEGREES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Wind direction particle rendering.").push("particles");
        PARTICLES_ENABLED = builder
                .comment("Whether to spawn wind direction particles at all.")
                .define("enabled", true);
        MIN_RADIUS = builder
                .comment("Minimum spawn distance from the player, in blocks.")
                .defineInRange("minRadius", 8.0, 0.0, 128.0);
        MAX_RADIUS = builder
                .comment("Maximum spawn distance from the player, in blocks.")
                .defineInRange("maxRadius", 20.0, 0.0, 128.0);
        LATERAL_JITTER = builder
                .comment("Random horizontal offset applied to each spawn position, in blocks.")
                .defineInRange("lateralJitter", 4.0, 0.0, 32.0);
        HEIGHT_JITTER = builder
                .comment("Random vertical offset applied to each spawn position around eye height, in blocks.")
                .defineInRange("heightJitter", 3.0, 0.0, 32.0);
        MAX_PARTICLES_PER_TICK = builder
                .comment("Particles spawned per client tick at strength 100 (scales linearly down to 0 at strength 0).")
                .defineInRange("maxParticlesPerTick", 0.5, 0.0, 20.0);
        MIN_SPEED = builder
                .comment("Particle drift speed, in blocks/tick, at strength just above 0.")
                .defineInRange("minSpeed", 0.05, 0.0, 5.0);
        MAX_SPEED = builder
                .comment("Particle drift speed, in blocks/tick, at strength 100.")
                .defineInRange("maxSpeed", 0.4, 0.0, 5.0);
        DIRECTION_JITTER_DEG = builder
                .comment("Random per-particle deviation from the wind's exact direction, in degrees.")
                .defineInRange("directionJitterDegrees", 15.0, 0.0, 180.0);
        OUTDOORS_ONLY = builder
                .comment("Whether particles only spawn where they can see the sky (skips spawns inside/under terrain and buildings).")
                .define("outdoorsOnly", true);
        GUST_PARTICLE_MIN_STRENGTH = builder
                .comment("Minimum elevation-adjusted wind strength (0-100 scale) required for the WIND_GUST particle type to spawn, on top of the always-on WIND_STREAK particles.")
                .defineInRange("gustParticleMinStrength", 50.0, 0.0, 100.0);
        RESTRICT_TO_ACTIVE_CONTRAPTIONS = builder
                .comment("If enabled, wind particles only spawn near something the wind is actually acting on - a Sable contraption currently experiencing wind force, or a Create windmill the wind is currently turning - instead of ambiently around the player.")
                .define("restrictToActiveContraptions", false);
        ACTIVE_CONTRAPTION_RADIUS = builder
                .comment("Blocks from a wind-affected contraption or windmill within which wind particles are still allowed to spawn. Only used when restrictToActiveContraptions is enabled.")
                .defineInRange("activeContraptionRadius", 32.0, 0.0, 512.0);
        builder.pop();

        builder.comment("Wind influence on vanilla ambient particles (campfire smoke, falling cherry leaves) - purely cosmetic, no gameplay effect.").push("ambientParticles");
        AMBIENT_WIND_PARTICLE_INTENSITY = builder
                .comment("Target extra drift speed, in blocks/tick, eased into these particles at strength 100 (scales linearly down to 0 at strength 0). 0 disables the effect.")
                .defineInRange("ambientWindParticleIntensity", 0.2, 0.0, 2.0);
        builder.pop();

        builder.comment(
                "Wind influence on the Particle Rain mod's weather particles (rain, snow, sandstorm, mist).",
                "Ignored when that mod isn't installed."
        ).push("particleRain");
        PARTICLE_RAIN_ENABLED = builder
                .comment("Whether AeroWeather's wind drives Particle Rain's particles. When off, Particle Rain uses its own built-in wind instead.")
                .define("particleRainEnabled", true);
        PARTICLE_RAIN_ANGLE_DEGREES = builder
                .comment("How far rain slants from vertical, in degrees, at the reference strength below. Not a maximum: stronger wind keeps steepening the slant, approaching (but never reaching) horizontal. Other particle types keep their own response relative to rain - snow is gentler, sandstorm dust blows nearly sideways.")
                .defineInRange("particleRainAngleDegrees", 45.0, 0.0, 89.0);
        PARTICLE_RAIN_REFERENCE_STRENGTH = builder
                .comment("The elevation-adjusted wind strength (0-100 scale, before elevation scaling can push it higher) at which rain slants by exactly the angle above.")
                .defineInRange("particleRainReferenceStrength", 100.0, 1.0, 300.0);
        PARTICLE_RAIN_SAND_MIN_ANGLE_DEGREES = builder
                .comment("Sandstorm dust never slants less than this many degrees from vertical, however calm the wind - blowing sand shouldn't fall straight down like rain. Because Particle Rain gives every type one shared wind, this floor tilts rain slightly too: about 4 degrees in dead calm at the default, which is far less than it sounds. 0 removes the floor.")
                .defineInRange("particleRainSandMinAngleDegrees", 45.0, 0.0, 89.0);
        builder.pop();

        builder.comment(
                "Interactive Foliage integration. Ignored unless Interactive Foliage is installed.",
                "Our wind decides how far grass leans, and which way, in any weather - not only in rain."
        ).push("interactiveFoliage");
        INTERACTIVE_FOLIAGE_ENABLED = builder
                .comment("Whether AeroWeather's wind drives Interactive Foliage's grass lean. Off hands it back to Interactive Foliage's own rain-only wind.")
                .define("interactiveFoliageEnabled", true);
        INTERACTIVE_FOLIAGE_DIRECTION_ENABLED = builder
                .comment("Whether grass also leans the way our wind blows. Off keeps Interactive Foliage's fixed east wind and only follows our strength. While on, Interactive Foliage's wind shelter behind walls is turned off, since it only knows how to shelter from an east wind.")
                .define("interactiveFoliageDirectionEnabled", true);
        INTERACTIVE_FOLIAGE_CALM_SWAY_FULL_STRENGTH = builder
                .comment("Wind strength at which Interactive Foliage's gentle idle sway reaches full. Below it the sway fades out, so grass stands still in dead calm air. As wind rises further, the lean takes over from the sway.")
                .defineInRange("interactiveFoliageCalmSwayFullStrength", 20.0, 1.0, 300.0);
        INTERACTIVE_FOLIAGE_FULL_LEAN_STRENGTH = builder
                .comment("Wind strength at which grass leans fully. Below it the lean fades toward Interactive Foliage's calm idle sway.")
                .defineInRange("interactiveFoliageFullLeanStrength", 50.0, 1.0, 300.0);
        INTERACTIVE_FOLIAGE_STORM_STRENGTH = builder
                .comment("Wind strength at which grass reaches its storm lean, twice the full lean. Should be above the full-lean strength.")
                .defineInRange("interactiveFoliageStormStrength", 100.0, 1.0, 300.0);
        INTERACTIVE_FOLIAGE_EASE_RATE = builder
                .comment("How quickly the lean and its direction follow the wind, as a fraction of the difference per tick. Higher shows gusts more sharply; lower is smoother.")
                .defineInRange("interactiveFoliageEaseRate", 0.08, 0.001, 1.0);
        INTERACTIVE_FOLIAGE_MAX_TURN_RATE = builder
                .comment("The fastest grass may swing round to a new wind direction, in degrees per second. A full reversal takes at least 180 divided by this. Faster looks snappier but can flicker the gusts at high wind.")
                .defineInRange("interactiveFoliageMaxTurnRate", 90.0, 1.0, 3600.0);
        builder.pop();

        builder.comment(
                "Looping wind ambience. Two layers: a lighter one that fades in first and stays up,",
                "and a heavier one laid over it in strong wind, so the two blend rather than swap.",
                "Both follow the wind where you are standing, so biome and altitude both count."
        ).push("windSounds");
        WIND_SOUNDS_ENABLED = builder
                .comment("Whether wind ambience plays at all.")
                .define("windSoundsEnabled", true);
        WIND_SOUND_VOLUME = builder
                .comment("Overall volume of both layers, on top of Minecraft's own Ambient/Environment slider. 0 is silent, 1 is full.")
                .defineInRange("windSoundVolume", 1.0, 0.0, 1.0);
        WIND_SOUND_LIGHT_START_STRENGTH = builder
                .comment("Elevation-adjusted wind strength at which the light layer becomes audible. Below this there is no wind sound at all.")
                .defineInRange("windSoundLightStartStrength", 10.0, 0.0, 300.0);
        WIND_SOUND_LIGHT_FULL_STRENGTH = builder
                .comment("Strength at which the light layer reaches full volume. It stays there for any stronger wind, so the heavy layer adds to it rather than replacing it.")
                .defineInRange("windSoundLightFullStrength", 50.0, 0.0, 300.0);
        WIND_SOUND_HEAVY_START_STRENGTH = builder
                .comment("Strength at which the heavy layer starts fading in over the light one.")
                .defineInRange("windSoundHeavyStartStrength", 50.0, 0.0, 300.0);
        WIND_SOUND_HEAVY_FULL_STRENGTH = builder
                .comment("Strength at which the heavy layer reaches full volume.")
                .defineInRange("windSoundHeavyFullStrength", 100.0, 0.0, 300.0);
        WIND_SOUND_FADE_RATE = builder
                .comment("How quickly volume chases the wind, as a fraction of the remaining difference per tick. 0.02 takes a couple of seconds to settle; higher is snappier, lower is more gradual.")
                .defineInRange("windSoundFadeRate", 0.02, 0.001, 1.0);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroWeatherClientConfig() {
    }
}
