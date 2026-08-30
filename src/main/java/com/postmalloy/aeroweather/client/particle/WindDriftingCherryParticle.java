package com.postmalloy.aeroweather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CherryParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Drop-in replacement for vanilla's {@code cherry_leaves} particle (falling petals/leaves),
 * registered over vanilla's own provider - see {@link WindDriftingCampfireSmokeParticle} for
 * why overriding an already-registered vanilla particle type is safe. Identical to vanilla
 * {@link CherryParticle} in every respect (its own per-particle swirl/spin/fall curve, gravity,
 * friction) since it only adds one step on top: each tick, before letting the vanilla
 * {@code tick()} logic run, xd/zd are eased a small fraction of the way toward
 * {@link AmbientWindDrift}'s wind-driven target velocity, so falling leaves drift downwind
 * without losing their natural tumble. 1.21.1 has no other vanilla falling-leaves particle
 * type (e.g. Pale Garden's pale oak leaves weren't added yet), so this is the only target.
 */
@OnlyIn(Dist.CLIENT)
public class WindDriftingCherryParticle extends CherryParticle {
    private static final float EASE_FACTOR = 0.02F;

    private WindDriftingCherryParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites);
    }

    @Override
    public void tick() {
        Vec3 target = AmbientWindDrift.targetVelocity(this.level, this.y);
        if (target != null) {
            this.xd += (target.x - this.xd) * EASE_FACTOR;
            this.zd += (target.z - this.zd) * EASE_FACTOR;
        }
        super.tick();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new WindDriftingCherryParticle(level, x, y, z, this.sprites);
        }
    }
}
