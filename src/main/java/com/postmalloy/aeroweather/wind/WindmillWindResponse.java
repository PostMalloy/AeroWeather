package com.postmalloy.aeroweather.wind;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Turns wind into a multiplier on a Create windmill's normal (sail-count
 * derived) rotation speed. Pure math, kept free of config imports for the same
 * reason {@link WindHeightScaling} is — the caller passes the tunables in, so
 * this stays trivially reviewable and reusable.
 * <p>
 * The windmill's <em>front face normal</em> is its block's
 * {@code BlockStateProperties.FACING} (Create's {@code BearingBlock} points
 * FACING from the bearing toward the sails). Two independent factors multiply
 * together:
 * <ul>
 * <li><b>Strength</b> — elevation-adjusted wind strength over
 * {@code fullSpeedStrength}, clamped to {@code maxSpeedMultiplier}. At
 * {@code fullSpeedStrength} the windmill runs at exactly its normal Create
 * speed; above it, it can overspeed up to the cap.</li>
 * <li><b>Direction</b> — 1.0 while the wind is within a 90 degree arc centred
 * on the face normal (i.e. no more than 45 degrees off the axis), then ramping
 * down to {@code minDirectionalScale} as the wind turns to run parallel to the
 * face. Wind arriving at the <em>back</em> of the windmill mirrors the same
 * curve and (optionally) flips the sign so it spins the other way.</li>
 * </ul>
 * Windmills whose facing is vertical (UP/DOWN — a horizontal sail disc) are
 * exempt from the directional factor entirely: horizontal wind is always
 * perpendicular to their axis, so applying the rule would peg every such build
 * at {@code minDirectionalScale} (0 by default) forever.
 */
public final class WindmillWindResponse {
    /**
     * Multipliers are snapped to this step before being used. Create rebuilds
     * the whole kinetic network on every generated-speed change
     * ({@code detachKinetics}/{@code attachKinetics} + a stress recalc + a
     * block entity sync), so the value driving it must be a coarse step
     * function of the continuously drifting wind rather than tracking it
     * exactly. It also keeps the client's and server's independently computed
     * multipliers agreeing despite the sync thresholds, so the visual sail
     * speed matches the mechanical one.
     */
    public static final float QUANTIZATION_STEP = 0.05f;

    /** Half-width of the undampened arc, in degrees (a 90 degree arc total). */
    private static final double FULL_EFFECT_HALF_ARC_DEG = 45.0;

    private WindmillWindResponse() {
    }

    /**
     * @param facing              the windmill bearing's FACING - its front face normal
     * @param windDirectionDeg    the bearing wind blows FROM (see {@link WindDirection})
     * @param adjustedStrength    elevation-adjusted wind strength at the windmill (0-100 scale)
     * @param fullSpeedStrength   the adjusted strength that maps to normal Create speed
     * @param maxSpeedMultiplier  upper bound on the strength factor, allowing overspeed above 1.0
     * @param minDirectionalScale what the directional factor falls to when wind runs parallel to the face
     * @param reverseWhenBehind   whether wind hitting the back of the windmill reverses its spin
     * @return a signed multiplier on the windmill's normal speed, quantized to {@link #QUANTIZATION_STEP}
     */
    public static float multiplier(Direction facing, float windDirectionDeg, float adjustedStrength,
            double fullSpeedStrength, double maxSpeedMultiplier, double minDirectionalScale, boolean reverseWhenBehind) {
        if (adjustedStrength <= 0.0f || fullSpeedStrength <= 0.0) {
            return 0.0f;
        }
        double strengthFactor = Math.min(adjustedStrength / fullSpeedStrength, maxSpeedMultiplier);
        double directionFactor = directionalFactor(facing, windDirectionDeg, minDirectionalScale, reverseWhenBehind);
        return quantize((float) (strengthFactor * directionFactor));
    }

    /**
     * The signed directional factor on its own, in
     * {@code [-1, -minDirectionalScale] u [minDirectionalScale, 1]} (or always
     * 1.0 for a vertical-axis windmill). Split out from
     * {@link #multiplier} so the geometry can be reasoned about - and tested -
     * without the strength curve mixed in.
     */
    public static double directionalFactor(Direction facing, float windDirectionDeg,
            double minDirectionalScale, boolean reverseWhenBehind) {
        if (facing.getAxis().isVertical()) {
            return 1.0;
        }

        // How squarely the wind drives into the front face: +1 when it blows straight
        // into it, 0 when it runs parallel to it, -1 when it hits the back. Wind TRAVELS
        // opposite the face normal when it's hitting that face head-on, hence the negation.
        Vec3 travel = WindDirection.travelVector(windDirectionDeg);
        double intoFront = -(travel.x * facing.getStepX() + travel.z * facing.getStepZ());
        double angleFromFrontDeg = Math.toDegrees(Math.acos(Math.clamp(intoFront, -1.0, 1.0)));

        double offAxisDeg = Math.min(angleFromFrontDeg, 180.0 - angleFromFrontDeg);
        double magnitude = offAxisDeg <= FULL_EFFECT_HALF_ARC_DEG
                ? 1.0
                : lerp(1.0, minDirectionalScale, (offAxisDeg - FULL_EFFECT_HALF_ARC_DEG) / FULL_EFFECT_HALF_ARC_DEG);

        boolean fromBehind = angleFromFrontDeg > 90.0;
        return fromBehind && reverseWhenBehind ? -magnitude : magnitude;
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static float quantize(float value) {
        return Math.round(value / QUANTIZATION_STEP) * QUANTIZATION_STEP;
    }
}
