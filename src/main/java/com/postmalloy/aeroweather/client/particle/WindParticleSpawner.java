package com.postmalloy.aeroweather.client.particle;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.registry.AeroWeatherParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Spawns {@link WindStreakParticle}s around the client player based on
 * {@link ClientWindState}. Spawn rate/radius/etc. are hardcoded for now;
 * they become configurable in a later milestone (see CLAUDE.md roadmap,
 * M5), matching the same convention used in wind/WindState.
 */
@EventBusSubscriber(modid = AeroWeather.MODID, value = Dist.CLIENT)
public final class WindParticleSpawner {
    private static final float MIN_RADIUS = 8.0F;
    private static final float MAX_RADIUS = 20.0F;
    private static final float LATERAL_JITTER = 4.0F;
    private static final float HEIGHT_JITTER = 3.0F;
    private static final float MAX_PARTICLES_PER_TICK = 1.0F; // at strength 100
    private static final float PARTICLE_SPEED = 0.3F;
    private static final boolean OUTDOORS_ONLY = true;

    private static float spawnAccumulator;

    private WindParticleSpawner() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
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

        float strength = ClientWindState.strength();
        if (strength <= 0.0F) {
            return;
        }

        spawnAccumulator += MAX_PARTICLES_PER_TICK * (strength / 100.0F);
        while (spawnAccumulator >= 1.0F) {
            spawnAccumulator -= 1.0F;
            spawnOne(level, player, ClientWindState.directionDeg());
        }
    }

    private static void spawnOne(ClientLevel level, LocalPlayer player, float directionDeg) {
        RandomSource random = level.random;

        // Unit vector pointing toward the compass bearing wind blows FROM (0=north=-Z, 90=east=+X).
        double bearingRad = Math.toRadians(directionDeg);
        double upwindX = Math.sin(bearingRad);
        double upwindZ = -Math.cos(bearingRad);

        float radius = MIN_RADIUS + random.nextFloat() * (MAX_RADIUS - MIN_RADIUS);
        double x = player.getX() + upwindX * radius + (random.nextFloat() - 0.5) * LATERAL_JITTER;
        double y = player.getEyeY() + (random.nextFloat() - 0.5) * HEIGHT_JITTER;
        double z = player.getZ() + upwindZ * radius + (random.nextFloat() - 0.5) * LATERAL_JITTER;

        if (OUTDOORS_ONLY && !level.canSeeSky(BlockPos.containing(x, y, z))) {
            return;
        }

        // Particles drift downwind: opposite of the upwind spawn-offset direction.
        double xd = -upwindX * PARTICLE_SPEED;
        double zd = -upwindZ * PARTICLE_SPEED;

        level.addParticle(AeroWeatherParticles.WIND_STREAK.get(), x, y, z, xd, 0.0, zd);
    }
}
