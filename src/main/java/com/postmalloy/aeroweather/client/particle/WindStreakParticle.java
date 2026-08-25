package com.postmalloy.aeroweather.client.particle;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.VertexConsumer;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A single wisp used to show wind direction. Cycles through its 8
 * texture frames on a fixed 100ms-per-frame flipbook loop, independent
 * of the particle's own lifetime — this deliberately does NOT use the
 * vanilla {@link TextureSheetParticle#setSpriteFromAge} behavior, which
 * spreads all frames evenly across the lifetime once with no looping.
 * Shared by both registered particle types — {@code wind_streak}
 * (assets/aeroweather/particles/wind_streak.json, spawned continuously)
 * and {@code wind_gust} (wind_gust.json, spawned only while gusting) —
 * since they differ only in which texture set their {@link SpriteSet}
 * resolves to; all orientation/rendering/lifecycle behavior is identical.
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
    private static final int FRAME_COUNT = 8;
    private static final int TICKS_PER_FRAME = 2; // 100ms at 20 ticks/sec

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
        this.lifetime = 24 + this.random.nextInt(17); // 24-40 ticks
        updateSprite();
        // Set the correct age=0 alpha immediately - without this, the particle renders
        // at the default alpha (1.0, fully opaque) for however many frames occur before
        // its first tick() call (which is what actually applies fadeAlpha()), then jumps
        // down to a low value once that first tick fires. That mismatch is the flicker.
        this.setAlpha(fadeAlpha());
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
        updateSprite();
        this.setAlpha(fadeAlpha());
    }

    private void updateSprite() {
        int frame = (this.age / TICKS_PER_FRAME) % FRAME_COUNT;
        // SpriteSet only exposes get(age, maxAge) with an internal age*(size-1)/maxAge formula;
        // passing maxAge = FRAME_COUNT - 1 makes that resolve to exactly `frame`, giving indexed access.
        this.setSprite(this.sprites.get(frame, FRAME_COUNT - 1));
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
