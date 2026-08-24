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
 * Spawns {@link WindStreakParticle}s in a ring around the client player
 * based on {@link ClientWindState} — particles spawn at a random angle
 * (not just upwind) and drift in the wind's travel direction regardless
 * of where they started, so the ones that happen to spawn downwind
 * simply have less visible travel time before expiring. Both spawn rate
 * and drift speed scale with wind strength. Spawn rate/radius/etc. are
 * hardcoded for now; they become configurable in a later milestone (see
 * CLAUDE.md roadmap, M5), matching the same convention used in
 * wind/WindState.
 */
@EventBusSubscriber(modid = AeroWeather.MODID, value = Dist.CLIENT)
public final class WindParticleSpawner {
    private static final float MIN_RADIUS = 8.0F;
    private static final float MAX_RADIUS = 20.0F;
    private static final float LATERAL_JITTER = 4.0F;
    private static final float HEIGHT_JITTER = 3.0F;
    private static final float MAX_PARTICLES_PER_TICK = 1.0F; // at strength 100
    private static final float MIN_SPEED = 0.05F; // at strength >0
    private static final float MAX_SPEED = 0.4F; // at strength 100
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
            spawnOne(level, player, ClientWindState.directionDeg(), strength);
        }
    }

    private static void spawnOne(ClientLevel level, LocalPlayer player, float directionDeg, float strength) {
        RandomSource random = level.random;

        // Spawn at a random angle around the player - particles drift with the
        // wind regardless of where they started, they don't need to start upwind.
        double spawnAngle = random.nextDouble() * (Math.PI * 2.0);
        float radius = MIN_RADIUS + random.nextFloat() * (MAX_RADIUS - MIN_RADIUS);
        double x = player.getX() + Math.sin(spawnAngle) * radius + (random.nextFloat() - 0.5) * LATERAL_JITTER;
        double y = player.getEyeY() + (random.nextFloat() - 0.5) * HEIGHT_JITTER;
        double z = player.getZ() + Math.cos(spawnAngle) * radius + (random.nextFloat() - 0.5) * LATERAL_JITTER;

        if (OUTDOORS_ONLY && !level.canSeeSky(BlockPos.containing(x, y, z))) {
            return;
        }

        // Travel direction: wind blows FROM directionDeg TOWARD the opposite bearing.
        double bearingRad = Math.toRadians(directionDeg);
        double travelX = -Math.sin(bearingRad);
        double travelZ = Math.cos(bearingRad);

        float speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * (strength / 100.0F);
        double xd = travelX * speed;
        double zd = travelZ * speed;

        level.addParticle(AeroWeatherParticles.WIND_STREAK.get(), x, y, z, xd, 0.0, zd);
    }
}
