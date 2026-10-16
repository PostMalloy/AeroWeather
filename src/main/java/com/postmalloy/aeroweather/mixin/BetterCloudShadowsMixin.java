package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.postmalloy.aeroweather.client.CloudWind;

/**
 * Better Cloud Shadows' shadows stay under clouds that follow the wind (M18).
 * <p>
 * The mod doesn't read the clouds' position from the renderer; it works it out again on
 * its own, in {@code VanillaClouds.field}, with vanilla's own sum: the cloud texture's
 * origin at {@code -(ticks + partialTick) * 0.03} on X and {@code -0.33} of a cell on Z.
 * That serves both vanilla's clouds and Sodium's. So once {@link LevelRendererCloudsMixin}
 * or {@link SodiumCloudsMixin} had stopped the clouds' drift, the shadows kept drifting
 * west on their own. This makes the same two changes they do: the 0.03 goes to 0, and the
 * origin moves by the offset {@link CloudWind} has carried the clouds, which is exactly
 * where shifting the camera back by it puts the cloud texture. The coverage texture the
 * mod samples from scrolls with its origin, in either direction on either axis, so
 * nothing else needs to change.
 * <p>
 * Only its vanilla path is touched. With Better Clouds, Cloud Layers, Cirrus or Cloud
 * Tweaks the mod follows those mods' own clouds instead, which AeroWeather doesn't move.
 * <p>
 * Names Better Cloud Shadows only by string, like the other optional-mod mixins, and is
 * withheld by {@code AeroWeatherMixinPlugin} unless it's installed. Verified against
 * {@code better_cloud_shadows-neoforge-1.21.1-1.4.0}: the constant and the call each
 * appear once in the method. {@code require = 0}: its internals aren't API, and a change
 * there should cost us following shadows, not the game.
 */
@Mixin(targets = "com.evandev.better_cloud_shadows.clouds.VanillaClouds", remap = false)
public class BetterCloudShadowsMixin {
    private static final String SINGLE_LAYER = "Lcom/evandev/better_cloud_shadows/clouds/CloudField;"
            + "single(DDFFFFFJLcom/evandev/better_cloud_shadows/clouds/CloudField$Coverage;)"
            + "Lcom/evandev/better_cloud_shadows/clouds/CloudField;";

    @ModifyConstant(method = "field", constant = @Constant(floatValue = 0.03F), require = 0)
    private static float aeroweather$stopShadowDrift(float drift) {
        return CloudWind.active() ? 0.0F : drift;
    }

    @ModifyArg(method = "field", at = @At(value = "INVOKE", target = SINGLE_LAYER), index = 0, require = 0)
    private static double aeroweather$carryShadowsX(double originX) {
        return originX + CloudWind.carriedX();
    }

    @ModifyArg(method = "field", at = @At(value = "INVOKE", target = SINGLE_LAYER), index = 1, require = 0)
    private static double aeroweather$carryShadowsZ(double originZ) {
        return originZ + CloudWind.carriedZ();
    }
}
