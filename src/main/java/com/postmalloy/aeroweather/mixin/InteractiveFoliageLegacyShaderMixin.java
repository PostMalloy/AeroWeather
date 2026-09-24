package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.postmalloy.aeroweather.integration.interactivefoliage.FoliageWind;

import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * Rewrites Interactive Foliage's {@code foliage_legacy.vsh}, the shader it falls back
 * to when Sodium draws the chunks (M16).
 * <p>
 * Unlike {@code sway.glsl} this one is an ordinary core shader: IF registers it with
 * {@code new ShaderInstance(event.getResourceProvider(), ...)}, and vanilla reads its
 * source through that provider. So the provider is swapped here, for one that hands
 * back this single file patched and passes everything else through. That keeps the
 * change inside IF's own registration — vanilla's shader loading is untouched for
 * every other mod and every vanilla shader.
 * <p>
 * String targets only, withheld without IF, {@code require = 0} — see
 * {@link InteractiveFoliageWindMixin}.
 */
@Mixin(targets = "net.karto.mc2.mc2_interactivefoliage.platform.neoforge.NeoforgeFoliageHooks", remap = false)
public class InteractiveFoliageLegacyShaderMixin {
    @ModifyExpressionValue(method = "registerShaders", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/event/RegisterShadersEvent;getResourceProvider()Lnet/minecraft/server/packs/resources/ResourceProvider;"),
            require = 0)
    private static ResourceProvider aeroweather$patchLegacyShader(ResourceProvider original) {
        return FoliageWind.wrapLegacyProvider(original);
    }
}
