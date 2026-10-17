package com.postmalloy.aeroweather.integration.cloudlayers;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.client.CloudWind;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Where Cloud Layers' decks sit once the wind has carried them — see
 * {@code CloudLayersMixin}, the only caller.
 * <p>
 * Every Cloud Layers layer is a {@code Layer} record, and every reader of one —
 * Cloud Layers' own renderer and Better Cloud Shadows' Cloud Layers bridge — places its
 * texture at {@code offset + time * velocity}. So the mixin rewrites what the record's
 * {@code velocity()} and {@code offset()} hand out: no velocity of their own, and an
 * offset moved on by however far {@link CloudWind} has carried a deck at that layer's
 * height. Both readers then agree with each other by construction, and the shadows stay
 * under the clouds with no hook into Better Cloud Shadows at all.
 * <p>
 * References no Cloud Layers types. Its one Cloud Layers call, for the height offset, is
 * looked up by name, so this is always safe to classload.
 */
@OnlyIn(Dist.CLIENT)
public final class CloudLayersWind {
    private static final List<Double> STILL = List.of(0.0, 0.0);

    /**
     * {@code CloudLayers.getHeightOffset()}: its config's offset, plus one derived from
     * the world's horizon height if that option is on, added to every layer that isn't in
     * skybox mode. Resolved on first use; null if that failed.
     */
    private static MethodHandle heightOffset;
    private static boolean resolved;

    private CloudLayersWind() {
    }

    /** The layer's own velocity, or none while our wind is carrying the clouds instead. */
    public static List<Double> velocity(List<Double> theirs) {
        return CloudWind.active() ? STILL : theirs;
    }

    /**
     * The layer's own offset moved on by the wind at its height, or unchanged while
     * inactive.
     *
     * @param height the layer's own {@code height}, before Cloud Layers' height offset
     * @param skybox whether the layer is in skybox mode, which draws it at {@code height}
     *               above the camera instead, and ignores the height offset
     */
    public static List<Double> offset(List<Double> theirs, double height, boolean skybox) {
        if (!CloudWind.active() || theirs.size() < 2) {
            return theirs;
        }
        double y = skybox ? height : height + heightOffset();
        return List.of(theirs.get(0) + CloudWind.carriedX(y, skybox),
                theirs.get(theirs.size() - 1) + CloudWind.carriedZ(y, skybox));
    }

    private static int heightOffset() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> cloudLayers = Class.forName("mod.lwhrvw.cloud_layers.CloudLayers", false,
                        CloudLayersWind.class.getClassLoader());
                heightOffset = MethodHandles.publicLookup()
                        .findStatic(cloudLayers, "getHeightOffset", MethodType.methodType(int.class));
            } catch (ReflectiveOperationException | RuntimeException e) {
                AeroWeather.LOGGER.warn("Couldn't find Cloud Layers' height offset; its clouds will follow the wind at their configured heights as if it were 0", e);
            }
        }
        if (heightOffset == null) {
            return 0;
        }
        try {
            return (int) heightOffset.invokeExact();
        } catch (Throwable t) {
            // Only before Cloud Layers has loaded its config, which it does before drawing anything.
            return 0;
        }
    }
}
