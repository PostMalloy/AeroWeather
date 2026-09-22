package com.postmalloy.aeroweather.block;

import java.util.List;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.mixin.ScrollValueSlotAccessor;
import com.postmalloy.aeroweather.mixin.SwivelBearingInternals;
import com.postmalloy.aeroweather.integration.simulated.WindBearingRegistration;
import com.postmalloy.aeroweather.wind.LocalWind;
import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wind bearing's behaviour: the one thing it changes about a swivel bearing.
 * <p>
 * A swivel bearing integrates the speed of the cogwheel on its side into a target
 * angle. This instead drives that same target angle toward the wind, at a rate
 * set by the rotation on its <em>own</em> axis — the top and bottom faces. So it
 * only turns while it's receiving stress, and turns faster the faster the network
 * runs; with no power the servo simply holds the contraption where it is.
 * Everything else — assembly, the rotary constraint, persistence, locking and
 * stress pass-through into the contraption — is inherited untouched.
 * <p>
 * Only the absolute target is ours: {@code super.tick()} still runs first and is
 * left to do its own work. Its own integration step contributes nothing, because
 * the cogwheel node can't connect to anything (see
 * {@link WindBearingBlock#getExtraKineticsRotationConfiguration()}).
 */
public class WindBearingBlockEntity extends SwivelBearingBlockEntity {
    public WindBearingBlockEntity(BlockPos pos, BlockState state) {
        super(WindBearingRegistration.WIND_BEARING_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Moves the inherited redstone-locking menu up the side of the block, from where the
     * swivel bearing puts it to where every Create bearing puts it.
     * <p>
     * Both start from {@code CenteredSideValueBoxTransform} and then push the box back along
     * the facing axis, but by different amounts: Simulated's own by 5px ({@code 0.3125}),
     * Create's {@link DirectionalExtenderScrollOptionSlot} by 2px ({@code -0.125}) — a 3px
     * difference, and the wind bearing inherited the wrong one. Reusing Create's slot rather
     * than copying an offset keeps it identical to the windmill, clockwork and mechanical
     * bearings by construction.
     * <p>
     * The transform has to be swapped on the behaviour {@code super} already built, not passed
     * to a replacement: the swivel bearing keeps its own reference to that behaviour and reads
     * the locking mode back through it, so a substitute would be ignored.
     */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        for (BlockEntityBehaviour behaviour : behaviours) {
            if (behaviour instanceof ScrollOptionBehaviour<?> scrollOption) {
                ((ScrollValueSlotAccessor) scrollOption).aeroweather$setSlotPositioning(
                        new DirectionalExtenderScrollOptionSlot((state, side) ->
                                side.getAxis() != state.getValue(DirectionalKineticBlock.FACING).getAxis()));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (!AeroWeatherCommonConfig.WIND_BEARING_ENABLED.get() || !this.isAssembled()) {
            return;
        }

        // "Into the wind" only means something about a vertical axis; mounted on its side the
        // block just behaves like a swivel bearing. Same reasoning as the M9 windmill rule.
        Direction facing = this.getBlockState().getValue(DirectionalKineticBlock.FACING);
        if (facing.getAxis() != Direction.Axis.Y) {
            return;
        }

        // Stress is what makes it move, and only from the top or bottom: this is the block's
        // own kinetic node, which for a vertical bearing is exactly those two faces.
        float speed = this.getSpeed();
        if (speed == 0.0f) {
            return;
        }

        LocalWind.Sample wind = LocalWind.at(this.level, this.getBlockPos());
        if (wind == null) {
            return;
        }

        // The servo angle runs counter-clockwise seen from above, against the compass (confirmed
        // live: a north wind lined up, a northeast one landed on northwest). A build whose front
        // sits at bearing `offset` when the angle is 0 therefore faces `offset - angle` once
        // turned, so facing the wind needs angle = offset - wind.
        double target = AeroWeatherCommonConfig.WIND_BEARING_FACING_OFFSET_DEGREES.getAsDouble() - wind.directionDeg();
        if (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            // Simulated takes the servo's zero from Direction.getRotation() on the plate's
            // facing, and DOWN's is a 180 degree turn about X. That single quaternion does two
            // things: it reverses the Y axis, so the angle runs backwards, and it carries north
            // to south, so zero itself is half a turn out. Hence negate *and* add 180 -- the
            // negation alone left an upside-down bearing pointing exactly downwind.
            target = 180.0 - target;
        }

        double current = this.getTargetAngleDegrees();
        double maxStep = Math.abs(convertToAngular(speed));
        double step = Mth.clamp(Mth.wrapDegrees(target - current), -maxStep, maxStep);
        if (step == 0.0) {
            return;
        }

        ((SwivelBearingInternals) this).aeroweather$setTargetAngleDegrees((current + step) % 360.0);
        wakePhysics();
    }

    /**
     * A contraption at rest goes to sleep, and a sleeping body won't notice the servo's new
     * target. The swivel bearing wakes it whenever its own angle moves; ours has to do the
     * same, since the rotation that moves us isn't the one it's watching.
     */
    private void wakePhysics() {
        SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (!(container instanceof ServerSubLevelContainer serverContainer)) {
            return;
        }

        SwivelBearingInternals internals = (SwivelBearingInternals) this;
        wake(serverContainer, internals.aeroweather$getAttachedSubLevel());
        wake(serverContainer, internals.aeroweather$getContainingSubLevel());
    }

    private static void wake(ServerSubLevelContainer container, SubLevel subLevel) {
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            container.physicsSystem().getPipeline().wakeUp(serverSubLevel);
        }
    }
}
