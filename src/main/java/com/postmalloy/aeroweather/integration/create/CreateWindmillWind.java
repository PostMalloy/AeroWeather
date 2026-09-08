package com.postmalloy.aeroweather.integration.create;

import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.wind.WindHeightScaling;
import com.postmalloy.aeroweather.wind.WindSavedData;
import com.postmalloy.aeroweather.wind.WindState;
import com.postmalloy.aeroweather.wind.WindmillWindResponse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * different rate than the kinetic network they drive. Server reads the
 * authoritative {@link WindSavedData}; client reads its synced
 * {@link ClientWindState} cache.
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
        Direction facing = state.getValue(BlockStateProperties.FACING);

        float directionDeg;
        float baseStrength;
        if (level instanceof ServerLevel serverLevel) {
            WindState wind = WindSavedData.get(serverLevel).wind();
            directionDeg = wind.directionDeg();
            baseStrength = wind.strength();
        } else {
            ResourceLocation syncedDimension = ClientWindState.dimension();
            if (syncedDimension == null || !syncedDimension.equals(level.dimension().location())) {
                return 1.0f;
            }
            directionDeg = ClientWindState.directionDeg();
            baseStrength = ClientWindState.strength();
        }

        float adjustedStrength = WindHeightScaling.scale(baseStrength, pos.getY(), level.getSeaLevel(),
                AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_EXPONENT.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_MAX_MULTIPLIER.getAsDouble());

        return WindmillWindResponse.multiplier(facing, directionDeg, adjustedStrength,
                AeroWeatherCommonConfig.WINDMILL_FULL_SPEED_STRENGTH.getAsDouble(),
                AeroWeatherCommonConfig.WINDMILL_MAX_SPEED_MULTIPLIER.getAsDouble(),
                AeroWeatherCommonConfig.WINDMILL_MIN_DIRECTIONAL_SCALE.getAsDouble(),
                AeroWeatherCommonConfig.WINDMILL_REVERSE_WHEN_BEHIND.get());
    }
}
