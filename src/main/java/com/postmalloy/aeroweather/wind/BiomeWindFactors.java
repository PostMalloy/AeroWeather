package com.postmalloy.aeroweather.wind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;

/**
 * How much each biome scales wind strength: open ground blows harder than
 * forest. One table per game, rebuilt whenever tags change, and handed to
 * clients verbatim so both sides agree (see M14 in CLAUDE.md).
 * <p>
 * A biome's factor is resolved in three steps, first match winning:
 * <ol>
 *   <li>an explicit {@code biomeWindFactors} config entry;</li>
 *   <li>the first matching tag rule in {@link #TAG_RULES};</li>
 *   <li>a fallback derived from the biome's downfall, so an unrecognised modded
 *       biome still lands somewhere sensible.</li>
 * </ol>
 * The table is read on hot paths, so it is a plain immutable map swapped in
 * wholesale rather than mutated in place; readers never synchronise.
 */
public final class BiomeWindFactors {
    /** Applied to any biome we can't place at all — and to unloaded chunks. */
    public static final float NEUTRAL = 1.0f;

    /**
     * Tag rules in priority order, first match winning. Ordering matters where a
     * biome carries several: a windswept forest is a forest first, because its
     * trees are what actually block the wind.
     * <p>
     * NeoForge's {@code Tags.Biomes} comes first throughout because it is far
     * richer than vanilla's — vanilla has no {@code IS_PLAINS},
     * {@code IS_DESERT} or {@code IS_SWAMP} at all — and is the set modded
     * biomes conventionally populate. The vanilla entries are a second pass for
     * anything that only carries those.
     * <p>
     * Mountains sit near neutral on purpose. They are rough ground, but they are
     * also tall, and {@link WindHeightScaling} already multiplies wind by
     * altitude; giving them a high factor as well would double-count and make
     * peaks absurd.
     */
    private static final List<Rule> TAG_RULES = List.of(
            // Dense canopy: the strongest shelter there is.
            new Rule(Tags.Biomes.IS_DENSE_VEGETATION, 0.55f),
            new Rule(Tags.Biomes.IS_JUNGLE, 0.55f),
            new Rule(BiomeTags.IS_JUNGLE, 0.55f),
            new Rule(Tags.Biomes.IS_OLD_GROWTH, 0.6f),

            // Ordinary woodland.
            new Rule(Tags.Biomes.IS_SWAMP, 0.65f),
            new Rule(Tags.Biomes.IS_MUSHROOM, 0.65f),
            new Rule(Tags.Biomes.IS_CONIFEROUS_TREE, 0.7f),
            new Rule(Tags.Biomes.IS_DECIDUOUS_TREE, 0.7f),
            new Rule(Tags.Biomes.IS_FOREST, 0.7f),
            new Rule(BiomeTags.IS_FOREST, 0.7f),
            new Rule(Tags.Biomes.IS_TAIGA, 0.72f),
            new Rule(BiomeTags.IS_TAIGA, 0.72f),

            // Enclosed or sheltered.
            new Rule(Tags.Biomes.IS_CAVE, 0.4f),
            new Rule(Tags.Biomes.IS_UNDERGROUND, 0.4f),
            new Rule(Tags.Biomes.IS_RIVER, 1.05f),
            new Rule(BiomeTags.IS_RIVER, 1.05f),

            // Rough ground, but see the note above about altitude.
            new Rule(Tags.Biomes.IS_HILL, 0.95f),
            new Rule(BiomeTags.IS_HILL, 0.95f),
            new Rule(Tags.Biomes.IS_MOUNTAIN_SLOPE, 0.95f),

            // Exposed: nothing between the wind and you.
            new Rule(Tags.Biomes.IS_DEEP_OCEAN, 1.35f),
            new Rule(BiomeTags.IS_DEEP_OCEAN, 1.35f),
            new Rule(Tags.Biomes.IS_OCEAN, 1.3f),
            new Rule(BiomeTags.IS_OCEAN, 1.3f),
            new Rule(Tags.Biomes.IS_MOUNTAIN_PEAK, 1.3f),
            new Rule(Tags.Biomes.IS_WINDSWEPT, 1.3f),
            new Rule(Tags.Biomes.IS_BEACH, 1.25f),
            new Rule(BiomeTags.IS_BEACH, 1.25f),
            new Rule(Tags.Biomes.IS_STONY_SHORES, 1.2f),
            new Rule(Tags.Biomes.IS_DESERT, 1.25f),
            new Rule(Tags.Biomes.IS_BADLANDS, 1.25f),
            new Rule(BiomeTags.IS_BADLANDS, 1.25f),
            new Rule(Tags.Biomes.IS_WASTELAND, 1.25f),
            new Rule(Tags.Biomes.IS_PLATEAU, 1.2f),
            new Rule(Tags.Biomes.IS_SPARSE_VEGETATION, 1.15f),
            new Rule(Tags.Biomes.IS_SAVANNA, 1.15f),
            new Rule(BiomeTags.IS_SAVANNA, 1.15f),
            new Rule(Tags.Biomes.IS_SNOWY_PLAINS, 1.15f),
            new Rule(Tags.Biomes.IS_PLAINS, 1.15f),
            new Rule(Tags.Biomes.IS_MOUNTAIN, 1.0f),
            new Rule(BiomeTags.IS_MOUNTAIN, 1.0f));

