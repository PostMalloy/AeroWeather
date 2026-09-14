package com.postmalloy.aeroweather.block;

import com.mojang.serialization.MapCodec;
import com.postmalloy.aeroweather.registry.AeroWeatherBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The zinc wind vane: a static base with a vane that swings to point into the
 * wind, drawn by GeckoLib (see {@code WindVaneRenderer}). It's purely an
 * indicator - {@link BrassWindVaneBlock} builds on this class to also emit a
 * redstone signal.
 * <p>
 * The swinging is entirely client-side ({@link WindVaneBlockEntity}), so this
 * block has no blockstate properties and no server ticker.
 */
public class WindVaneBlock extends BaseEntityBlock {
    public static final MapCodec<WindVaneBlock> CODEC = simpleCodec(WindVaneBlock::new);

    /** Collision is the model's 3px base slab; the thin rotating vane doesn't block movement. */
    private static final VoxelShape COLLISION_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);
    /** Selection covers the vane's whole sweep, so aiming at the vane itself picks the block. */
    private static final VoxelShape OUTLINE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public WindVaneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindVaneBlockEntity(pos, state);
    }

    /** Client only: easing the vane's heading. The brass vane adds a server ticker for its signal. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return createTickerHelper(type, AeroWeatherBlockEntityTypes.WIND_VANE.get(), WindVaneBlockEntity::clientTick);
        }
        return null;
    }
}
