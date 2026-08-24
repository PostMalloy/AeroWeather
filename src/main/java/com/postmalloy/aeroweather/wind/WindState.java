package com.postmalloy.aeroweather.wind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * Wind for a single dimension: a direction (compass bearing wind blows
 * FROM) and strength (0-100), naturally drifting over time, gusting
 * occasionally, boosted by rain/thunderstorms, and overridable via the
 * /aeroweather command. See CLAUDE.md's "Wind system design" section for
 * the model this implements.
 *
 * The simulation constants below are hardcoded for now; they become
 * configurable in a later milestone (see CLAUDE.md roadmap, M5).
 */
public final class WindState {
    private static final float MIN_STRENGTH = 0.0f;
    private static final float MAX_STRENGTH = 100.0f;

    // Natural drift: how far a newly rolled target may differ from the
    // current base value, how often it's re-rolled, and how quickly the
    // base value eases toward that target each simulation step.
    private static final float DRIFT_MAX_DIRECTION_DELTA = 60.0f;
    private static final float DRIFT_MAX_STRENGTH_DELTA = 30.0f;
    private static final int DRIFT_RETARGET_MIN_STEPS = 15; // ~15s at 1 step/s
    private static final int DRIFT_RETARGET_MAX_STEPS = 45; // ~45s
    private static final float DRIFT_LERP_FACTOR = 0.08f;

    // Gusts: short additive strength spikes that ramp linearly back to 0.
    private static final float GUST_CHANCE_PER_STEP = 0.02f;
    private static final float GUST_MIN_MAGNITUDE = 10.0f;
    private static final float GUST_MAX_MAGNITUDE = 25.0f;
    private static final int GUST_DURATION_STEPS = 4; // ~4s

    // Weather coupling.
    private static final float RAIN_BOOST = 15.0f;
    private static final float THUNDER_BOOST = 35.0f;
    private static final float WEATHER_EASE_FACTOR = 0.1f;

    private float baseDirectionDeg;
    private float targetDirectionDeg;
    private float baseStrength;
    private float targetStrength;
    private int stepsUntilRetarget;

    private float gustMagnitude;
    private int gustStepsRemaining;

    private float weatherBoost;

    private boolean overridden;
    private float overrideDirectionDeg;
    private float overrideStrength;

    // Recomputed every step; what the rest of the mod reads.
    private float directionDeg;
    private float strength;

    public WindState() {
        recomputeEffective();
    }

    /** Advances the simulation by one step (called roughly once per second). */
    public void tick(RandomSource random, boolean raining, boolean thundering) {
        if (!overridden) {
            tickDrift(random);
            tickGust(random);
            tickWeatherBoost(raining, thundering);
        }
        recomputeEffective();
    }

    private void tickDrift(RandomSource random) {
        if (stepsUntilRetarget <= 0) {
            targetDirectionDeg = WindDirection.normalizeDegrees(baseDirectionDeg + randomRange(random, -DRIFT_MAX_DIRECTION_DELTA, DRIFT_MAX_DIRECTION_DELTA));
            targetStrength = clampStrength(baseStrength + randomRange(random, -DRIFT_MAX_STRENGTH_DELTA, DRIFT_MAX_STRENGTH_DELTA));
            stepsUntilRetarget = random.nextIntBetweenInclusive(DRIFT_RETARGET_MIN_STEPS, DRIFT_RETARGET_MAX_STEPS);
        } else {
            stepsUntilRetarget--;
        }
        baseDirectionDeg = lerpAngle(baseDirectionDeg, targetDirectionDeg, DRIFT_LERP_FACTOR);
        baseStrength = lerp(baseStrength, targetStrength, DRIFT_LERP_FACTOR);
    }

    private void tickGust(RandomSource random) {
        if (gustStepsRemaining > 0) {
            gustStepsRemaining--;
        } else if (random.nextFloat() < GUST_CHANCE_PER_STEP) {
            gustMagnitude = randomRange(random, GUST_MIN_MAGNITUDE, GUST_MAX_MAGNITUDE);
            gustStepsRemaining = GUST_DURATION_STEPS;
        }
    }

