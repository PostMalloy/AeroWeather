package com.postmalloy.aeroweather.integration.aeronautics;

import org.jetbrains.annotations.Nullable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Sable-backed {@link SubLevelFrames}. Alongside
 * {@code AeronauticsWindForceApplier}, one of only two classes allowed to
 * reference Sable types - see CLAUDE.md's isolation rule. Only ever constructed
 * by {@code SubLevelFrames.Resolved}, after it has confirmed Sable is installed.
 * <p>
 * Uses only Sable's common, side-agnostic API, so one lookup serves both the
 * server (redstone signal, windmill kinetics) and the client (vane animation,
 * windmill visuals): container, then plot, then sub-level, then its logical
 * pose. Every step is null-checked: {@code getContainer} is null for levels
 * Sable doesn't manage, and {@code getPlot} for unallocated plots.
 * {@code inBounds} is a plain range check, so every ordinary world block is
 * rejected before any lookup happens.
 * <p>
 * Positions go through the same pose conversions
 * {@code AeronauticsWindForceApplier} already relies on: {@code transformPosition}
 * for plot to world, and {@code transformNormalInverse} to turn a world
 * direction into the sub-level's own frame.
 */
final class SableSubLevelFrames implements SubLevelFrames {
    @Override
    public @Nullable LocalFrame frameAt(Level level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) {
            return null;
        }
        LevelPlot plot = container.getPlot(new ChunkPos(pos));
        if (plot == null) {
            return null;
        }
        SubLevel subLevel = plot.getSubLevel();
        if (subLevel == null) {
            return null;
        }
        Pose3dc pose = subLevel.logicalPose();
        return new LocalFrame(pose.transformPosition(Vec3.atCenterOf(pos)), pose::transformNormalInverse);
    }
}
