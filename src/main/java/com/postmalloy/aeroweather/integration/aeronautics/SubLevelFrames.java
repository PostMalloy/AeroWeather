package com.postmalloy.aeroweather.integration.aeronautics;

import java.util.function.UnaryOperator;

import org.jetbrains.annotations.Nullable;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.integration.ModCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Answers "is this block riding a Sable sub-level, and if so where is it
 * really?" for code that needs wind as seen from a block's own grid - the wind
 * vane and Create windmills, via {@code wind.LocalWind}.
 * <p>
 * Blocks on a sub-level live in real level chunks at far-away "plot"
 * coordinates, and the sub-level's pose maps them into the world. So such a
 * block's own {@code BlockPos} says nothing about its real altitude, and its
 * block grid is rotated relative to the world's compass.
 * <p>
 * Deliberately free of Sable types, so it's always safe to classload: the
 * Sable-backed {@link SableSubLevelFrames} is only constructed once
 * {@link ModCompat#isLoaded} confirms Sable is present - the same isolation
 * pattern as {@link AeronauticsIntegration}.
 */
public interface SubLevelFrames {
    /** The active implementation: Sable-backed when Sable is installed, otherwise one that finds nothing. */
    static SubLevelFrames get() {
        return Resolved.INSTANCE;
    }

    /**
     * Where the block at {@code pos} really is, or {@code null} if it isn't on a
     * sub-level - true of every block in an ordinary world, and of all blocks
     * when Sable isn't installed.
     */
    @Nullable
    LocalFrame frameAt(Level level, BlockPos pos);

    /** The block's centre in world space: its own centre, unless it's riding a sub-level. */
    static Vec3 worldPositionOf(Level level, BlockPos pos) {
        LocalFrame frame = get().frameAt(level, pos);
        return frame == null ? Vec3.atCenterOf(pos) : frame.worldPosition();
    }

    /**
     * A sub-level block's world-space centre, plus a way to rotate a
     * world-space direction into the sub-level's own frame - the frame its block
     * grid, facings and redstone neighbours all live in. Sable-free on purpose,
     * so callers never see a Sable type.
     */
    record LocalFrame(Vec3 worldPosition, UnaryOperator<Vec3> worldToLocalDirection) {
    }

    /** Lazy holder, so resolution runs on first lookup rather than whenever this interface loads. */
    final class Resolved {
        private static final SubLevelFrames INSTANCE = resolve();

        private Resolved() {
        }

        private static SubLevelFrames resolve() {
            if (!ModCompat.isLoaded(ModCompat.SABLE_MODID)) {
                return (level, pos) -> null;
            }
            try {
                return new SableSubLevelFrames();
            } catch (Throwable t) {
                AeroWeather.LOGGER.error("Failed to initialize Sable sub-level lookups; treating every block as world-placed.", t);
                return (level, pos) -> null;
            }
        }
    }
}
