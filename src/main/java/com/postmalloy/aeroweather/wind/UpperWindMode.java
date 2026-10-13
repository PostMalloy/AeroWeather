package com.postmalloy.aeroweather.wind;

/** How the upper wind layer blows (M17). See {@link UpperWindLayer}. */
public enum UpperWindMode {
    /** The wind below, reversed: it dies away to nothing where the layers meet. */
    SIMPLE,
    /** A flow map of its own, turned from the wind below by an angle that wanders over time. */
    COMPLEX
}
