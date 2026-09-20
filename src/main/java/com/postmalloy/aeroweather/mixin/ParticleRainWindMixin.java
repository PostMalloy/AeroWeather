package com.postmalloy.aeroweather.mixin;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.postmalloy.aeroweather.integration.particlerain.ParticleRainWind;

/**
 * Makes Particle Rain's weather particles blow with AeroWeather's wind instead
 * of the position/time noise the mod generates for itself.
 * <p>
 * <b>Deliberately free of Particle Rain types</b>, exactly like
 * {@code WindmillBearingBlockEntityMixin}: the target is named by string, and
 * {@code getWind}'s whole descriptor is {@code (DDD)Lorg/joml/Vector3f;} — JOML
 * and primitives, both of which we already have. So Particle Rain needs no
 * compile dependency at all, only {@code localRuntime} for dev-client testing.
 * Do not "tidy" this into typed references.
 * <p>
 * {@code ParticleRain.getWind} is the mod's single wind source, called from just
 * two places: {@code CustomParticle}'s constructor (a particle's initial
 * velocity) and {@code CustomParticle.tickWind()} (the per-tick acceleration).
 * Replacing its return value therefore covers spawn and flight for every
 * weather particle at once — rain, snow, sandstorm, mist, and anything a
 * resource pack adds, since v4 defines particles as data rather than classes.
 * <p>
 * When Particle Rain isn't installed this mixin is never registered at all —
 * {@code AeroWeatherMixinPlugin} withholds it, so the missing target class is
 * never even resolved.
 */
@Mixin(targets = "pigcart.particlerain.ParticleRain", remap = false)
public class ParticleRainWindMixin {
    /**
     * HEAD with cancellation rather than modifying the return value: our wind
     * replaces the mod's outright, so there's no reason to let it compute its
     * noise first. Returning without setting a value leaves Particle Rain's own
     * wind in place, which is how the integration's off switch works.
     */
    @Inject(method = "getWind", at = @At("HEAD"), cancellable = true)
    private static void aeroweather$replaceWithSimulatedWind(double x, double y, double z,
            CallbackInfoReturnable<Vector3f> cir) {
        Vector3f wind = ParticleRainWind.windAt(x, y, z);
        if (wind != null) {
            cir.setReturnValue(wind);
        }
    }
}
