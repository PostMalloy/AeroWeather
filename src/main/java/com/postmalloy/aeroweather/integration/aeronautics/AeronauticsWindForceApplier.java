package com.postmalloy.aeroweather.integration.aeronautics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WindHeightScaling;
import com.postmalloy.aeroweather.wind.WindSavedData;
import com.postmalloy.aeroweather.wind.WindState;

import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The only class in AeroWeather allowed to reference Sable (or Create/
 * Create Aeronautics/Sable Companion) types directly — see CLAUDE.md's
 * soft-dependency isolation rule. Only ever constructed by
 * {@link AeronauticsIntegration} after {@link ModCompat#isLoaded} has
 * confirmed Sable is present.
 * <p>
 * Event registration in {@link #start} is deliberately manual/instance-
 * based ({@code NeoForge.EVENT_BUS.addListener(...)}), NOT this
 * codebase's usual {@code @EventBusSubscriber} + static
 * {@code @SubscribeEvent} pattern — that pattern gets scanned and
 * registered by NeoForge at mod-init regardless of intent, which would
 * force-classload Sable's event types even when Sable isn't installed.
 * Manual registration inside {@code start} keeps classloading
 * conditional on the isolation-rule check already having passed. Do not
 * "clean this up" to the usual annotation pattern. The one exception is
 * the {@code ForceGroup} registration below, which genuinely needs the
 * mod event bus's {@code RegisterEvent} (the only bus it fires on) —
 * still manual (a plain {@code addListener} call inside {@code start}),
 * just not on {@code NeoForge.EVENT_BUS} like the other two.
 * <p>
 * Applies wind force uniformly to every Sable sub-level (not just
 * recognized Create Aeronautics assemblies) gated only on Sable being
 * loaded — Sable's sub-level API can't distinguish content, so there's
 * no clean way to restrict this further; see CLAUDE.md's M6/M7 notes.
 */
public final class AeronauticsWindForceApplier implements WindForceApplier, SubLevelObserver {
    /**
     * Constructing this is safe without touching the registry (a plain
     * record), but it must also be REGISTERED into {@code ForceGroups.REGISTRY}
     * (see {@link #onRegisterForceGroup}) - an unregistered ForceGroup has no
     * registry id, and Create Aeronautics' contraption diagram tool crashes
     * the connection trying to network-encode a sub-level's queued force
     * groups by id (confirmed live: NullPointerException encoding
     * `simulated:diagram_data`, deep in an Object2ObjectMap<ForceGroup,...>
     * codec). Registering it (rather than just not using ForceGroups at all)
     * also makes the wind force show up correctly on that same diagram.
     */
    private static final ForceGroup WIND_FORCE_GROUP = new ForceGroup(
            Component.translatable("aeroweather.aeronautics.force_group.name"),
            Component.translatable("aeroweather.aeronautics.force_group.description"),
            0x55AAFF, true);

    /** Lift ratio + local half-extents, cached once per sub-level on first force application. Keyed by SubLevel.getUniqueId(). */
    private final Map<UUID, LiftProfile> liftProfiles = new ConcurrentHashMap<>();
    /** Resolves which ServerLevel a ForgeSablePrePhysicsTickEvent belongs to. */
    private final Map<SubLevelPhysicsSystem, ServerLevel> levelsByPhysicsSystem = new HashMap<>();
    private volatile boolean disabled = false;

    @Override
    public void start(IEventBus modEventBus) {
        try {
            modEventBus.addListener(RegisterEvent.class, this::onRegisterForceGroup);
            NeoForge.EVENT_BUS.addListener(ForgeSableSubLevelContainerReadyEvent.class, this::onSubLevelContainerReady);
            NeoForge.EVENT_BUS.addListener(ForgeSablePrePhysicsTickEvent.class, this::onPrePhysicsTick);
        } catch (Throwable t) {
            AeroWeather.LOGGER.error("Failed to start the Aeronautics wind force integration; disabling.", t);
            disabled = true;
        }
    }

    /**
     * NeoForge fires RegisterEvent once per registry; this no-ops unless the
     * registryKey matches ForceGroups.REGISTRY_KEY, per the standard pattern.
     * Only vanilla Registry/RegisterEvent types are touched here (not Veil's
     * RegistrationProvider/RegistryObject, which ForceGroups.GRAVITY/DRAG/etc.
     * use internally) - confirmed via a compile-time smoke test that
     * referencing ForceGroups.REGISTRY_KEY/REGISTRY alone doesn't require Veil
     * on the classpath, unlike referencing the individual built-in groups.
     */
    private void onRegisterForceGroup(RegisterEvent event) {
        event.register(ForceGroups.REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath(AeroWeather.MODID, "wind"),
                () -> WIND_FORCE_GROUP);
    }

    private void onSubLevelContainerReady(ForgeSableSubLevelContainerReadyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        SubLevelContainer container = event.getContainer();
        if (container instanceof ServerSubLevelContainer serverContainer) {
            levelsByPhysicsSystem.put(serverContainer.physicsSystem(), serverLevel);
        }
        container.addObserver(this);
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        liftProfiles.remove(subLevel.getUniqueId());
    }

    /**
     * Computed lazily on first use rather than eagerly in onSubLevelAdded: that
     * notification fires before the contraption's blocks are actually copied
     * into the sub-level's storage (confirmed live - it saw a bare 2x2x2 box),
     * so an eager scan there permanently caches an empty snapshot. By the time
     * a sub-level is actually reaching applyWindForce (i.e. participating in
     * real physics ticks), its blocks are guaranteed populated. Still computed
     * and cached exactly once per sub-level, matching the original intent.
     */
    private LiftProfile getOrBuildLiftProfile(ServerSubLevel subLevel) {
        return liftProfiles.computeIfAbsent(subLevel.getUniqueId(), id -> buildLiftProfile(subLevel));
    }

    private LiftProfile buildLiftProfile(ServerSubLevel subLevel) {
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        Level level = subLevel.getLevel();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int totalSolidBlocks = 0;
        int liftBlocks = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    totalSolidBlocks++;
                    if (state.is(AeroWeatherBlockTags.ENVELOPE)
                            || state.is(AeroWeatherBlockTags.LEVITITE)
                            || state.is(AeroWeatherBlockTags.WINDMILL_SAILS)) {
                        liftBlocks++;
                    }
                }
            }
        }

        double liftRatio = totalSolidBlocks == 0 ? 0.0 : (double) liftBlocks / totalSolidBlocks;
        double halfExtentX = (bounds.maxX() - bounds.minX() + 1) / 2.0;
        double halfExtentY = (bounds.maxY() - bounds.minY() + 1) / 2.0;
        double halfExtentZ = (bounds.maxZ() - bounds.minZ() + 1) / 2.0;
        return new LiftProfile(liftRatio, halfExtentX, halfExtentY, halfExtentZ);
    }

    /** Fires every physics substep; forces aren't persistent in Sable so they're re-queued fresh each time. */
    private void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        if (disabled) {
            return;
        }
        try {
            ServerLevel level = levelsByPhysicsSystem.get(event.getPhysicsSystem());
            if (level == null) {
                return;
            }
            WindState wind = WindSavedData.get(level).wind();
            if (wind.strength() <= 0.0f) {
                return;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            for (ServerSubLevel subLevel : container.getAllSubLevels()) {
                try {
                    applyWindForce(subLevel, level, wind);
                } catch (Throwable t) {
                    AeroWeather.LOGGER.warn("Failed to apply wind force to a Sable sub-level; skipping it this tick.", t);
                }
            }
        } catch (Throwable t) {
            AeroWeather.LOGGER.error("Failed to process a Sable physics tick; disabling AeroWeather's wind force.", t);
            disabled = true;
        }
    }

    private void applyWindForce(ServerSubLevel subLevel, ServerLevel level, WindState wind) {
        LiftProfile profile = getOrBuildLiftProfile(subLevel);
        if (profile.liftRatio() <= 0.0) {
            return;
        }

        MassData mass = subLevel.getMassTracker();
        Vector3dc centerOfMass = mass == null ? null : mass.getCenterOfMass();
        if (mass == null || mass.getMass() <= 0.0 || centerOfMass == null) {
            return;
        }

        // Center of mass and the force/point passed to applyAndRecordPointForce are both in the
        // sub-level's LOCAL frame (confirmed by decompiling FloatingBlockController's own gravity/
        // lift force calls, which never transform to world space before recording). Only the wind
        // DIRECTION needs rotating from world into local space, and only the center of mass needs a
        // one-off world-space Y read, purely to sample elevation-adjusted strength at its altitude.
        Pose3dc pose = subLevel.logicalPose();
        Vec3 comWorld = pose.transformPosition(new Vec3(centerOfMass.x(), centerOfMass.y(), centerOfMass.z()));
        float adjustedStrength = WindHeightScaling.scale(wind.strength(), comWorld.y, level.getSeaLevel(),
                AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_EXPONENT.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_MAX_MULTIPLIER.getAsDouble());
        if (adjustedStrength <= 0.0f) {
            return;
        }

        Vec3 worldWindDirection = WindDirection.travelVector(wind.directionDeg());
        Vec3 localDir = pose.transformNormalInverse(worldWindDirection);
        double localLength = localDir.length();
        if (localLength < 1.0E-6) {
            return;
        }
        Vector3d localWindDirection = new Vector3d(localDir.x, localDir.y, localDir.z).div(localLength);

        // Closed-form box-silhouette projection: each pair of opposite faces (area 4*ha*hb) contributes
        // in proportion to how face-on it is to the wind. No block iteration needed here at all - only
        // the cached half-extents and a fresh per-tick rotation.
        double sectionalArea = 4.0 * (profile.halfExtentX() * profile.halfExtentY() * Math.abs(localWindDirection.z)
                + profile.halfExtentY() * profile.halfExtentZ() * Math.abs(localWindDirection.x)
                + profile.halfExtentX() * profile.halfExtentZ() * Math.abs(localWindDirection.y));

        double magnitude = AeroWeatherCommonConfig.AERONAUTICS_PRESSURE_COEFFICIENT.getAsDouble()
                * sectionalArea * ((double) adjustedStrength * adjustedStrength) * profile.liftRatio();
        magnitude *= oscillationMultiplier(level);
        magnitude = Math.min(magnitude, AeroWeatherCommonConfig.AERONAUTICS_MAX_FORCE.getAsDouble());
        if (magnitude <= 0.0) {
            return;
        }

        Vector3d force = new Vector3d(localWindDirection).mul(magnitude);
        QueuedForceGroup queued = subLevel.getOrCreateQueuedForceGroup(WIND_FORCE_GROUP);
        queued.applyAndRecordPointForce(new Vector3d(centerOfMass), force);
    }

    /**
     * A sinusoidal ripple on top of the base force (+/-60% every 2s by
     * default, tuned via live testing) - not a separate force, just a slow
     * multiplier so the push doesn't feel perfectly static. Phased off the
     * level's game time (not accumulated substep dt) so it stays stable
     * regardless of how many physics substeps run per game tick.
     */
    private static double oscillationMultiplier(ServerLevel level) {
        double amplitude = AeroWeatherCommonConfig.AERONAUTICS_OSCILLATION_AMPLITUDE.getAsDouble();
        double periodSeconds = AeroWeatherCommonConfig.AERONAUTICS_OSCILLATION_PERIOD_SECONDS.getAsDouble();
        double phase = (level.getGameTime() / 20.0) / periodSeconds;
        return 1.0 + amplitude * Math.sin(2.0 * Math.PI * phase);
    }

    private record LiftProfile(double liftRatio, double halfExtentX, double halfExtentY, double halfExtentZ) {
    }
}
