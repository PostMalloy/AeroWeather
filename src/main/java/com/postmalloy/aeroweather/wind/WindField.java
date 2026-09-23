package com.postmalloy.aeroweather.wind;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * How wind varies from place to place: a direction offset from a smooth flow
 * map, and a strength multiplier from the biomes around the sample (M14).
 * <p>
 * <b>The field does not change over time.</b> It depends only on position,
 * biomes and config — never on the wind state, which is what actually updates
 * every second. That is what makes it affordable: every cell is computed once
 * and kept, and the 1 Hz wind tick costs nothing extra. Callers combine it with
 * the live per-dimension wind themselves:
 * <pre>
 *   direction = baseDirection + sample.directionOffsetDeg()
 *   strength  = baseStrength  * sample.strengthFactor()
 * </pre>
 *
 * <h2>Why it is shaped like this</h2>
 * Biome strength is cached in two layers on a 16-block grid:
 * <ul>
 *   <li><b>raw</b> — one biome lookup at a cell's centre, turned into a factor;</li>
 *   <li><b>blurred</b> — the mean of raw over a square neighbourhood.</li>
 * </ul>
 * Sampling bilinearly interpolates <em>blurred</em> across the four surrounding
 * cell centres. Blur plus interpolation is what stops a forest/plains border
 * being a step: it eases over roughly twice the blend radius. Raw cells are
 * shared between neighbouring blurs, so filling amortises to about one biome
 * lookup per cell.
 * <p>
 * The direction offset is pure noise, cheap enough to evaluate per call, so it
 * isn't cached at all and stays perfectly continuous.
 *
 * <h2>Chunk safety — the one real hazard</h2>
 * Biome reads go through {@code getChunkNow}, never {@code level.getBiome}.
 * On the server {@code ServerChunkCache.getChunk(.., BIOMES, false)} will
 * happily drive a border chunk through worldgen synchronously on the main
 * thread, and {@code getUncachedNoiseBiome} runs the climate sampler; neither is
 * acceptable at this call rate. {@code getChunkNow} generates nothing, and
 * resolves on the client to a plain array index into the view-distance ring.
 * <p>
 * A miss is treated as neutral and <b>not cached</b>, so the cell fills in
 * properly once the chunk loads. That also sidesteps
 * {@code ClientLevel.getUncachedNoiseBiome}, which silently answers
 * {@code minecraft:plains} for anything out of range.
 */
public final class WindField {
    /** Neutral: no direction offset, unscaled strength. What you get with the feature off, or before anything is known. */
    public static final Sample NEUTRAL = new Sample(0.0f, 1.0f);

    /** Cell edge in blocks. Chunk-aligned so a cell never straddles two chunks' biome data. */
    private static final int CELL_BITS = 4;
    private static final int CELL_SIZE = 1 << CELL_BITS;

    /** Beyond this many cells a level's cache is dropped, bounding a long session's wandering. */
    private static final int MAX_CELLS = 16384;

    /** Biomes are sampled at this height above sea level, keeping the cache 2D — see {@link #biomeFactorAt}. */
    private static final int SAMPLE_HEIGHT_ABOVE_SEA = 16;

    /**
     * Split by side on purpose. In single player the client level and the server
     * level share a dimension key but run on different threads, so one map would
     * hand both of them the same {@link Cells} — and the owner check below would
     * then quietly give one side neutral wind, which is the exact client/server
     * disagreement the synced biome table exists to prevent.
     */
    private static final Map<ResourceKey<Level>, Cells> CLIENT_CACHES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Cells> SERVER_CACHES = new ConcurrentHashMap<>();

    private WindField() {
    }

    /** A direction offset in degrees and a strength multiplier, both already blended. */
    public record Sample(float directionOffsetDeg, float strengthFactor) {
    }

    /**
     * The field at a world position. Never null; returns {@link #NEUTRAL} when
     * per-biome wind is switched off, so callers need no branch of their own.
     */
    public static Sample at(Level level, double x, double z) {
        if (!AeroWeatherCommonConfig.PER_BIOME_WIND_ENABLED.get()) {
            return NEUTRAL;
        }
        // Before the table arrives, a client would otherwise bend wind with a zero seed and
        // neutral factors - visibly different from the server for the moment it takes to sync.
        // Uniform wind is the better thing to show meanwhile, and it's what the server has too.
        if (!BiomeWindFactors.isReady()) {
            return NEUTRAL;
        }

        long seed = BiomeWindFactors.flowSeed();
        double scale = AeroWeatherCommonConfig.FLOW_MAP_SCALE_BLOCKS.getAsDouble();
        double fieldX = x / scale;
        double fieldZ = z / scale;

        double directionOffset = FlowNoise.sample(fieldX, fieldZ, seed)
                * AeroWeatherCommonConfig.FLOW_MAP_MAX_DIRECTION_DEVIATION_DEGREES.getAsDouble();

        // A second, independent layer so strength wanders a little even within one
        // biome. One octave is plenty: nobody can see detail in a strength field.
        double strengthNoise = FlowNoise.sampleCoarse(fieldX, fieldZ, seed ^ 0x51ED270B4A1D9E77L)
                * AeroWeatherCommonConfig.FLOW_MAP_STRENGTH_VARIATION.getAsDouble();

        double influence = AeroWeatherCommonConfig.BIOME_WIND_STRENGTH_INFLUENCE.getAsDouble();
        double biomeFactor = 1.0 + (blendedBiomeFactor(level, x, z) - 1.0) * influence;

        return new Sample((float) directionOffset, (float) Math.max(0.0, biomeFactor * (1.0 + strengthNoise)));
    }

