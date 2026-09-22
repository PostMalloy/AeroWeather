package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.postmalloy.aeroweather.block.WindBearingBlock;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Breaking a swivel bearing's plate breaks the bearing too: the plate's
 * {@code destroyBearing()} destroys its parent, but only after checking
 * {@code state.is(SimBlocks.SWIVEL_BEARING)}. That check also has to accept a wind
 * bearing, or breaking a wind bearing's plate would leave the bearing standing and
 * convinced it's still assembled.
 * <p>
 * String target, withheld by {@code AeroWeatherMixinPlugin} when Simulated is absent.
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity",
        remap = false)
public abstract class WindBearingPlateParentMixin {
    @WrapOperation(method = "destroyBearing",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/core/Holder;)Z"))
    private boolean aeroweather$recogniseWindBearing(BlockState state, Holder<Block> block,
            Operation<Boolean> original) {
        return original.call(state, block) || state.getBlock() instanceof WindBearingBlock;
    }
}
