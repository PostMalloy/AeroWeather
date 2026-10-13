package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.postmalloy.aeroweather.client.CloudWind;

/**
 * Sodium's clouds follow the wind (M18), as {@link LevelRendererCloudsMixin} does for
 * vanilla's. Sodium cancels vanilla's {@code renderClouds} and calls its own
 * {@code CloudRenderer.render}, which works out the same lookup the same way:
 * {@code camera.getPosition().x() + (ticks + partialTick) * 0.03} and {@code .z() + 0.33}.
 * So the same two changes apply. Those {@code x()}/{@code z()} calls are the method's
 * only ones; its later fog check reads the camera's fields directly, and is unaffected.
 * <p>
 * Names Sodium only by string, like the other optional-mod mixins, and is withheld by
 * {@code AeroWeatherMixinPlugin} unless Sodium is installed. Verified against
 * {@code sodium-neoforge-0.8.13+mc1.21.1}. {@code require = 0}: Sodium's internals
 * aren't API, and a change there should cost us moving clouds, not the game.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer", remap = false)
public class SodiumCloudsMixin {
    @ModifyConstant(method = "render", constant = @Constant(floatValue = 0.03F), require = 0)
    private float aeroweather$stopSodiumDrift(float drift) {
        return CloudWind.active() ? 0.0F : drift;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;x()D", ordinal = 0), require = 0)
    private double aeroweather$carryCloudsX(double cameraX) {
        return CloudWind.shiftX(cameraX);
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;z()D", ordinal = 0), require = 0)
    private double aeroweather$carryCloudsZ(double cameraZ) {
        return CloudWind.shiftZ(cameraZ);
    }
}
