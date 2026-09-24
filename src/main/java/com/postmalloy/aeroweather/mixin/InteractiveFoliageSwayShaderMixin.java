package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.postmalloy.aeroweather.integration.interactivefoliage.FoliageWind;

/**
 * Rewrites Interactive Foliage's {@code sway.glsl} so grass leans the way our wind
 * blows (M16). Both classes read the file themselves, straight from IF's jar, and
 * splice it into a program: {@code LegacyTerrainShader} for vanilla terrain,
 * {@code IrisFoliageShaders} for shaderpacks. Same method name and descriptor in
 * each, so one mixin covers both.
 * <p>
 * {@code IrisFoliageShaders} is only ever loaded by IF when Iris is present. Naming it
 * here doesn't change that: a mixin applies when its target loads, and never makes
 * it load.
 * <p>
 * String targets only, withheld without IF, {@code require = 0} — see
 * {@link InteractiveFoliageWindMixin}.
 */
@Mixin(targets = {
        "net.karto.mc2.mc2_interactivefoliage.gpu.LegacyTerrainShader",
        "net.karto.mc2.mc2_interactivefoliage.gpu.IrisFoliageShaders"
}, remap = false)
public class InteractiveFoliageSwayShaderMixin {
    @ModifyReturnValue(method = "readSway", at = @At("RETURN"), require = 0)
    private static String aeroweather$takeDirectionFromWeather(String source) {
        return FoliageWind.patchSway(source);
    }
}
