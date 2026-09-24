package com.postmalloy.aeroweather.integration.interactivefoliage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.joml.Vector4f;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;
import com.postmalloy.aeroweather.integration.ModCompat;
import com.postmalloy.aeroweather.wind.LocalWind;
import com.postmalloy.aeroweather.wind.WindDirection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Drives Interactive Foliage's grass lean from AeroWeather's wind (M16).
 * <p>
 * IF's wind lives entirely on the GPU. Its Java side hands the shader one
 * {@code Weather} vector per frame — {@code (rain, thunder, reach, fade)} — and the
 * shader leans grass by {@code x}, doubles it by {@code y}, and replaces its calm
 * idle sway with that lean as {@code x} rises. So {@code x} and {@code y} come from
 * our wind instead of the weather, which makes grass lean in any weather, not only
 * rain. {@code w} carries the direction; see {@link FoliageShaderPatch}.
 *
 * <h2>Optional, never required</h2>
 * Nothing here references an Interactive Foliage type. The mixins that call in are
 * withheld by {@code AeroWeatherMixinPlugin} unless IF is installed, and the tick
 * below returns at once without it, so a player without IF pays nothing.
 *
 * <h2>Falling back safely</h2>
 * Direction is only encoded when everything that makes it correct holds: the
 * integration and the direction option are on, IF is a version whose shader text
 * was verified, and no shader patch has failed. Otherwise {@code w} stays IF's own
 * value, which every shader — patched or not — reads as IF's own direction. So an
 * IF update can at worst reduce this to strength-only; it can't break a shader.
 */
@EventBusSubscriber(modid = AeroWeather.MODID, value = Dist.CLIENT)
public final class FoliageWind {
    private static final String MOD_ID = "mc2_interactivefoliage";

    /** IF versions whose shader text {@link FoliageShaderPatch} was checked against. */
    private static final String VERIFIED_VERSION_PREFIX = "2.0.";

    private static final boolean LOADED = ModCompat.isLoaded(MOD_ID);

    /** Set once any shader patch misses its target; from then on only strength is driven. */
    private static volatile boolean patchFailed;

    // Eased state, written on the client tick and read by the render thread's weather() call.
    // Both are the client thread in vanilla, so no synchronisation.
    //
    // Kept as a previous and a current value, and blended by partial tick when drawn - the same
    // thing the wind vane does. The tick runs at 20 Hz but IF reads these every frame, so without
    // it the whole field held still for a few frames and then jumped: at 60 fps the first tick of
    // a reversal swung every blade 14 degrees at once. It read as jitter, most at high wind, where
    // the lean is largest.
    private static boolean initialised;
    /** Our wind at the grass, eased. Lean, storm and idle sway are all read off this. */
    private static float strength;
    private static float previousStrength;
    /** The bearing the wind blows FROM, eased by the shortest arc. */
    private static float fromBearingDeg;
    private static float previousFromBearingDeg;

    private FoliageWind() {
    }

    // ----------------------------------------------------------------- shader text

    /** Patches {@code include/sway.glsl}, used by IF's vanilla-terrain and Iris programs. */
    public static String patchSway(String source) {
        return patchOrKeep(source, FoliageShaderPatch.SWAY, "sway.glsl");
    }

