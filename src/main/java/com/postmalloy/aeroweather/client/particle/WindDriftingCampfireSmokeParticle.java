package com.postmalloy.aeroweather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Drop-in replacement for vanilla's {@code campfire_cosy_smoke}/{@code campfire_signal_smoke}
 * particle, registered over vanilla's own provider in {@link AeroWeatherParticleProviders} -
 * {@code ParticleEngine}'s provider map is a plain overwritable {@code HashMap} and
 * {@code RegisterParticleProvidersEvent} fires after vanilla's own bootstrap registration, a
 * standard, safe override point. Identical to vanilla {@link CampfireSmokeParticle} in every
 * respect (construction, lifetime, random jitter, fade-out, sprite selection) since it only
 * adds one step on top: each tick, before letting the vanilla {@code tick()} logic run, xd/zd
 * are eased a small fraction of the way toward {@link AmbientWindDrift}'s wind-driven target
 * velocity, so smoke leans downwind without losing its natural billow.
 */
@OnlyIn(Dist.CLIENT)
public class WindDriftingCampfireSmokeParticle extends CampfireSmokeParticle {
    private static final float EASE_FACTOR = 0.02F;

    private WindDriftingCampfireSmokeParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, boolean signal, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, signal);
        this.setAlpha(signal ? 0.95F : 0.9F);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        Vec3 target = AmbientWindDrift.targetVelocity(this.level, this.x, this.y, this.z);
        if (target != null) {
            this.xd += (target.x - this.xd) * EASE_FACTOR;
            this.zd += (target.z - this.zd) * EASE_FACTOR;
        }
        super.tick();
    }

    @OnlyIn(Dist.CLIENT)
    public static class CosyProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public CosyProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new WindDriftingCampfireSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, false, this.sprites);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class SignalProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SignalProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new WindDriftingCampfireSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, true, this.sprites);
        }
    }
}
