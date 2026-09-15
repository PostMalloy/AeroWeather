package com.postmalloy.aeroweather.wind;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * Wind for a single dimension: a direction (compass bearing wind blows
 * FROM) and strength (0-100), naturally drifting over time, gusting
 * occasionally, boosted by rain/thunderstorms, and overridable - indefinitely
 * via the /aeroweather command, or for a limited time via the breeze maker. See CLAUDE.md's "Wind system design" section for
 * the model this implements.
 * <p>
 * Natural (non-overridden) strength is additionally capped per weather
 * tier - clear/rain/thunder each have their own ceiling, thunderstorms
 * highest. This is a maximum, not a fixed value: the drift target itself
 * is bounded by the current tier's cap (so baseStrength keeps varying
 * naturally below it via the normal random walk, rather than wandering
 * above and then sitting pinned at the cap until it happens to drift back
 * down), and the final drift+gust+weatherBoost sum is also capped as a
 * hard ceiling regardless of what's contributing to the total (e.g. a
 * gust spike). This is separate from (and layered on top of) the
 * elevation-based scaling in {@link WindHeightScaling}, which is applied
 * downstream by whatever samples wind at a specific position - the cap
 * here bounds the base 0-100 value, not the elevation-adjusted one.
 * <p>
 * Simulation tuning comes from {@link AeroWeatherCommonConfig}, read
 * fresh each simulation step (once/second) rather than cached, since
 * NeoForge config values can change live via the in-game config screen
 * or file reload.
 */
public final class WindState {
    private static final float MIN_STRENGTH = 0.0f;
    private static final float MAX_STRENGTH = 100.0f;
    /** {@link #overrideExpiresAt} for an override that never lapses on its own. */
    private static final long NO_EXPIRY = -1L;

    private float baseDirectionDeg;
    private float targetDirectionDeg;
    private float baseStrength;
    private float targetStrength;
    private int stepsUntilRetarget;

    private float gustMagnitude;
    private int gustStepsRemaining;

    private float weatherBoost;
    private boolean raining;
    private boolean thundering;

    private boolean overridden;
    private float overrideDirectionDeg;
    private float overrideStrength;
    /** Game time a timed override (the breeze maker's) lapses at, or {@link #NO_EXPIRY} for an operator's pin. */
    private long overrideExpiresAt = NO_EXPIRY;

    // Recomputed every step; what the rest of the mod reads.
    private float directionDeg;
    private float strength;

    public WindState() {
        recomputeEffective();
    }

    /** Advances the simulation by one step (called roughly once per second). */
    public void tick(RandomSource random, boolean raining, boolean thundering) {
        this.raining = raining;
        this.thundering = thundering;
        if (!overridden) {
            tickDrift(random);
            tickGust(random);
            tickWeatherBoost(raining, thundering);
        }
        recomputeEffective();
    }

    private void tickDrift(RandomSource random) {
        float maxDirectionDelta = (float) AeroWeatherCommonConfig.DRIFT_MAX_DIRECTION_DELTA_DEG.getAsDouble();
        float maxStrengthDelta = (float) AeroWeatherCommonConfig.DRIFT_MAX_STRENGTH_DELTA.getAsDouble();
        float lerpFactor = (float) AeroWeatherCommonConfig.DRIFT_LERP_FACTOR.getAsDouble();

        if (stepsUntilRetarget <= 0) {
            targetDirectionDeg = WindDirection.normalizeDegrees(baseDirectionDeg + randomRange(random, -maxDirectionDelta, maxDirectionDelta));
            // Capped to the current weather tier's ceiling (not just [0,100]) so the drift target
            // itself stays bounded and baseStrength keeps varying naturally below the cap, rather
            // than wandering above it via the unbounded random walk and then sitting pinned at the
            // cap for a whole retarget cycle - which reads as "stuck at a constant", not "capped".
            targetStrength = Math.min(clampStrength(baseStrength + randomRange(random, -maxStrengthDelta, maxStrengthDelta)), currentStrengthCap());
            // Config is expressed in seconds; one simulation step is one second (WindSimulator's 20-tick cadence).
            int minSteps = AeroWeatherCommonConfig.DRIFT_RETARGET_MIN_SECONDS.get();
            int maxSteps = AeroWeatherCommonConfig.DRIFT_RETARGET_MAX_SECONDS.get();
            stepsUntilRetarget = random.nextIntBetweenInclusive(Math.min(minSteps, maxSteps), Math.max(minSteps, maxSteps));
        } else {
            stepsUntilRetarget--;
        }
        baseDirectionDeg = lerpAngle(baseDirectionDeg, targetDirectionDeg, lerpFactor);
        baseStrength = lerp(baseStrength, targetStrength, lerpFactor);
    }

