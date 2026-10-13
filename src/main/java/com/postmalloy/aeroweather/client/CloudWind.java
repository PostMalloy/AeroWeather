package com.postmalloy.aeroweather.client;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WorldWind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Moves the clouds with the wind at cloud height (M18), so they show what the upper
 * layer is doing.
 * <p>
 * Vanilla's clouds have no direction of their own: the renderer looks the cloud
 * texture up at the camera's X plus {@code (ticks + partialTick) * 0.03}, which drifts
 * them west at a fixed 0.6 blocks a second. Sodium's renderer does exactly the same
 * sum. So both mixins make the same two changes — that drift term becomes 0, and the
 * camera X/Z used for the lookup are shifted back by {@link #shiftX}/{@link #shiftZ} —
 * and the clouds then sit wherever this offset has carried them.
 * <p>
 * The offset is integrated here once a client tick, from the wind sampled at the
 * camera's XZ and the dimension's own cloud height, and interpolated by partial tick
 * when drawn, so it's smooth at any frame rate.
 *
 * <h2>Smoothing</h2>
 * The wind reaching the client moves in small steps — a sync whenever direction moves
 * 2° or strength moves 1, roughly once a second — and at cloud height the two layers
 * partly cancel, which magnifies those steps. A first-order ease ({@code v += (target -
 * v) * k}) answers every step with an instant change of acceleration, which reads as the
 * clouds lurching every second or so. So the velocity follows the wind through a
 * critically damped spring instead (the {@code SmoothDamp} form): its acceleration
 * changes gradually too, steps blend into one steady change of course, and it settles
 * without overshoot in about {@code cloudSmoothingSeconds}.
 * <p>
 * Inactive — vanilla clouds, untouched — when the option is off, or no wind has
 * synced for this dimension, as on a server without AeroWeather. Frozen clouds would
 * be a worse fallback than ordinary ones.
 */
@EventBusSubscriber(modid = AeroWeather.MODID, value = Dist.CLIENT)
public final class CloudWind {
    /**
     * The offset is wrapped at this many blocks to keep it small. It's vanilla's own
     * wrap (2048 cloud cells of 12 blocks), a whole number of repeats of any cloud
     * texture up to 2048 pixels wide, so wrapping never shows.
     */
    private static final double WRAP_BLOCKS = 2048.0 * 12.0;


    private static boolean active;
    /** Blocks per tick. */
    private static double velocityX;
    private static double velocityZ;
    /** Blocks per tick per tick: the spring's own rate, which is what keeps the velocity's changes smooth. */
    private static double accelerationX;
    private static double accelerationZ;
    /** How far the clouds have been carried, in blocks; previous is one tick earlier, for interpolation. */
    private static double offsetX;
    private static double offsetZ;
    private static double previousX;
    private static double previousZ;

    private CloudWind() {
    }

    /** Whether the cloud mixins should apply at all this frame. */
    public static boolean active() {
        return active;
    }

    /** The camera X to look the cloud texture up at, so the clouds appear carried by the wind. */
    public static double shiftX(double cameraX) {
        return active ? cameraX - Mth.lerp(partialTick(), previousX, offsetX) : cameraX;
    }

    public static double shiftZ(double cameraZ) {
        return active ? cameraZ - Mth.lerp(partialTick(), previousZ, offsetZ) : cameraZ;
    }

    /** The same partial tick the cloud renderers are handed, so the interpolation lines up with the frame. */
    private static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !AeroWeatherClientConfig.CLOUD_WIND_ENABLED.get()) {
            reset();
            return;
        }
        // Vanilla's clouds stop while paused, and so should these; the partial tick freezes too.
        if (minecraft.isPaused()) {
            return;
        }

        ResourceLocation syncedDimension = ClientWindState.dimension();
        if (syncedDimension == null || !syncedDimension.equals(level.dimension().location())) {
            reset();
            return;
        }

        Vec3 target = targetVelocity(minecraft, level);
        if (!active) {
            // The first reading snaps, so the clouds don't start from a standstill.
            velocityX = target.x;
            velocityZ = target.z;
            active = true;
        }

        double smoothTicks = AeroWeatherClientConfig.CLOUD_SMOOTHING_SECONDS.getAsDouble() * 20.0;
        double startX = velocityX;
        double startZ = velocityZ;
        if (smoothTicks <= 0.0) {
            velocityX = target.x;
            velocityZ = target.z;
            accelerationX = 0.0;
            accelerationZ = 0.0;
        } else {
            // Unity's SmoothDamp, one tick at a time. omega = 2 / smoothTime makes it settle in
            // about smoothTime; the polynomial stands in for exp(-omega), and is stable for any step.
            double omega = 2.0 / smoothTicks;
            double decay = 1.0 / (1.0 + omega + 0.48 * omega * omega + 0.235 * omega * omega * omega);
            double tempX = accelerationX + omega * (velocityX - target.x);
            double tempZ = accelerationZ + omega * (velocityZ - target.z);
            accelerationX = (accelerationX - omega * tempX) * decay;
            accelerationZ = (accelerationZ - omega * tempZ) * decay;
            velocityX = target.x + (velocityX - target.x + tempX) * decay;
            velocityZ = target.z + (velocityZ - target.z + tempZ) * decay;
        }

        // Trapezoidal: the distance covered while the velocity moved from start to its new value.
        previousX = offsetX;
        previousZ = offsetZ;
        offsetX += (startX + velocityX) * 0.5;
        offsetZ += (startZ + velocityZ) * 0.5;

        // Shift both by the same amount, so the interpolation between them is unaffected.
        double wrapX = Math.floor(offsetX / WRAP_BLOCKS) * WRAP_BLOCKS;
        double wrapZ = Math.floor(offsetZ / WRAP_BLOCKS) * WRAP_BLOCKS;
        offsetX -= wrapX;
        previousX -= wrapX;
        offsetZ -= wrapZ;
        previousZ -= wrapZ;
    }

    /** Blocks per tick the clouds should move: the wind at cloud height, above the camera. */
    private static Vec3 targetVelocity(Minecraft minecraft, ClientLevel level) {
        float cloudHeight = level.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight)) {
            return Vec3.ZERO;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        WorldWind.Sample wind = WorldWind.at(level, camera.x, cloudHeight, camera.z,
                ClientWindState.directionDeg(), ClientWindState.strength());
        double blocksPerTick = AeroWeatherClientConfig.CLOUD_SPEED_AT_FULL_STRENGTH.getAsDouble()
                * (wind.strength() / 100.0) / 20.0;
        return WindDirection.travelVector(wind.directionDeg()).scale(blocksPerTick);
    }

    private static void reset() {
        active = false;
        velocityX = 0.0;
        velocityZ = 0.0;
        accelerationX = 0.0;
        accelerationZ = 0.0;
        offsetX = 0.0;
        offsetZ = 0.0;
        previousX = 0.0;
        previousZ = 0.0;
    }
}