    /** Drops every cached cell — on dimension change, config reload or tag reload. */
    public static void invalidate() {
        CLIENT_CACHES.clear();
        SERVER_CACHES.clear();
    }

    /**
     * Bilinear interpolation of the blurred biome factor. Cell centres sit at
     * {@code cell * 16 + 8}, so the sample is expressed relative to those.
     */
    private static float blendedBiomeFactor(Level level, double x, double z) {
        Cells cells = cellsFor(level);
        if (cells == null) {
            return BiomeWindFactors.NEUTRAL;
        }

        double u = (x - (CELL_SIZE / 2.0)) / CELL_SIZE;
        double v = (z - (CELL_SIZE / 2.0)) / CELL_SIZE;
        int cellX = floor(u);
        int cellZ = floor(v);
        float tx = (float) (u - cellX);
        float tz = (float) (v - cellZ);

        float f00 = cells.blurred(level, cellX, cellZ);
        float f10 = cells.blurred(level, cellX + 1, cellZ);
        float f01 = cells.blurred(level, cellX, cellZ + 1);
        float f11 = cells.blurred(level, cellX + 1, cellZ + 1);

        return lerp(lerp(f00, f10, tx), lerp(f01, f11, tx), tz);
    }

    /**
     * The per-level cache, or null when this thread may not touch it.
     * <p>
     * Each cache belongs to the thread that created it — the client thread or the
     * server main thread. Anything else (a physics worker, say) computes uncached
     * rather than risking a torn {@code Long2FloatOpenHashMap}, which costs a
     * little accuracy on a rare path and keeps the hot path lock-free. Locking
     * here would show up: this runs thousands of times a tick in heavy rain.
     */
    private static Cells cellsFor(Level level) {
        Map<ResourceKey<Level>, Cells> caches = level.isClientSide() ? CLIENT_CACHES : SERVER_CACHES;
        Cells cells = caches.computeIfAbsent(level.dimension(), key -> new Cells());
        return cells.owner == Thread.currentThread() ? cells : null;
    }

    /**
     * The factor for one cell's centre, from an already-loaded chunk only.
     * Returns {@code NaN} when the chunk isn't loaded, which the caller treats as
     * "don't cache this".
     * <p>
     * Sampled at a fixed height rather than the caller's Y. That keeps the cache
     * two-dimensional, and stops cave biomes from deciding what the wind does on
     * the surface above them; altitude is {@link WindHeightScaling}'s job.
     */
    private static float biomeFactorAt(Level level, int cellX, int cellZ) {
        int blockX = (cellX << CELL_BITS) + CELL_SIZE / 2;
        int blockZ = (cellZ << CELL_BITS) + CELL_SIZE / 2;

        ChunkAccess chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
        if (chunk == null) {
            return Float.NaN;
        }

        Holder<Biome> biome = chunk.getNoiseBiome(
                QuartPos.fromBlock(blockX),
                QuartPos.fromBlock(level.getSeaLevel() + SAMPLE_HEIGHT_ABOVE_SEA),
                QuartPos.fromBlock(blockZ));
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        return key == null ? BiomeWindFactors.NEUTRAL : BiomeWindFactors.factorFor(key.location());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /** One level's cells. Single-threaded by construction — see {@link #cellsFor}. */
    private static final class Cells {
        private final Thread owner = Thread.currentThread();
        private final Long2FloatOpenHashMap raw = new Long2FloatOpenHashMap();
        private final Long2FloatOpenHashMap blurred = new Long2FloatOpenHashMap();

        Cells() {
            raw.defaultReturnValue(Float.NaN);
            blurred.defaultReturnValue(Float.NaN);
        }

        float blurred(Level level, int cellX, int cellZ) {
            long key = key(cellX, cellZ);
            float cached = blurred.get(key);
            if (!Float.isNaN(cached)) {
                return cached;
            }

            int radius = blurRadiusCells();
            float total = 0.0f;
            int counted = 0;
            boolean complete = true;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    float factor = raw(level, cellX + dx, cellZ + dz);
                    if (Float.isNaN(factor)) {
                        complete = false;
                    } else {
                        total += factor;
                        counted++;
                    }
                }
            }

            float result = counted == 0 ? BiomeWindFactors.NEUTRAL : total / counted;
            // Only keep a blur built from fully loaded surroundings. Caching a partial
            // one would freeze an edge-of-world artefact in place for the session.
            if (complete) {
                evictIfFull();
                blurred.put(key, result);
            }
            return result;
        }

        private float raw(Level level, int cellX, int cellZ) {
            long key = key(cellX, cellZ);
            float cached = raw.get(key);
            if (!Float.isNaN(cached)) {
                return cached;
            }
            float factor = biomeFactorAt(level, cellX, cellZ);
            if (!Float.isNaN(factor)) {
                evictIfFull();
                raw.put(key, factor);
            }
            return factor;
        }

        private void evictIfFull() {
            if (raw.size() + blurred.size() >= MAX_CELLS) {
                raw.clear();
                blurred.clear();
            }
        }

        private static int blurRadiusCells() {
            double blocks = AeroWeatherCommonConfig.BIOME_WIND_BLEND_RADIUS_BLOCKS.getAsDouble();
            return Math.max(0, (int) Math.ceil(blocks / CELL_SIZE));
        }

        private static long key(int cellX, int cellZ) {
            return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        }
    }
}
