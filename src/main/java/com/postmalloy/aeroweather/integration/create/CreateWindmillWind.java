package com.postmalloy.aeroweather.integration.create;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.wind.LocalWind;
import com.postmalloy.aeroweather.wind.WindmillWindResponse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The wind lookup behind {@code WindmillBearingBlockEntityMixin} — everything
 * about the windmill feature that can be written in plain Java, kept out of the
 * mixin so the mixin itself stays a thin pair of injections.
 * <p>
 * Despite living under {@code integration/}, this class references <em>no</em>
 * Create types (only vanilla + AeroWeather), so unlike
 * {@code AeronauticsWindForceApplier} it is always safe to classload. The
 * Create-presence gate for this feature happens earlier and more strongly, in
 * {@code AeroWeatherMixinPlugin}: with Create absent the mixin is never
 * registered, so nothing here is ever called.
 * <p>
 * Runs on both sides, because Create's
 * {@code MechanicalBearingBlockEntity.getAngularSpeed()} feeds a windmill's
 * <em>visual</em> rotation from {@code getGeneratedSpeed()} too — so the client
 * has to reach the same multiplier as the server or the sails would spin at a
 * different rate than the kinetic network they drive. {@link LocalWind} handles
 * that split (authoritative server wind vs the client's synced copy), and also
 * makes a windmill riding a Sable ship read the ship's real altitude and a
 * bearing in the ship's own frame — the same frame the bearing's {@code FACING}
 * lives in. For a windmill in an ordinary world the result is unchanged.
 */
public final class CreateWindmillWind {
    private CreateWindmillWind() {
    }

    /**
     * The multiplier to apply to a windmill's normal Create speed, or 1.0
     * (no change) when the feature is off, the wind hasn't synced for this
     * dimension yet, or the block isn't something with a FACING to read.
     */
    public static float speedMultiplier(Level level, BlockPos pos, BlockState state) {
        if (!AeroWeatherCommonConfig.WINDMILLS_ENABLED.get()) {
            return 1.0f;
        }
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return 1.0f;
        }
        LocalWind.Sample wind = LocalWind.at(level, pos);
        if (wind == null) {
            return 1.0f;
        }
        Direction facing = state.getValue(BlockStateProperties.FACING);

        return WindmillWindResponse.multiplier(facing, wind.directionDeg(), wind.adjustedStrength(),
                AeroWeatherCommonConfig.WINDMILL_FULL_SPEED_STRENGTH.getAsDouble(),
                AeroWeatherCommonConfig.WINDMILL_MAX_SPEED_MULTIPLIER.getAsDouble(),
                AeroWeatherCommonConfig.WINDMILL_MIN_DIRECTIONAL_SCALE.getAsDouble(),
                AeroWeatherCommonConfig.WINDMILL_REVERSE_WHEN_BEHIND.get());
    }
}