    /** Downfall fallback: dry ground is open, wet ground tends to be overgrown. */
    private static final float DRY_FACTOR = 1.15f;
    private static final float WET_FACTOR = 0.6f;

    private static volatile Map<ResourceLocation, Float> factors = Map.of();
    private static volatile long flowSeed;

    private BiomeWindFactors() {
    }

    private record Rule(TagKey<Biome> tag, float factor) {
    }

    /** The factor for a biome, or {@link #NEUTRAL} if it isn't in the table. */
    public static float factorFor(ResourceLocation biomeId) {
        if (biomeId == null) {
            return NEUTRAL;
        }
        return factors.getOrDefault(biomeId, NEUTRAL);
    }

    /** Seed for the flow map. Chosen by the server and synced, since the client never learns the world seed. */
    public static long flowSeed() {
        return flowSeed;
    }

    public static Map<ResourceLocation, Float> table() {
        return factors;
    }

    /** True once a table has arrived, so callers can fall back rather than treat everything as neutral. */
    public static boolean isReady() {
        return !factors.isEmpty();
    }

    /**
     * Builds the table from the live biome registry. Server side, from
     * {@code TagsUpdatedEvent} — tags must be bound before {@code Holder.is} means
     * anything, and a datapack reload can change them.
     */
    public static void rebuild(RegistryAccess registryAccess, long seed) {
        Map<ResourceLocation, Float> overrides = parseOverrides();
        Map<ResourceLocation, Float> built = new LinkedHashMap<>();

        registryAccess.registryOrThrow(Registries.BIOME).holders().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            Float override = overrides.get(id);
            built.put(id, override != null ? override : defaultFactorFor(holder));
        });

        factors = Map.copyOf(built);
        flowSeed = seed;
        AeroWeather.LOGGER.debug("Built wind factors for {} biomes", built.size());
    }

    /** Applies a table received from the server. */
    public static void accept(long seed, Map<ResourceLocation, Float> received) {
        factors = Map.copyOf(received);
        flowSeed = seed;
    }

    /** Drops the table, so a disconnected client doesn't keep applying a server's values. */
    public static void clear() {
        factors = Map.of();
        flowSeed = 0L;
    }

    /**
     * What the config file should list for a biome the user hasn't set: its tag
     * rule, or a downfall-derived guess. Also used to populate the config.
     */
    public static float defaultFactorFor(Holder<Biome> holder) {
        for (Rule rule : TAG_RULES) {
            if (holder.is(rule.tag())) {
                return rule.factor();
            }
        }
        // Nothing recognised it. Downfall is the only cheap public signal that
        // tracks vegetation at all - dry biomes are usually open, wet ones usually
        // aren't - so it beats assuming neutral for a whole modded biome set.
        float downfall = clamp01(holder.value().getModifiedClimateSettings().downfall());
        return DRY_FACTOR + (WET_FACTOR - DRY_FACTOR) * downfall;
    }

    /**
     * The whole registry as {@code id=factor} lines, sorted, for writing back to
     * the config so modded biomes show up ready to tune.
     */
    public static List<String> asConfigLines(RegistryAccess registryAccess) {
        Map<ResourceLocation, Float> overrides = parseOverrides();
        Map<String, String> sorted = new TreeMap<>();
        registryAccess.registryOrThrow(Registries.BIOME).holders().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            Float override = overrides.get(id);
            float factor = override != null ? override : defaultFactorFor(holder);
            sorted.put(id.toString(), format(id, factor));
        });
        return new ArrayList<>(sorted.values());
    }

    /** Parses the config list. Malformed entries are logged once and skipped rather than failing the load. */
    private static Map<ResourceLocation, Float> parseOverrides() {
        Map<ResourceLocation, Float> parsed = new LinkedHashMap<>();
        for (String entry : AeroWeatherCommonConfig.BIOME_WIND_FACTORS.get()) {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                AeroWeather.LOGGER.warn("Ignoring malformed biomeWindFactors entry '{}' (expected 'namespace:biome=1.0')", entry);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, separator).trim());
            if (id == null) {
                AeroWeather.LOGGER.warn("Ignoring biomeWindFactors entry '{}': not a valid biome id", entry);
                continue;
            }
            try {
                parsed.put(id, Float.parseFloat(entry.substring(separator + 1).trim()));
            } catch (NumberFormatException malformed) {
                AeroWeather.LOGGER.warn("Ignoring biomeWindFactors entry '{}': not a number", entry);
            }
        }
        return parsed;
    }

    private static String format(ResourceLocation id, float factor) {
        return String.format(Locale.ROOT, "%s=%.2f", id, factor);
    }

    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : Math.min(value, 1.0f);
    }
}
