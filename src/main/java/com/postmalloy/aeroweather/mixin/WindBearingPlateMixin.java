package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.postmalloy.aeroweather.block.WindBearingBlockEntity;
import com.postmalloy.aeroweather.block.WindBearingPlateBlock;
import com.postmalloy.aeroweather.integration.simulated.WindBearingRegistration;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes a wind bearing use its own plate ({@link WindBearingPlateBlock}) as the
 * top piece it leaves on the contraption, rather than Simulated's.
 * <p>
 * Simulated names its plate by a hardcoded {@code SimBlocks.SWIVEL_BEARING_LINK_BLOCK}
 * in two ways: once in {@code assemble()} to place it, and in six methods as a
 * {@code state.is(SWIVEL_BEARING_LINK_BLOCK)} check that the plate is still there.
 * Both are covered: placement is swapped for wind bearings only, and every check
 * also accepts our plate — failing one would make the bearing think its plate had
 * been removed. Swivel bearings are untouched: they never place our plate, so the
 * extra acceptance never fires for them.
 * <p>
 * String target, withheld by {@code AeroWeatherMixinPlugin} when Simulated is
 * absent. The names below are Simulated's private internals; a rename breaks this
 * at class-transform time, the same accepted cost as {@link SwivelBearingInternals}.
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity", remap = false)
public abstract class WindBearingPlateMixin {
    @ModifyExpressionValue(method = "assemble",
            at = @At(value = "INVOKE",
                    target = "Lcom/tterrag/registrate/util/entry/BlockEntry;getDefaultState()Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState aeroweather$placeWindBearingPlate(BlockState original) {
        if ((Object) this instanceof WindBearingBlockEntity) {
            return WindBearingRegistration.WIND_BEARING_PLATE.get().defaultBlockState();
        }
        return original;
    }

    @WrapOperation(method = {"tick", "checkPersistence", "reattachConstraint", "associatePlateWithParent",
            "attachConstraints", "destroyPlate"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/core/Holder;)Z"))
    private boolean aeroweather$recogniseWindBearingPlate(BlockState state, Holder<Block> block,
            Operation<Boolean> original) {
        return original.call(state, block) || state.getBlock() instanceof WindBearingPlateBlock;
    }
}