    private void tickGust(RandomSource random) {
        if (gustStepsRemaining > 0) {
            gustStepsRemaining--;
        } else if (random.nextFloat() < AeroWeatherCommonConfig.GUST_CHANCE_PER_SECOND.getAsDouble()) {
            float minMagnitude = (float) AeroWeatherCommonConfig.GUST_MIN_MAGNITUDE.getAsDouble();
            float maxMagnitude = (float) AeroWeatherCommonConfig.GUST_MAX_MAGNITUDE.getAsDouble();
            gustMagnitude = randomRange(random, minMagnitude, maxMagnitude);
            gustStepsRemaining = AeroWeatherCommonConfig.GUST_DURATION_SECONDS.get();
        }
    }

    private void tickWeatherBoost(boolean raining, boolean thundering) {
        float rainBoost = (float) AeroWeatherCommonConfig.RAIN_BOOST.getAsDouble();
        float thunderBoost = (float) AeroWeatherCommonConfig.THUNDER_BOOST.getAsDouble();
        float easeFactor = (float) AeroWeatherCommonConfig.WEATHER_EASE_FACTOR.getAsDouble();
        float target = thundering ? thunderBoost : raining ? rainBoost : 0.0f;
        weatherBoost = lerp(weatherBoost, target, easeFactor);
    }

    private void recomputeEffective() {
        if (overridden) {
            directionDeg = overrideDirectionDeg;
            strength = overrideStrength;
        } else {
            directionDeg = baseDirectionDeg;
            int gustDurationSteps = Math.max(1, AeroWeatherCommonConfig.GUST_DURATION_SECONDS.get());
            float gustStrength = gustStepsRemaining == 0 ? 0.0f : gustMagnitude * (gustStepsRemaining / (float) gustDurationSteps);
            strength = Math.min(clampStrength(baseStrength + gustStrength + weatherBoost), currentStrengthCap());
        }
    }

    /** The weather-tier ceiling on natural (non-overridden, pre-elevation-adjustment) wind strength. */
    private float currentStrengthCap() {
        if (thundering) {
            return (float) AeroWeatherCommonConfig.STRENGTH_CAP_THUNDER.getAsDouble();
        }
        if (raining) {
            return (float) AeroWeatherCommonConfig.STRENGTH_CAP_RAIN.getAsDouble();
        }
        return (float) AeroWeatherCommonConfig.STRENGTH_CAP_CLEAR.getAsDouble();
    }

    /**
     * Pins wind to an exact direction/strength and freezes natural drift until
     * {@link #clearOverride()}. Replaces any override already active, timed or not.
     */
    public void applyOverride(WindOverride override) {
        applyTimedOverride(override, NO_EXPIRY);
    }

    /**
     * Like {@link #applyOverride(WindOverride)}, but lapsing by itself once game time
     * reaches {@code expiresAt} (see {@link #expireTimedOverride}). The breeze maker's.
     */
    public void applyTimedOverride(WindOverride override, long expiresAt) {
        this.overridden = true;
        this.overrideDirectionDeg = override.directionDeg();
        this.overrideStrength = override.strength();
        this.overrideExpiresAt = expiresAt;
        recomputeEffective();
    }

    /** Resumes natural simulation from wherever the drift/gust/weather state was left. */
    public void clearOverride() {
        this.overridden = false;
        this.overrideExpiresAt = NO_EXPIRY;
        recomputeEffective();
    }

    /**
     * Ends a timed override whose time is up, returning whether it did. An
     * indefinite (operator) override is never touched.
     */
    public boolean expireTimedOverride(long gameTime) {
        if (isTimedOverride() && gameTime >= overrideExpiresAt) {
            clearOverride();
            return true;
        }
        return false;
    }

    public boolean isOverridden() {
        return overridden;
    }

    /** True while an override that lapses by itself is active - a breeze, not an operator's pin. */
    public boolean isTimedOverride() {
        return overridden && overrideExpiresAt != NO_EXPIRY;
    }

    /** Ticks until the active timed override lapses, or 0 if there isn't one. */
    public long timedOverrideTicksRemaining(long gameTime) {
        return isTimedOverride() ? Math.max(0L, overrideExpiresAt - gameTime) : 0L;
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
        tag.putLong("OverrideExpiresAt", overrideExpiresAt);
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
        // Absent in worlds saved before the breeze maker existed, whose overrides were all indefinite.
        state.overrideExpiresAt = tag.contains("OverrideExpiresAt") ? tag.getLong("OverrideExpiresAt") : NO_EXPIRY;
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
