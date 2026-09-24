package com.postmalloy.aeroweather.wind;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * Wind for a single dimension: a direction (compass bearing wind blows
 * FROM) and strength (0-100), naturally drifting over time, gusting
 * occasionally, boosted by rain/thunderstorms, and overridable - indefinitely
 * via the /aeroweather command, or for a limited time via the breeze maker. See CLAUDE.md's "Wind system design" section for
 * the model this implements.
 * <p>
 * Natural strength walks within a <b>band</b> set by the weather: 0 to 50 in clear
 * weather, 0 to 75 in rain, 0 to 100 in thunder (the three caps in the config). The
 * walk itself moves on a normalized position from 0 to 1, and strength is that
 * position times the band. So the walk never has to explore its way up when rain
 * starts - the whole band widens under it - and every weather gets the same
 * statistics: an even spread over its band, averaging half its cap.
 * <p>
 * Three things stop it tending toward 0, which it visibly did before:
 * <ul>
 *   <li><b>It starts anywhere in the band, not at 0.</b> Every state used to begin
 *       at strength 0, and with small drift steps the walk took 20-30 minutes to climb
 *       out: five minutes into a fresh world, over half of worlds were still below 10.
 *       Saves from before this change are re-seeded once, easing up rather than jumping.</li>
 *   <li><b>It reflects off the band's edges rather than clamping.</b> Clamping stacks up
 *       probability at exactly 0 and at the cap; reflecting keeps the spread even.</li>
 *   <li><b>No separate rain/thunder boost.</b> An additive boost set a floor - rain could
 *       never drop below its boost - so it's gone; widening the band does its job, raising
 *       the average from 25 to 37.5 to 50.</li>
 * </ul>
 * This is separate from (and layered on top of) the elevation-based scaling in
 * {@link WindHeightScaling}, which is applied downstream by whatever samples wind at
 * a specific position - the band bounds the base 0-100 value, not the
 * elevation-adjusted one.
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
    /** Where the strength walk is, as a fraction of the band: 0 is calm, 1 is the weather's cap. */
    private float driftPosition;
    private float driftTarget;
    private int stepsUntilRetarget;
    /**
     * The band's current width, easing toward the weather's cap so the band widens and
     * narrows smoothly as rain comes and goes rather than jumping with the weather.
     */
    private float bandCap;

    private float gustMagnitude;
    private int gustStepsRemaining;

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
        // Seeded anywhere in the band - never at 0, which is what made new worlds calm for
        // their first half hour. Clear weather's band until the first tick says otherwise.
        this.driftPosition = ThreadLocalRandom.current().nextFloat();
        this.driftTarget = this.driftPosition;
        this.bandCap = currentStrengthCap();
        recomputeEffective();
    }

    /** Advances the simulation by one step (called roughly once per second). */
    public void tick(RandomSource random, boolean raining, boolean thundering) {
        this.raining = raining;
        this.thundering = thundering;
        if (!overridden) {
            tickDrift(random);
            tickGust(random);
            tickBand();
        }
        recomputeEffective();
    }

    private void tickDrift(RandomSource random) {
        float maxDirectionDelta = (float) AeroWeatherCommonConfig.DRIFT_MAX_DIRECTION_DELTA_DEG.getAsDouble();
        float maxStrengthDelta = (float) AeroWeatherCommonConfig.DRIFT_MAX_STRENGTH_DELTA.getAsDouble();
        float lerpFactor = (float) AeroWeatherCommonConfig.DRIFT_LERP_FACTOR.getAsDouble();

        if (stepsUntilRetarget <= 0) {
            targetDirectionDeg = WindDirection.normalizeDegrees(baseDirectionDeg + randomRange(random, -maxDirectionDelta, maxDirectionDelta));
            // maxStrengthDelta is in strength units, so it's turned into a fraction of the current
            // band: strength still moves at most that far per retarget, in every weather.
            float step = randomRange(random, -maxStrengthDelta, maxStrengthDelta) / Math.max(bandWidth(), 1.0f);
            driftTarget = reflectIntoUnit(driftPosition + step);
            // Config is expressed in seconds; one simulation step is one second (WindSimulator's 20-tick cadence).
            int minSteps = AeroWeatherCommonConfig.DRIFT_RETARGET_MIN_SECONDS.get();
            int maxSteps = AeroWeatherCommonConfig.DRIFT_RETARGET_MAX_SECONDS.get();
            stepsUntilRetarget = random.nextIntBetweenInclusive(Math.min(minSteps, maxSteps), Math.max(minSteps, maxSteps));
        } else {
            stepsUntilRetarget--;
        }
        baseDirectionDeg = lerpAngle(baseDirectionDeg, targetDirectionDeg, lerpFactor);
        driftPosition = lerp(driftPosition, driftTarget, lerpFactor);
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

    /** Eases the band toward the weather's cap, so rain widens it over seconds rather than at once. */
    private void tickBand() {
        float easeFactor = (float) AeroWeatherCommonConfig.WEATHER_EASE_FACTOR.getAsDouble();
        bandCap = lerp(bandCap, currentStrengthCap(), easeFactor);
    }

    private void recomputeEffective() {
        if (overridden) {
            directionDeg = overrideDirectionDeg;
            strength = overrideStrength;
        } else {
            directionDeg = baseDirectionDeg;
            int gustDurationSteps = Math.max(1, AeroWeatherCommonConfig.GUST_DURATION_SECONDS.get());
            float gustStrength = gustStepsRemaining == 0 ? 0.0f : gustMagnitude * (gustStepsRemaining / (float) gustDurationSteps);
            // Capped by the eased band, not the weather's cap directly: when rain stops the cap
            // drops at once, and capping by it would cut the wind off in a single step.
            strength = Math.min(clampStrength(bandFloor() + driftPosition * bandWidth() + gustStrength), bandCap);
        }
    }

    /**
     * The bottom of the band: natural wind never drops below this. It <em>raises</em>
     * the band's bottom rather than clamping to it - clamping would stack every value
     * below the floor up at exactly the floor, about a fifth of all clear weather at
     * one number, while this keeps the walk evenly spread over what's left. Never above
     * the cap, so a floor configured higher than a weather's cap simply pins that
     * weather at its cap instead of inverting the band.
     */
    private float bandFloor() {
        return Math.min((float) AeroWeatherCommonConfig.STRENGTH_FLOOR.getAsDouble(), bandCap);
    }

    /** How much of the band the walk has to move in, between the floor and the eased cap. */
    private float bandWidth() {
        return bandCap - bandFloor();
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

    public CompoundTag save(CompoundTag tag) {
        tag.putFloat("BaseDirection", baseDirectionDeg);
        tag.putFloat("TargetDirection", targetDirectionDeg);
        tag.putFloat("DriftPosition", driftPosition);
        tag.putFloat("DriftTarget", driftTarget);
        tag.putFloat("BandCap", bandCap);
        tag.putInt("StepsUntilRetarget", stepsUntilRetarget);
        tag.putFloat("GustMagnitude", gustMagnitude);
        tag.putInt("GustStepsRemaining", gustStepsRemaining);
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
        // Saves from before the band keep the random seed the constructor just gave them, rather
        // than their old strength: those were exactly the worlds stuck near 0. The old keys are
        // simply ignored. The walk eases from wherever it was, so nothing jumps on screen.
        if (tag.contains("DriftPosition")) {
            state.driftPosition = tag.getFloat("DriftPosition");
            state.driftTarget = tag.getFloat("DriftTarget");
            state.bandCap = tag.getFloat("BandCap");
        }
        state.stepsUntilRetarget = tag.getInt("StepsUntilRetarget");
        state.gustMagnitude = tag.getFloat("GustMagnitude");
        state.gustStepsRemaining = tag.getInt("GustStepsRemaining");
        state.overridden = tag.getBoolean("Overridden");
        state.overrideDirectionDeg = tag.getFloat("OverrideDirection");
        state.overrideStrength = tag.getFloat("OverrideStrength");
        // Absent in worlds saved before the breeze maker existed, whose overrides were all indefinite.
        state.overrideExpiresAt = tag.contains("OverrideExpiresAt") ? tag.getLong("OverrideExpiresAt") : NO_EXPIRY;
        state.recomputeEffective();
        return state;
    }

    /**
     * Folds a walk position back into [0, 1] by reflecting off the edges, as a ball
     * bouncing off walls would. Clamping instead parks any overshoot exactly on the
     * edge, which piles probability up at 0 and at the cap; reflecting leaves the walk
     * evenly spread. Correct for any overshoot, however large.
     */
    private static float reflectIntoUnit(float value) {
        float folded = value - 2.0f * (float) Math.floor(value / 2.0f);   // into [0, 2)
        return folded > 1.0f ? 2.0f - folded : folded;
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
