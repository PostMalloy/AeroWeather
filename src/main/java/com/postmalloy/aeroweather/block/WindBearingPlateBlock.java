package com.postmalloy.aeroweather.block;

import com.postmalloy.aeroweather.integration.simulated.WindBearingRegistration;

import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The top piece a wind bearing leaves on its contraption when it assembles:
 * Simulated's swivel bearing plate in every respect but its model, which carries
 * an arrow. Because the plate rides the contraption, the arrow always shows which
 * way the top is facing, i.e. where the bearing has swivelled it.
 * <p>
 * Only exists because Simulated places its own plate block, by a hardcoded
 * reference, and a model can't differ per parent. {@code WindBearingPlateMixin}
 * makes a wind bearing place this one instead and recognise it afterwards.
 * <p>
 * Same classloading rule as {@link WindBearingBlock}: never touched without the
 * {@code WindBearingIntegration} gate.
 */
public class WindBearingPlateBlock extends SwivelBearingPlateBlock {
    public WindBearingPlateBlock(Properties properties) {
        super(properties);
    }

    /** Its own type, since a block entity type only accepts the blocks it was built with. */
    @Override
    public BlockEntityType<? extends SwivelBearingPlateBlockEntity> getBlockEntityType() {
        return WindBearingRegistration.WIND_BEARING_PLATE_BLOCK_ENTITY.get();
    }

    /** Pick-block gives a wind bearing, as Simulated's plate gives a swivel bearing. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(WindBearingRegistration.WIND_BEARING_ITEM.get());
    }
}