    /**
     * Wraps the provider IF builds its {@code foliage_legacy} shader from, so that
     * one file comes back patched and everything else — {@code #moj_import}ed
     * includes too — passes straight through. {@code ShaderInstance} reads program
     * source through exactly this provider, so this reaches the Sodium path without
     * touching vanilla's shader loading for anyone else.
     */
    public static ResourceProvider wrapLegacyProvider(ResourceProvider original) {
        return location -> {
            Optional<Resource> found = original.getResource(location);
            if (found.isEmpty() || !isLegacyVertexShader(location)) {
                return found;
            }
            Resource resource = found.get();
            return Optional.of(new Resource(resource.source(), () -> {
                String source;
                try (InputStream in = resource.open()) {
                    source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                String patched = patchOrKeep(source, FoliageShaderPatch.LEGACY, "foliage_legacy.vsh");
                return new ByteArrayInputStream(patched.getBytes(StandardCharsets.UTF_8));
            }));
        };
    }

    private static boolean isLegacyVertexShader(ResourceLocation location) {
        return MOD_ID.equals(location.getNamespace()) && location.getPath().endsWith("foliage_legacy.vsh");
    }

    private static String patchOrKeep(String source, FoliageShaderPatch.Names names, String name) {
        String patched = FoliageShaderPatch.patch(source, names);
        if (patched != null) {
            return patched;
        }
        if (!patchFailed) {
            patchFailed = true;
            AeroWeather.LOGGER.warn("Interactive Foliage's {} isn't the text AeroWeather expects, so grass will lean "
                    + "with our wind's strength but in Interactive Foliage's own direction", name);
        }
        return source;
    }

    // ----------------------------------------------------------------- per frame

    /**
     * The {@code Weather} vector IF's shaders get, in place of the one it computed.
     * {@code original.z} is IF's reach, which is never 0 while its wind is on, so a
     * zero there means IF's own {@code weatherWind} option is off — and that's
     * respected, not overridden.
     */
    public static Vector4f adjustWeather(Vector4f original) {
        if (!enabled() || original.z == 0.0f) {
            return original;
        }
        float w = directionActive() ? encodedDirection() : original.w;
        return new Vector4f(lean(), storm(), original.z, w);
    }

    /**
     * IF's calm idle sway, scaled by our wind. IF sways grass at full strength whenever
     * its waving option is on, whatever the weather - so with our wind at 0 the lean
     * vanished but the idle sway stayed, and dead calm air still rippled the grass.
     * {@code CalmSway} multiplies only that idle term (IF's {@code SwayIntensity} also
     * scales the wind, so it's left alone), and IF's own value is kept as a factor, so
     * turning its waving option off still turns it off.
     */
    public static float adjustCalmSway(float original) {
        if (!enabled()) {
            return original;
        }
        double fullAt = AeroWeatherClientConfig.INTERACTIVE_FOLIAGE_CALM_SWAY_FULL_STRENGTH.getAsDouble();
        return original * (float) Mth.clamp(renderedStrength() / fullAt, 0.0, 1.0);
    }

    /** How far grass leans: 0 in calm air, 1 at full lean. Also how much of the idle sway it replaces. */
    private static float lean() {
        double fullLean = AeroWeatherClientConfig.INTERACTIVE_FOLIAGE_FULL_LEAN_STRENGTH.getAsDouble();
        return (float) Mth.clamp(renderedStrength() / fullLean, 0.0, 1.0);
    }

    /** IF doubles the lean as this goes 0 to 1, so it ramps in past full lean. */
    private static float storm() {
        double fullLean = AeroWeatherClientConfig.INTERACTIVE_FOLIAGE_FULL_LEAN_STRENGTH.getAsDouble();
        double stormAt = AeroWeatherClientConfig.INTERACTIVE_FOLIAGE_STORM_STRENGTH.getAsDouble();
        return stormAt <= fullLean ? 0.0f
                : (float) Mth.clamp((renderedStrength() - fullLean) / (stormAt - fullLean), 0.0, 1.0);
    }

    /**
     * IF only works out wind shelter behind walls while it rains, and assumes an east
     * wind when it does. With our direction encoded that would shelter the wrong side
     * of every wall, so its rain check is told there's no rain and its own state
     * machine keeps shelter off. Without our direction its shelter is right again.
     */
    public static float shelterRainLevel(float original) {
        return directionActive() ? 0.0f : original;
    }

    private static boolean enabled() {
        return LOADED && AeroWeatherClientConfig.INTERACTIVE_FOLIAGE_ENABLED.get();
    }

    private static boolean directionActive() {
        return enabled()
                && AeroWeatherClientConfig.INTERACTIVE_FOLIAGE_DIRECTION_ENABLED.get()
                && !patchFailed
                && VersionCheck.VERIFIED;
    }

    private static float encodedDirection() {
        Vec3 travel = WindDirection.travelVector(renderedFromBearingDeg());
        return FoliageShaderPatch.encodeAngle(travel.x, travel.z);
    }

    /**
     * How far through the current tick this frame is. The same call IF itself uses for
     * the rain level it would otherwise have sent, so we blend on its timing.
     */
    private static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static float renderedStrength() {
        return Mth.lerp(partialTick(), previousStrength, strength);
    }

    private static float renderedFromBearingDeg() {
        return Mth.rotLerp(partialTick(), previousFromBearingDeg, fromBearingDeg);
    }

    // ----------------------------------------------------------------- per tick

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            initialised = false;
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        // At the ground below the player, not the player: grass grows at the surface, and a
        // player flying high would otherwise apply up to 3x height scaling to grass far below.
        // A world position, so LocalWind gives world-frame wind even when stood on a ship.
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        LocalWind.Sample sample = LocalWind.at(level, new BlockPos(x, ground, z));

        float target = sample == null ? 0.0f : sample.adjustedStrength();

        if (!initialised) {
            // Snap on the first reading, so grass doesn't sweep round every time you log in.
            strength = target;
            previousStrength = target;
            if (sample != null) {
                fromBearingDeg = sample.directionDeg();
                previousFromBearingDeg = fromBearingDeg;
            }
            initialised = true;
            return;
        }

        previousStrength = strength;
        previousFromBearingDeg = fromBearingDeg;

        float rate = (float) AeroWeatherClientConfig.INTERACTIVE_FOLIAGE_EASE_RATE.getAsDouble();
        strength += (target - strength) * rate;
        if (sample != null) {
            // Exponential easing alone is fastest at the start: a reversal's first tick turned
            // 14 degrees, around 290 degrees a second. Even interpolated, that sweeps the gust
            // crossfade fast enough to flicker at high wind. Capping the turn gives a steady swing
            // that still settles smoothly at the end, where the eased step falls below the cap.
            float maxStep = (float) AeroWeatherClientConfig.INTERACTIVE_FOLIAGE_MAX_TURN_RATE.getAsDouble() / 20.0f;
            float step = Mth.clamp(Mth.wrapDegrees(sample.directionDeg() - fromBearingDeg) * rate, -maxStep, maxStep);
            fromBearingDeg = WindDirection.normalizeDegrees(fromBearingDeg + step);
        }
    }

    /** Read lazily, once: ModList exists by then, and the answer can't change during a session. */
    private static final class VersionCheck {
        static final boolean VERIFIED = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString().startsWith(VERIFIED_VERSION_PREFIX))
                .orElse(false);

        static {
            if (!VERIFIED) {
                AeroWeather.LOGGER.info("Interactive Foliage isn't a version AeroWeather has verified its shaders for, "
                        + "so grass will follow our wind's strength but keep its own direction");
            }
        }

        private VersionCheck() {
        }
    }
}
