package com.postmalloy.aeroweather.client.particle;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.client.ClientActiveContraptions;
import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.registry.AeroWeatherParticles;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WindHeightScaling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Spawns {@link WindStreakParticle}s in a ring around the client player
 * based on {@link ClientWindState} — particles spawn at a random angle
 * (not just upwind) and drift in the wind's travel direction regardless
 * of where they started, so the ones that happen to spawn downwind
 * simply have less visible travel time before expiring. Both spawn rate
 * and drift speed scale with wind strength. Strength is additionally
 * scaled by the player's elevation via {@link WindHeightScaling} - 0 at
 * or below sea level, ramping up above it - computed once per tick from
 * the player's own Y rather than per particle, since the small amount of
 * per-particle height jitter isn't meaningful against the scale this
 * curve operates over. Particle tuning comes from
 * {@link AeroWeatherClientConfig}; the height-scaling curve itself lives
 * in the common config instead (it's server-authoritative - see
 * {@code AeroWeatherCommand}'s "wind info" output, which reports the
 * same elevation-adjusted value).
 * <p>
 * A second particle type, {@code WIND_GUST}, uses the identical
 * spawn/rate logic but only once the elevation-adjusted strength exceeds
 * {@link AeroWeatherClientConfig#GUST_PARTICLE_MIN_STRENGTH} (default
 * 50/100), via its own accumulator so its rate doesn't borrow from or
 * interfere with the always-on {@code WIND_STREAK} spawning. A third
 * type, {@code WIND_LOOP}, mirrors {@code WIND_STREAK}'s exact
 * always-on spawn-rate formula via its own accumulator, but each
 * trigger only actually spawns a particle on a 50% coin flip — a
 * rarer companion texture layered in for visual variety, not an
 * additional full-rate particle stream.
 * <p>
 * If {@link AeroWeatherClientConfig#RESTRICT_TO_ACTIVE_CONTRAPTIONS} is
 * enabled, both particle types are suppressed entirely unless the player
 * is within {@link AeroWeatherClientConfig#ACTIVE_CONTRAPTION_RADIUS} of
 * a Sable sub-level currently experiencing real wind force — synced from
 * {@code AeronauticsWindForceApplier} via {@link ClientActiveContraptions}.
 * Off by default, so ambient particles work exactly as before unless a
 * player opts in.
 */
@EventBusSubscriber(modid = AeroWeather.MODID, value = Dist.CLIENT)
public final class WindParticleSpawner {
    private static float spawnAccumulator;
    private static float gustSpawnAccumulator;
    private static float loopSpawnAccumulator;

    private WindParticleSpawner() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!AeroWeatherClientConfig.PARTICLES_ENABLED.get()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) {
            return;
        }

        ResourceLocation syncedDimension = ClientWindState.dimension();
        if (syncedDimension == null || !syncedDimension.equals(level.dimension().location())) {
            return;
        }

        float baseStrength = ClientWindState.strength();
        if (baseStrength <= 0.0F) {
            return;
        }

        float strength = WindHeightScaling.scale(baseStrength, player.getY(), level.getSeaLevel(),
                AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_EXPONENT.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_MAX_MULTIPLIER.getAsDouble());
        if (strength <= 0.0F) {
            return;
        }

        if (AeroWeatherClientConfig.RESTRICT_TO_ACTIVE_CONTRAPTIONS.get() && !isNearActiveContraption(level, player)) {
            return;
        }

        float maxParticlesPerTick = (float) AeroWeatherClientConfig.MAX_PARTICLES_PER_TICK.getAsDouble();
        spawnAccumulator += maxParticlesPerTick * (strength / 100.0F);
        while (spawnAccumulator >= 1.0F) {
            spawnAccumulator -= 1.0F;
            spawnOne(level, player, ClientWindState.directionDeg(), strength, AeroWeatherParticles.WIND_STREAK.get());
        }

        // WIND_LOOP mirrors WIND_STREAK's exact spawn-rate formula, but only half of its
        // triggers actually spawn a particle - a rarer companion texture for visual variety.
        loopSpawnAccumulator += maxParticlesPerTick * (strength / 100.0F);
        while (loopSpawnAccumulator >= 1.0F) {
            loopSpawnAccumulator -= 1.0F;
            if (level.random.nextBoolean()) {
                spawnOne(level, player, ClientWindState.directionDeg(), strength, AeroWeatherParticles.WIND_LOOP.get());
            }
        }

        float gustMinStrength = (float) AeroWeatherClientConfig.GUST_PARTICLE_MIN_STRENGTH.getAsDouble();
        if (strength > gustMinStrength) {
            gustSpawnAccumulator += maxParticlesPerTick * (strength / 100.0F);
            while (gustSpawnAccumulator >= 1.0F) {
                gustSpawnAccumulator -= 1.0F;
                spawnOne(level, player, ClientWindState.directionDeg(), strength, AeroWeatherParticles.WIND_GUST.get());
            }
        } else {
            gustSpawnAccumulator = 0.0F;
        }
    }

    /**
     * True if the player is within {@code AeroWeatherClientConfig.ACTIVE_CONTRAPTION_RADIUS}
     * of any position in {@link ClientActiveContraptions} — Sable sub-levels
     * currently experiencing actual wind force (0 lift-tagged blocks never
     * reach this synced list at all, per how the server builds it, so no
     * separate check is needed here for that case).
     */
    private static boolean isNearActiveContraption(ClientLevel level, LocalPlayer player) {
        ResourceLocation dimension = ClientActiveContraptions.dimension();
        if (dimension == null || !dimension.equals(level.dimension().location())) {
            return false;
        }
        double radius = AeroWeatherClientConfig.ACTIVE_CONTRAPTION_RADIUS.getAsDouble();
        double radiusSq = radius * radius;
        Vec3 playerPos = player.position();
        for (Vec3 position : ClientActiveContraptions.positions()) {
            if (playerPos.distanceToSqr(position) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private static void spawnOne(ClientLevel level, LocalPlayer player, float directionDeg, float strength, SimpleParticleType particleType) {
        RandomSource random = level.random;

        float minRadius = (float) AeroWeatherClientConfig.MIN_RADIUS.getAsDouble();
        float maxRadius = (float) AeroWeatherClientConfig.MAX_RADIUS.getAsDouble();
        float lateralJitter = (float) AeroWeatherClientConfig.LATERAL_JITTER.getAsDouble();
        float heightJitter = (float) AeroWeatherClientConfig.HEIGHT_JITTER.getAsDouble();

        // Spawn at a random angle around the player - particles drift with the
        // wind regardless of where they started, they don't need to start upwind.
        double spawnAngle = random.nextDouble() * (Math.PI * 2.0);
        float radius = minRadius + random.nextFloat() * (maxRadius - minRadius);
        double x = player.getX() + Math.sin(spawnAngle) * radius + (random.nextFloat() - 0.5) * lateralJitter;
        double y = player.getEyeY() + (random.nextFloat() - 0.5) * heightJitter;
        double z = player.getZ() + Math.cos(spawnAngle) * radius + (random.nextFloat() - 0.5) * lateralJitter;

        if (AeroWeatherClientConfig.OUTDOORS_ONLY.get() && !level.canSeeSky(BlockPos.containing(x, y, z))) {
            return;
        }

        // Travel direction: wind blows FROM directionDeg TOWARD the opposite bearing,
        // with a small per-particle deviation so the drift doesn't look perfectly uniform.
        float directionJitterDeg = (float) AeroWeatherClientConfig.DIRECTION_JITTER_DEG.getAsDouble();
        float jitteredDirectionDeg = directionDeg + (random.nextFloat() * 2.0F - 1.0F) * directionJitterDeg;
        Vec3 travel = WindDirection.travelVector(jitteredDirectionDeg);

        float minSpeed = (float) AeroWeatherClientConfig.MIN_SPEED.getAsDouble();
        float maxSpeed = (float) AeroWeatherClientConfig.MAX_SPEED.getAsDouble();
        float speed = minSpeed + (maxSpeed - minSpeed) * (strength / 100.0F);
        double xd = travel.x * speed;
        double zd = travel.z * speed;

        level.addParticle(particleType, x, y, z, xd, 0.0, zd);
    }
}
