package com.postmalloy.aeroweather.wind;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * The eight compass cardinals used for command parsing and display. Wind
 * direction is tracked internally as a continuous 0-360 bearing (see
 * {@link WindState}); this enum only maps names to/from that bearing and
 * provides the angle math the wind simulation needs. The bearing is the
 * direction wind is blowing FROM, matching real-world convention (a
 * "north wind" blows out of the north).
 */
public enum WindDirection {
    NORTH(0, "n"),
    NORTHEAST(45, "ne"),
    EAST(90, "e"),
    SOUTHEAST(135, "se"),
    SOUTH(180, "s"),
    SOUTHWEST(225, "sw"),
    WEST(270, "w"),
    NORTHWEST(315, "nw");

    private final float degrees;
    private final String abbreviation;

    WindDirection(float degrees, String abbreviation) {
        this.degrees = degrees;
        this.abbreviation = abbreviation;
    }

    public float degrees() {
        return degrees;
    }

    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    /** Matches a full name ("northeast") or abbreviation ("ne"), case-insensitively. */
    public static Optional<WindDirection> byKeyword(String keyword) {
        String normalized = keyword.toLowerCase(Locale.ROOT);
        for (WindDirection direction : values()) {
            if (direction.name().toLowerCase(Locale.ROOT).equals(normalized) || direction.abbreviation.equals(normalized)) {
                return Optional.of(direction);
            }
        }
        return Optional.empty();
    }

    /** The cardinal whose bearing is closest to the given degree value. */
    public static WindDirection nearest(float degrees) {
        float normalized = normalizeDegrees(degrees);
        WindDirection closest = NORTH;
        float closestDelta = Float.MAX_VALUE;
        for (WindDirection direction : values()) {
            float delta = angularDifference(normalized, direction.degrees);
            if (delta < closestDelta) {
                closestDelta = delta;
                closest = direction;
            }
        }
        return closest;
    }

    /** Normalizes a degree value into the [0, 360) range. */
    public static float normalizeDegrees(float degrees) {
        float result = degrees % 360.0f;
        return result < 0 ? result + 360.0f : result;
    }

    /** The shortest angular distance between two bearings, always in [0, 180]. */
    public static float angularDifference(float a, float b) {
        float diff = Math.abs(normalizeDegrees(a) - normalizeDegrees(b)) % 360.0f;
        return diff > 180.0f ? 360.0f - diff : diff;
    }

    /** Horizontal unit vector wind blows TOWARD (opposite the FROM-bearing); Y is always 0. */
    public static Vec3 travelVector(float bearingDeg) {
        double bearingRad = Math.toRadians(bearingDeg);
        return new Vec3(-Math.sin(bearingRad), 0.0, Math.cos(bearingRad));
    }
}
