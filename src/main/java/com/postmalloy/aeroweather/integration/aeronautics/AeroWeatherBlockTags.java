package com.postmalloy.aeroweather.integration.aeronautics;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags identifying "lift" blocks for {@link AeronauticsWindForceApplier}'s
 * lift-ratio calculation. These tags are shipped as plain resource data
 * by Create Aeronautics/Create themselves (confirmed by extracting
 * {@code data/.../tags/block/*.json} from the real published jars — see
 * CLAUDE.md's M7 design section) — referencing a {@link TagKey} requires
 * zero compile-time dependency on Aeronautics/Create Java classes, and
 * resolving it at runtime is safe even when those mods are absent (an
 * unmatched tag simply matches nothing, no crash risk), so unlike the
 * rest of the {@code integration.aeronautics} package this class does
 * NOT need to be gated behind {@code ModCompat}.
 */
public final class AeroWeatherBlockTags {
    /** Balloon fabric blocks (all dye colors, plus their encased-shaft variants). */
    public static final TagKey<Block> ENVELOPE = of("aeronautics", "envelope");
    /** Levitite — the magic floating-rock block. */
    public static final TagKey<Block> LEVITITE = of("aeronautics", "levitite");
    /** Create's windmill sail blocks (also includes #minecraft:wool). */
    public static final TagKey<Block> WINDMILL_SAILS = of("create", "windmill_sails");

    private AeroWeatherBlockTags() {
    }

    private static TagKey<Block> of(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
