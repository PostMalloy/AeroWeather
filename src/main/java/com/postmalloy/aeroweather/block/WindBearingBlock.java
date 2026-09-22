package com.postmalloy.aeroweather.block;

import com.postmalloy.aeroweather.integration.simulated.WindBearingRegistration;
import com.simibubi.create.content.kinetics.base.IRotate;

import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A swivel bearing that turns its contraption into the wind instead of wherever
 * rotational input points it — see {@link WindBearingBlockEntity} for the part
 * that differs. Assembly, the servo constraint and stress pass-through between
 * the base and the contraption are all inherited unchanged.
 * <p>
 * References Create, Create Simulated and Sable types directly, so it must never
 * be classloaded unless all three are installed —
 * {@code WindBearingIntegration} is the gate that guarantees that, and nothing
 * outside {@code integration/simulated/} may mention this class.
 */
public class WindBearingBlock extends SwivelBearingBlock {
    /**
     * Connects to nothing: not a cogwheel, and no shaft on any face. Create's
     * rotation propagator only links a node through a shaft or a cog, so handing
     * this back as the extra-kinetics configuration leaves the inherited cogwheel
     * node with no possible connection — the wind bearing takes power from its
     * own axis (top and bottom) only, unlike the swivel bearing which is driven
     * by a cog on its side.
     * <p>
     * The node itself is left in place rather than removed: the inherited
     * {@code tick()} calls it directly, so it still needs the lifecycle handling
     * that Simulated's ExtraKinetics mixins give it.
     */
    private static final IRotate NO_EXTRA_KINETICS = new IRotate() {
        @Override
        public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
            return false;
        }

        @Override
        public Direction.Axis getRotationAxis(BlockState state) {
            return Direction.Axis.Y;
        }
    };

    public WindBearingBlock(Properties properties) {
        super(properties);
    }

    /**
     * Only the type is overridden, not {@code getBlockEntityClass()}: {@code IBE}
     * declares that as an invariant {@code Class<T>}, and the inherited
     * {@code SwivelBearingBlockEntity.class} already describes ours correctly.
     */
    @Override
    public BlockEntityType<? extends SwivelBearingBlockEntity> getBlockEntityType() {
        return WindBearingRegistration.WIND_BEARING_BLOCK_ENTITY.get();
    }

    @Override
    public IRotate getExtraKineticsRotationConfiguration() {
        return NO_EXTRA_KINETICS;
    }
}
