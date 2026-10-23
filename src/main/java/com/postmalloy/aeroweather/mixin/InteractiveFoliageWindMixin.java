package com.postmalloy.aeroweather.mixin;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.postmalloy.aeroweather.integration.interactivefoliage.FoliageWind;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

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

    // ---- IF 2.2+ only; on earlier versions none of these targets exist and nothing is injected.

    /** Each Sable sub-level is drawn through this one call, inside IF's own subLevelPass flag. */
    @WrapOperation(method = "drawSubLevels", at = @At(value = "INVOKE",
            target = "Lnet/karto/mc2/mc2_interactivefoliage/gpu/GpuFoliageRenderer;drawLegacy(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/phys/Vec3;Ljava/util/List;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            require = 0)
    private static void aeroweather$markSubLevelPass(Minecraft minecraft, Vec3 camera, List<?> drawn,
            Matrix4f modelView, Matrix4f projection, Operation<Void> original) {
        FoliageWind.setDrawingSubLevel(true);
        try {
            original.call(minecraft, camera, drawn, modelView, projection);
        } finally {
            FoliageWind.setDrawingSubLevel(false);
        }
    }

    /** The only {@code Vector3f(float, float, float)} built here: the world's west, before it's turned into a ship's plot. */
    @ModifyExpressionValue(method = "drawLegacyRuns", at = @At(value = "NEW", target = "(FFF)Lorg/joml/Vector3f;"),
            require = 0)
    private static Vector3f aeroweather$blowSubLevelsWithOurWind(Vector3f west) {
        return FoliageWind.subLevelWind(west);
    }

    /** VulkanMod's pipeline compiles an unpatched sway.glsl, so it must never see our encoded direction. */
    @ModifyExpressionValue(method = "drawLegacyThroughVulkanMod", at = @At(value = "INVOKE",
            target = "Lnet/karto/mc2/mc2_interactivefoliage/gpu/GpuFoliageRenderer;weather()Lorg/joml/Vector4f;"),
            require = 0)
    private static Vector4f aeroweather$keepVulkanModsDirection(Vector4f weather) {
        return FoliageWind.forUnpatchedShader(weather);
    }
}
