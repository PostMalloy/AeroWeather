package com.postmalloy.aeroweather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A single wisp used to show wind direction. Cycles through its 8
 * texture frames (assets/aeroweather/particles/wind_streak.json) on a
 * fixed 200ms-per-frame flipbook loop, independent of the particle's own
 * lifetime — this deliberately does NOT use the vanilla
 * {@link TextureSheetParticle#setSpriteFromAge} behavior, which spreads
 * all frames evenly across the lifetime once with no looping.
 */
@OnlyIn(Dist.CLIENT)
public class WindStreakParticle extends TextureSheetParticle {
    private static final int FRAME_COUNT = 8;
    private static final int TICKS_PER_FRAME = 4; // 200ms at 20 ticks/sec

    private final SpriteSet sprites;

    private WindStreakParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.quadSize = 0.4F + this.random.nextFloat() * 0.3F;
        this.lifetime = 24 + this.random.nextInt(17); // 24-40 ticks
        updateSprite();
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
