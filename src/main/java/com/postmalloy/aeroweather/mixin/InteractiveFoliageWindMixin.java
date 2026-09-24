package com.postmalloy.aeroweather.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.postmalloy.aeroweather.integration.interactivefoliage.FoliageWind;

/**
 * Feeds AeroWeather's wind to Interactive Foliage's grass (M16).
 * <p>
 * {@code weather()} builds the one {@code Weather} vector every IF render pipeline
 * uploads — vanilla terrain, the Sodium path and Iris shaderpacks alike — so this
 * single hook drives all three. {@code calmSway()} likewise feeds every pipeline the
 * scale of IF's idle sway, which is what lets calm air hold the grass still.
 * {@code followRain} decides whether sections are meshed with IF's wind shelter; see
 * {@link FoliageWind#shelterRainLevel} for why it's switched off while our direction
 * is in use.
 * <p>
 * Names IF only by string, never by type, like the Create windmill and Particle Rain
 * mixins: {@code weather()} is {@code ()Lorg/joml/Vector4f;}, JOML only. Withheld by
 * {@code AeroWeatherMixinPlugin} unless IF is installed, so AeroWeather never depends
 * on it. {@code require = 0} throughout: these are IF's private internals, and a
 * future rename should cost us the feature, not the game.
 */
@Mixin(targets = "net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer", remap = false)
public class InteractiveFoliageWindMixin {
    @ModifyReturnValue(method = "weather", at = @At("RETURN"), require = 0)
    private static Vector4f aeroweather$driveWithSimulatedWind(Vector4f original) {
        return FoliageWind.adjustWeather(original);
    }

    /** Called three times per frame, once per pipeline's uniforms, so this reaches all of them. */
    @ModifyReturnValue(method = "calmSway", at = @At("RETURN"), require = 0)
    private static float aeroweather$stillInCalmAir(float original) {
        return FoliageWind.adjustCalmSway(original);
    }

    @ModifyExpressionValue(method = "followRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"), require = 0)
    private static float aeroweather$suppressEastOnlyShelter(float rainLevel) {
        return FoliageWind.shelterRainLevel(rainLevel);
    }
}
