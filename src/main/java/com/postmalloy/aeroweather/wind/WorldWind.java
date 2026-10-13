package com.postmalloy.aeroweather.wind;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The wind at a point in the world, in world space: the dimension's base wind
 * bent and scaled by the flow map and biome ({@link WindField}), scaled by
 * altitude ({@link WindHeightScaling}), and blended into the upper layer above
 * cloud level ({@link UpperWindLayer}). Every reader that wants wind somewhere in
 * particular comes through here, so all of them agree; {@link LocalWind} then
 * re-expresses the result in a block's own frame.
 * <p>
 * Below the upper layer's transition band this is exactly the flow map and height
 * scaling, unchanged, and costs nothing more than they do.
 *
 * <h2>Blending the layers</h2>
 * Across the transition band each layer's wind is taken as a vector — its
 * direction times its height-scaled strength — and the two vectors are
 * interpolated. That's what makes {@link UpperWindMode#SIMPLE} work: the layer
 * above is the one below reversed, and a vector blended into its own reverse
 * passes through exactly zero halfway, so the wind dies away to nothing where the
 * layers meet and picks back up the other way, with the strength on either side
 * still the height curve's. In {@link UpperWindMode#COMPLEX} the same blend
 * cross-fades the two flow maps, with a dip in strength only as deep as the two
 * directions disagree, which is how wind shear between real air masses behaves.
 */
public final class WorldWind {
    private WorldWind() {
    }

    /** The bearing wind blows FROM, and its strength there with altitude already applied. */
    public record Sample(float directionDeg, float strength) {
    }

    /**
     * @param baseDirectionDeg the dimension's base FROM bearing ({@link WindState} or the client's synced copy)
     * @param baseStrength     the dimension's base strength, before any position or altitude adjustment
     */
    public static Sample at(Level level, double x, double y, double z, float baseDirectionDeg, float baseStrength) {
        WindField.Sample field = WindField.at(level, x, z);
        float lowerDirection = WindDirection.normalizeDegrees(baseDirectionDeg + field.directionOffsetDeg());
        float lowerStrength = heightScaled(level, baseStrength * field.strengthFactor(), y);

        float blend = UpperWindLayer.blendAt(level, y);
        if (blend <= 0.0f) {
            return new Sample(lowerDirection, lowerStrength);
        }

        float upperDirection;
        float upperStrength;
        if (UpperWindLayer.mode() == UpperWindMode.SIMPLE) {
            upperDirection = WindDirection.normalizeDegrees(lowerDirection + 180.0f);
            upperStrength = lowerStrength;
        } else {
            UpperWindLayer.Flow upper = UpperWindLayer.complexAt(level, x, z, baseDirectionDeg);
            upperDirection = upper.directionDeg();
            upperStrength = heightScaled(level, baseStrength * upper.strengthFactor(), y);
        }
        return blend(lowerDirection, lowerStrength, upperDirection, upperStrength, blend);
    }

    /** {@link WindHeightScaling} with the configured curve. */
    public static float heightScaled(Level level, float strength, double y) {
        return WindHeightScaling.scale(strength, y, level.getSeaLevel(),
                AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_EXPONENT.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_MAX_MULTIPLIER.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_SEA_LEVEL_MULTIPLIER.getAsDouble());
    }

    /** The two layers as vectors, interpolated by {@code blend} (0 is all lower, 1 all upper). */
    static Sample blend(float lowerDirection, float lowerStrength, float upperDirection, float upperStrength,
            float blend) {
        if (blend >= 1.0f) {
            return new Sample(upperDirection, upperStrength);
        }
        Vec3 lower = WindDirection.travelVector(lowerDirection).scale(lowerStrength);
        Vec3 upper = WindDirection.travelVector(upperDirection).scale(upperStrength);
        double travelX = lower.x + (upper.x - lower.x) * blend;
        double travelZ = lower.z + (upper.z - lower.z) * blend;
        double strength = Math.sqrt(travelX * travelX + travelZ * travelZ);
        if (strength < 1.0E-4) {
            // Dead calm where the layers cancel. Any bearing would do; keep whichever side's
            // is nearer, so a vane at the meeting point doesn't spin to an arbitrary heading.
            return new Sample(blend < 0.5f ? lowerDirection : upperDirection, 0.0f);
        }
        return new Sample(WindDirection.bearingOf(new Vec3(-travelX, 0.0, -travelZ)), (float) strength);
    }
}
