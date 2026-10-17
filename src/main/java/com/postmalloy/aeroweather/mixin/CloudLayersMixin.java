package com.postmalloy.aeroweather.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.postmalloy.aeroweather.integration.cloudlayers.CloudLayersWind;

/**
 * Cloud Layers' clouds follow the wind, each deck at its own height, and Better Cloud
 * Shadows' shadows with them. See {@link CloudLayersWind}.
 * <p>
 * Hooks the {@code Layer} record's own {@code velocity()} and {@code offset()}
 * accessors rather than the renderer, because they're what both readers share: Cloud
 * Layers' {@code CloudRenderer.render} and Better Cloud Shadows' {@code CloudLayersBridge}
 * each call both (twice, for X and Z) and nothing else does, bar the record's codec,
 * which only uses them to encode. The record's {@code equals}/{@code hashCode} read the
 * fields themselves, so the cache Cloud Layers keys on a layer and the signature Better
 * Cloud Shadows takes from it are untouched.
 * <p>
 * The two fields shadowed are declared on the record itself, so {@code @Shadow} reaches
 * them (see M9's note); both are primitives, so this names no Cloud Layers type. Withheld
 * by {@code AeroWeatherMixinPlugin} unless {@code cloud_layers} is installed. Verified
 * against {@code cloud_layers-neoforge-1.21-2.0} and {@code 2.1}. {@code require = 0}:
 * its internals aren't API, and a change there should cost us moving clouds, not the game.
 */
@Mixin(targets = "mod.lwhrvw.cloud_layers.Layer", remap = false)
public abstract class CloudLayersMixin {
    @Shadow
    @Final
    private double height;

    @Shadow
    @Final
    private boolean skyboxMode;

    @ModifyReturnValue(method = "velocity", at = @At("RETURN"), require = 0)
    private List<Double> aeroweather$stillAir(List<Double> velocity) {
        return CloudLayersWind.velocity(velocity);
    }

    @ModifyReturnValue(method = "offset", at = @At("RETURN"), require = 0)
    private List<Double> aeroweather$carryWithWind(List<Double> offset) {
        return CloudLayersWind.offset(offset, this.height, this.skyboxMode);
    }
}
