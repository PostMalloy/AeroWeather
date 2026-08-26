package com.postmalloy.aeroweather.client.particle;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A single wisp used to show wind direction. Cycles through its 8
 * texture frames exactly once over the particle's own lifetime, via
 * vanilla {@link TextureSheetParticle#setSpriteFromAge}, so the
 * animation speed is tied to how long that particular particle happens
 * to live rather than a fixed cadence — a fixed per-frame duration was
 * tried first but looped 1.5-2.5x before the particle disappeared,
 * which read as stuttery. Lifetime itself is derived from wind speed
 * (recovered from the constructor's {@code xSpeed}/{@code zSpeed} —
 * {@link ParticleProvider#createParticle} has a fixed vanilla signature
 * with no room for an explicit strength parameter, but its velocity
 * magnitude already equals {@code WindParticleSpawner}'s locally
 * adjusted drift speed exactly, since travel direction is a unit
 * vector) so stronger wind means a shorter life and thus a
 * faster-cycling flipbook, with a small random jitter on top for
 * variety; because {@code setSpriteFromAge} always spans the full
 * frame set over {@code [0, lifetime]} regardless of what lifetime
 * is, "exactly one cycle per particle" holds automatically no matter
 * how lifetime is computed. Shared by both registered
 * particle types — {@code wind_streak}
 * (assets/aeroweather/particles/wind_streak.json, spawned continuously)
 * and {@code wind_gust} (wind_gust.json, spawned only above a strength
 * threshold) — since they differ only in which texture set their
 * {@link SpriteSet} resolves to; all orientation/rendering/lifecycle
 * behavior is identical.
 * <p>
 * Orientation is a vertical card, fixed in world space, computed once at
 * spawn from the travel direction and never touched again — no camera
 * dependency at all. The card's normal is perpendicular to the wind's
 * travel direction (the travel direction lies in the card's own plane,
 * as its width axis), matching how the texture is meant to read: face-on
 * when you look across the wind, edge-on when you look straight up or
 * downwind. Earlier attempts tried blending toward a camera-facing
 * billboard so the card would never go edge-on, but that added
 * camera-tracking rotation the texture was never meant to have and
 * repeatedly got the billboard math wrong; a plain fixed orientation
 * derived only from wind direction is simpler and matches the texture's
 * intent directly.
 */
@OnlyIn(Dist.CLIENT)
public class WindStreakParticle extends TextureSheetParticle {
    // Lifetime range at the extremes of the configured drift-speed range - faster wind
    // means a shorter life (and, via setSpriteFromAge, a faster-cycling flipbook).
    private static final int MIN_LIFETIME_TICKS = 12;
    private static final int MAX_LIFETIME_TICKS = 40;
    private static final int LIFETIME_JITTER_TICKS = 6;

    private final SpriteSet sprites;
    private final Quaternionf orientation;
    private final boolean mirrored;

    private WindStreakParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        // Deliberately NOT the 7-arg super(..., xSpeed, ySpeed, zSpeed): that chains to
        // vanilla Particle's randomizing constructor, which jitters each component by
        // up to +/-0.4 and unconditionally adds +0.1 to yd before renormalizing - it
        // does not just pass the given velocity through. Set xd/yd/zd ourselves instead.
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xSpeed;
        this.yd = 0.0; // clamp initial vertical speed to 0 for now
        this.zd = zSpeed;
        this.orientation = orientationForTravelDirection(xSpeed, zSpeed);
        this.mirrored = isLeftOfUpwindPlayer(x, z, xSpeed, zSpeed);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.quadSize = 0.4F + this.random.nextFloat() * 0.3F;
        this.lifetime = lifetimeForSpeed(xSpeed, zSpeed, this.random);
        this.setSpriteFromAge(this.sprites);
        // Set the correct age=0 alpha immediately - without this, the particle renders
        // at the default alpha (1.0, fully opaque) for however many frames occur before
        // its first tick() call (which is what actually applies fadeAlpha()), then jumps
        // down to a low value once that first tick fires. That mismatch is the flicker.
        this.setAlpha(fadeAlpha());
    }

    /**
     * Recovers wind speed from the constructor's velocity (the only wind-speed-correlated
     * data available through {@link ParticleProvider}'s fixed signature) and maps it onto
     * {@code [MIN_SPEED, MAX_SPEED]} from {@link AeroWeatherClientConfig} to get a fraction,
     * then interpolates the lifetime range inversely - stronger wind, shorter life, faster
     * flipbook - with a small random offset layered on for visual variety.
     */
    private static int lifetimeForSpeed(double xSpeed, double zSpeed, RandomSource random) {
        double speed = Math.sqrt(xSpeed * xSpeed + zSpeed * zSpeed);
        float minSpeed = (float) AeroWeatherClientConfig.MIN_SPEED.getAsDouble();
        float maxSpeed = (float) AeroWeatherClientConfig.MAX_SPEED.getAsDouble();
        float speedFraction = maxSpeed > minSpeed
                ? Math.clamp((float) ((speed - minSpeed) / (maxSpeed - minSpeed)), 0.0F, 1.0F)
                : 0.0F;
        int baseLifetime = Math.round(MAX_LIFETIME_TICKS - speedFraction * (MAX_LIFETIME_TICKS - MIN_LIFETIME_TICKS));
        int jitter = random.nextInt(LIFETIME_JITTER_TICKS * 2 + 1) - LIFETIME_JITTER_TICKS;
        return Math.max(1, baseLifetime + jitter);
    }

    private static Quaternionf orientationForTravelDirection(double xSpeed, double zSpeed) {
        double lengthSq = xSpeed * xSpeed + zSpeed * zSpeed;
        if (lengthSq < 1.0E-8) {
            return new Quaternionf();
        }
        double length = Math.sqrt(lengthSq);
        float travelX = (float) (xSpeed / length);
        float travelZ = (float) (zSpeed / length);
        // rotationY(theta) maps local +Z (the quad's normal) to (sin theta, 0, cos theta);
        // solving sin theta = travelX, cos theta = travelZ would make the normal equal the
        // travel direction (face-on looking along the wind) - but that's backwards from what's
        // wanted: the card's normal should be perpendicular to the wind, i.e. the travel
        // direction should lie IN the card's plane instead. Adding a quarter turn swaps which
        // horizontal axis is the normal vs. the in-plane one.
        float yaw = (float) Math.atan2(travelX, travelZ) + (float) (Math.PI / 2.0);
        return new Quaternionf().rotationY(yaw);
    }

    /**
     * All particles from the same wind share one fixed orientation, so
     * without correction a particle to the player's left and its mirror
     * counterpart to the right would show the exact same texture
     * handedness instead of reading as a symmetric pair - like two
     * right hands instead of a left and a right. "Left" is relative to
     * facing upwind (i.e. facing the direction the wind blows FROM, the
     * reference direction the whole orientation scheme is built around),
     * not the player's actual current camera facing, since orientation
     * is fixed at spawn and doesn't track the camera. For a direction
     * D=(dx,dz), "left of someone facing D" is (dz,-dx); here D is
     * upwind = -travelDirection, so left = (-travelZ, travelX). A point
     * is on that side when its offset from the player has a positive
     * dot product with that vector, which reduces to the sign of
     * travelX*offsetZ - travelZ*offsetX (equivalently xSpeed*offsetZ -
     * zSpeed*offsetX, since velocity is travel direction times a
     * positive speed and scaling doesn't flip a sign).
     */
    private static boolean isLeftOfUpwindPlayer(double x, double z, double xSpeed, double zSpeed) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        double offsetX = x - player.getX();
        double offsetZ = z - player.getZ();
        return (xSpeed * offsetZ - zSpeed * offsetX) > 0.0;
    }

    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        return (quaternion, camera, partialTick) -> quaternion.set(this.orientation);
    }

    @Override
    protected float getU0() {
        return this.mirrored ? this.sprite.getU1() : this.sprite.getU0();
    }

    @Override
    protected float getU1() {
        return this.mirrored ? this.sprite.getU0() : this.sprite.getU1();
    }

    /**
     * Draws the quad twice, once with reversed winding, so it's visible
     * regardless of backface culling. {@link ParticleRenderType#PARTICLE_SHEET_TRANSLUCENT}
     * never explicitly sets a cull state (see its {@code begin()}), so
     * particles inherit whatever culling was left enabled by whatever
     * rendered immediately before them in the frame - not something this
     * mod controls or should assume. A fixed world-space card (unlike a
     * camera billboard, which is always constructed facing the viewer)
     * can plausibly end up back-face-out from a given angle, so rather
     * than guess at the ambient GL state, submit both winding orders.
     * Rotating 180 degrees around the local Y axis flips the local X and
     * Z axes (width and normal) while leaving Y (up) alone, which is
     * exactly what reverses the vertex winding for the same visual plane.
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Quaternionf quaternion = new Quaternionf(this.orientation);
        this.renderRotatedQuad(buffer, camera, quaternion, partialTicks);
        quaternion.rotateY((float) Math.PI);
        this.renderRotatedQuad(buffer, camera, quaternion, partialTicks);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.move(this.xd, this.yd, this.zd);
        this.setSpriteFromAge(this.sprites);
        this.setAlpha(fadeAlpha());
    }

    private float fadeAlpha() {
        int fadeTicks = Math.min(6, this.lifetime / 4);
        if (this.age < fadeTicks) {
            return this.age / (float) fadeTicks;
        }
        int remaining = this.lifetime - this.age;
        if (remaining < fadeTicks) {
            return remaining / (float) fadeTicks;
        }
        return 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new WindStreakParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
