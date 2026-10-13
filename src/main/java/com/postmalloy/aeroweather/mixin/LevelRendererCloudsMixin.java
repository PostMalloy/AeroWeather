package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.postmalloy.aeroweather.client.CloudWind;

import net.minecraft.client.renderer.LevelRenderer;

/**
 * Vanilla's clouds follow the wind (M18). See {@link CloudWind} for the offset.
 * <p>
 * {@code renderClouds} turns the camera position into a cloud texture lookup:
 * {@code (cameraX + (ticks + partialTick) * 0.03) / 12} and {@code cameraZ / 12 + 0.33}.
 * The 0.03 is vanilla's whole drift, so it goes to 0, and the camera X/Z are shifted
 * back by the wind's offset instead.
 * <p>
 * The shift is applied at the {@code getCloudHeight()} call rather than at the head
 * of the method, deliberately: the camera position is first handed to NeoForge's
 * {@code DimensionSpecialEffects.renderClouds} hook, which lets a modded dimension draw
 * its own clouds, and that should see where the camera really is.
 * <p>
 * With Sodium installed none of this runs: Sodium cancels the method at its head and
 * draws clouds itself, which {@link SodiumCloudsMixin} covers.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public class LevelRendererCloudsMixin {
    @ModifyConstant(method = "renderClouds", constant = @Constant(floatValue = 0.03F))
    private float aeroweather$stopVanillaDrift(float drift) {
        return CloudWind.active() ? 0.0F : drift;
    }

    @ModifyVariable(method = "renderClouds", argsOnly = true, ordinal = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private double aeroweather$carryCloudsX(double cameraX) {
        return CloudWind.shiftX(cameraX);
    }

    @ModifyVariable(method = "renderClouds", argsOnly = true, ordinal = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private double aeroweather$carryCloudsZ(double cameraZ) {
        return CloudWind.shiftZ(cameraZ);
    }
}
