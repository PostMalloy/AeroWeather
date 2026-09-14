package com.postmalloy.aeroweather.block;

import com.mojang.serialization.MapCodec;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.registry.AeroWeatherBlockEntityTypes;
import com.postmalloy.aeroweather.wind.LocalWind;
import com.postmalloy.aeroweather.wind.WindDirection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The brass wind vane: a {@link WindVaneBlock} that also emits a redstone
 * signal from the side or sides facing the wind.
 * <p>
 * The signal mirrors vanilla's daylight detector: weak power only, the value
 * held in the blockstate ({@link #POWER}), re-evaluated every 20 ticks by a
 * server ticker and written back only when it changes - which also makes
 * observers pulse on every change. {@link #WIND_FROM} records which of 8
 * compass sectors the wind comes from: a cardinal wind lights one face, a
 * diagonal one the two faces either side of it.
 * <p>
 * Both come from {@link LocalWind}, i.e. in the block's own frame. So on a Sable
 * ship the lit faces are the ship's upwind faces, and strength follows the
 * ship's real altitude.
 * <p>
 * This is a subclass rather than a flag on {@link WindVaneBlock} because
 * {@code Block}'s constructor builds the state definition before any subclass
 * field is assigned, so a flag couldn't decide which properties exist.
 */
public class BrassWindVaneBlock extends WindVaneBlock {
    public static final MapCodec<BrassWindVaneBlock> CODEC = simpleCodec(BrassWindVaneBlock::new);

    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final EnumProperty<WindDirection> WIND_FROM = EnumProperty.create("wind_from", WindDirection.class);

    private static final long UPDATE_INTERVAL_TICKS = 20L;

    public BrassWindVaneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0).setValue(WIND_FROM, WindDirection.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, WIND_FROM);
    }

    /** Keeps the zinc vane's client ticker, and adds the server one that drives the signal. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return super.getTicker(level, state, type);
        }
        return createTickerHelper(type, AeroWeatherBlockEntityTypes.WIND_VANE.get(), BrassWindVaneBlock::serverTick);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, WindVaneBlockEntity vane) {
        if (level.getGameTime() % UPDATE_INTERVAL_TICKS != 0L) {
            return;
        }
        LocalWind.Sample wind = LocalWind.at(level, pos);
        if (wind == null) {
            return;
        }
        double fullSignalStrength = AeroWeatherCommonConfig.WIND_VANE_FULL_SIGNAL_STRENGTH.getAsDouble();
        int power = Mth.clamp(Math.round((float) (wind.adjustedStrength() / fullSignalStrength * 15.0)), 0, 15);
        // In calm air keep the last reading, so the blockstate (and any observer watching it)
        // doesn't churn as a strength-0 wind's direction drifts around.
        WindDirection windFrom = power == 0 ? state.getValue(WIND_FROM) : WindDirection.nearest(wind.directionDeg());
        BlockState updated = state.setValue(POWER, power).setValue(WIND_FROM, windFrom);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * {@code direction} points from the neighbour asking toward this block
     * ({@code SignalGetter.hasNeighborSignal} asks its <em>north</em> neighbour with
     * {@code Direction.NORTH}), so the face actually touching that neighbour is the
     * opposite one.
     */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        Direction face = direction.getOpposite();
        return emitsFrom(state.getValue(WIND_FROM), face) ? state.getValue(POWER) : 0;
    }

    /** True for the one face nearest a cardinal wind, or the two either side of a diagonal one. */
    private static boolean emitsFrom(WindDirection windFrom, Direction face) {
        float faceBearing = switch (face) {
            case NORTH -> 0.0f;
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> Float.NaN;
        };
        return !Float.isNaN(faceBearing) && WindDirection.angularDifference(windFrom.degrees(), faceBearing) <= 45.0f;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        float turn = switch (rotation) {
            case NONE -> 0.0f;
            case CLOCKWISE_90 -> 90.0f;
            case CLOCKWISE_180 -> 180.0f;
            case COUNTERCLOCKWISE_90 -> 270.0f;
        };
        return state.setValue(WIND_FROM, WindDirection.nearest(state.getValue(WIND_FROM).degrees() + turn));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        float bearing = state.getValue(WIND_FROM).degrees();
        float mirrored = switch (mirror) {
            case NONE -> bearing;
            case LEFT_RIGHT -> 180.0f - bearing; // swaps north and south
            case FRONT_BACK -> -bearing;         // swaps east and west
        };
        return state.setValue(WIND_FROM, WindDirection.nearest(mirrored));
    }
}
