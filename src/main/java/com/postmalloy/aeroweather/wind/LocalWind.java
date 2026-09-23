package com.postmalloy.aeroweather.wind;

import org.jetbrains.annotations.Nullable;

import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.integration.aeronautics.SubLevelFrames;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The wind as seen from one block's own grid. Shared by the wind vane and
 * Create windmills, which both care about direction relative to their own
 * facing or faces, and about strength at their own altitude.
 * <p>
 * Runs on both sides: the server reads the authoritative {@link WindSavedData}
 * (redstone signal, windmill kinetics), and the client its synced
 * {@link ClientWindState} cache (vane animation, windmill sail visuals).
 * <p>
 * For an ordinary world block this is exactly the computation windmills have
 * always used: {@link WindHeightScaling} at the block's own Y, and the raw
 * bearing. For a block riding a Sable sub-level (see {@link SubLevelFrames}),
 * three things change, because such a block sits at far-away plot coordinates
 * inside a frame the sub-level can rotate freely:
 * <ul>
 *   <li>strength is height-scaled at the block's <em>world</em> Y, not its plot Y;</li>
 *   <li>the bearing is re-expressed in the sub-level's own frame, so it compares
 *       correctly against the block's own facing and neighbours;</li>
 *   <li>strength is scaled by how much of the wind lies in the sub-level's
 *       horizontal plane. That's full strength on a level ship, fading to 0 as it
 *       pitches on end, where a horizontal bearing stops meaning anything.</li>
 * </ul>
 * Deliberately true wind only: the sub-level's own motion is ignored, matching
 * how the contraption force and windmills treat wind.
 */
public final class LocalWind {
    private LocalWind() {
    }

    /** The bearing wind blows FROM (in the block's own frame), and its elevation-adjusted strength. */
    public record Sample(float directionDeg, float adjustedStrength) {
    }

    /**
     * The wind at {@code pos} as seen from that block's own grid, or
     * {@code null} on the client before wind has synced for this dimension.
     */
    public static @Nullable Sample at(Level level, BlockPos pos) {
        float directionDeg;
        float baseStrength;
        if (level instanceof ServerLevel serverLevel) {
            WindState wind = WindSavedData.get(serverLevel).wind();
            directionDeg = wind.directionDeg();
            baseStrength = wind.strength();
        } else {
            ResourceLocation syncedDimension = ClientWindState.dimension();
            if (syncedDimension == null || !syncedDimension.equals(level.dimension().location())) {
                return null;
            }
            directionDeg = ClientWindState.directionDeg();
            baseStrength = ClientWindState.strength();
        }

        SubLevelFrames.LocalFrame frame = SubLevelFrames.get().frameAt(level, pos);

        // Where this block actually stands, which is not its BlockPos on a ship: a
        // sub-level's blocks live at far-away plot coordinates whose biome means
        // nothing. Sample the field where the ship is, not where its storage is.
        Vec3 fieldPosition = frame == null ? Vec3.atCenterOf(pos) : frame.worldPosition();
        WindField.Sample field = WindField.at(level, fieldPosition.x, fieldPosition.z);
        directionDeg = WindDirection.normalizeDegrees(directionDeg + field.directionOffsetDeg());
        baseStrength *= field.strengthFactor();

        if (frame == null) {
            return new Sample(directionDeg, heightScaled(level, baseStrength, pos.getY()));
        }

        float adjustedStrength = heightScaled(level, baseStrength, frame.worldPosition().y);
        Vec3 localFrom = frame.worldToLocalDirection().apply(WindDirection.travelVector(directionDeg).reverse());
        double length = localFrom.length();
        double horizontalShare = length < 1.0E-9
                ? 0.0
                : Math.sqrt(localFrom.x * localFrom.x + localFrom.z * localFrom.z) / length;
        return new Sample(WindDirection.bearingOf(localFrom), (float) (adjustedStrength * horizontalShare));
    }

    private static float heightScaled(Level level, float baseStrength, double y) {
        return WindHeightScaling.scale(baseStrength, y, level.getSeaLevel(),
                AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_EXPONENT.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_MAX_MULTIPLIER.getAsDouble());
    }
}
