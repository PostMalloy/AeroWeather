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
                .defineInRange("maxParticlesPerTick", 1.0, 0.0, 20.0);
        MIN_SPEED = builder
                .comment("Particle drift speed, in blocks/tick, at strength just above 0.")
                .defineInRange("minSpeed", 0.05, 0.0, 5.0);
        MAX_SPEED = builder
                .comment("Particle drift speed, in blocks/tick, at strength 100.")
                .defineInRange("maxSpeed", 0.4, 0.0, 5.0);
        DIRECTION_JITTER_DEG = builder
                .comment("Random per-particle deviation from the wind's exact direction, in degrees.")
                .defineInRange("directionJitterDegrees", 5.0, 0.0, 180.0);
        OUTDOORS_ONLY = builder
                .comment("Whether particles only spawn where they can see the sky (skips spawns inside/under terrain and buildings).")
                .define("outdoorsOnly", true);
        GUST_PARTICLE_MIN_STRENGTH = builder
                .comment("Minimum elevation-adjusted wind strength (0-100 scale) required for the WIND_GUST particle type to spawn, on top of the always-on WIND_STREAK particles.")
                .defineInRange("gustParticleMinStrength", 50.0, 0.0, 100.0);
        RESTRICT_TO_ACTIVE_CONTRAPTIONS = builder
                .comment("If enabled, wind particles (both types) only spawn near a Sable contraption currently experiencing wind force, instead of ambiently around the player.")
                .define("restrictToActiveContraptions", false);
        ACTIVE_CONTRAPTION_RADIUS = builder
                .comment("Blocks from an active contraption within which wind particles are still allowed to spawn. Only used when restrictToActiveContraptions is enabled.")
                .defineInRange("activeContraptionRadius", 32.0, 0.0, 512.0);
        builder.pop();

        builder.comment("Wind influence on vanilla ambient particles (campfire smoke, falling cherry leaves) - purely cosmetic, no gameplay effect.").push("ambientParticles");
        AMBIENT_WIND_PARTICLE_INTENSITY = builder
                .comment("Target extra drift speed, in blocks/tick, eased into these particles at strength 100 (scales linearly down to 0 at strength 0). 0 disables the effect.")
                .defineInRange("ambientWindParticleIntensity", 0.2, 0.0, 2.0);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroWeatherClientConfig() {
    }
}