    private void tickWeatherBoost(boolean raining, boolean thundering) {
        float target = thundering ? THUNDER_BOOST : raining ? RAIN_BOOST : 0.0f;
        weatherBoost = lerp(weatherBoost, target, WEATHER_EASE_FACTOR);
    }

    private void recomputeEffective() {
        if (overridden) {
            directionDeg = overrideDirectionDeg;
            strength = overrideStrength;
        } else {
            directionDeg = baseDirectionDeg;
            float gustStrength = gustStepsRemaining == 0 ? 0.0f : gustMagnitude * (gustStepsRemaining / (float) GUST_DURATION_STEPS);
            strength = clampStrength(baseStrength + gustStrength + weatherBoost);
        }
    }

    /** Pins wind to an exact direction/strength and freezes natural drift until {@link #clearOverride()}. */
    public void applyOverride(WindOverride override) {
        this.overridden = true;
        this.overrideDirectionDeg = override.directionDeg();
        this.overrideStrength = override.strength();
        recomputeEffective();
    }

    /** Resumes natural simulation from wherever the drift/gust/weather state was left. */
    public void clearOverride() {
        this.overridden = false;
        recomputeEffective();
    }

    public boolean isOverridden() {
        return overridden;
    }

    /** The compass bearing wind is currently blowing FROM, in [0, 360). */
    public float directionDeg() {
        return directionDeg;
    }

    /** Current effective wind strength, in [0, 100]. */
    public float strength() {
        return strength;
    }

    public float weatherBoost() {
        return weatherBoost;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putFloat("BaseDirection", baseDirectionDeg);
        tag.putFloat("TargetDirection", targetDirectionDeg);
        tag.putFloat("BaseStrength", baseStrength);
        tag.putFloat("TargetStrength", targetStrength);
        tag.putInt("StepsUntilRetarget", stepsUntilRetarget);
        tag.putFloat("GustMagnitude", gustMagnitude);
        tag.putInt("GustStepsRemaining", gustStepsRemaining);
        tag.putFloat("WeatherBoost", weatherBoost);
        tag.putBoolean("Overridden", overridden);
        tag.putFloat("OverrideDirection", overrideDirectionDeg);
        tag.putFloat("OverrideStrength", overrideStrength);
        return tag;
    }

    public static WindState load(CompoundTag tag) {
        WindState state = new WindState();
        state.baseDirectionDeg = tag.getFloat("BaseDirection");
        state.targetDirectionDeg = tag.getFloat("TargetDirection");
        state.baseStrength = tag.getFloat("BaseStrength");
        state.targetStrength = tag.getFloat("TargetStrength");
        state.stepsUntilRetarget = tag.getInt("StepsUntilRetarget");
        state.gustMagnitude = tag.getFloat("GustMagnitude");
        state.gustStepsRemaining = tag.getInt("GustStepsRemaining");
        state.weatherBoost = tag.getFloat("WeatherBoost");
        state.overridden = tag.getBoolean("Overridden");
        state.overrideDirectionDeg = tag.getFloat("OverrideDirection");
        state.overrideStrength = tag.getFloat("OverrideStrength");
        state.recomputeEffective();
        return state;
    }

    private static float clampStrength(float value) {
        return Math.clamp(value, MIN_STRENGTH, MAX_STRENGTH);
    }

    private static float randomRange(RandomSource random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private static float lerp(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    /** Lerps between two bearings by the shorter angular path (e.g. 350 -> 10 moves through 360/0, not backward through 180). */
    private static float lerpAngle(float from, float to, float factor) {
        float delta = WindDirection.normalizeDegrees(to - from + 180.0f) - 180.0f;
        return WindDirection.normalizeDegrees(from + delta * factor);
    }
}
