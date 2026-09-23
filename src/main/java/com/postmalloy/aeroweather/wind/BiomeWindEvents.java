package com.postmalloy.aeroweather.wind;

import java.util.List;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.network.BiomeWindSync;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Keeps {@link BiomeWindFactors} and {@link WindField} in step with the world
 * (M14): builds the biome table when a server starts or its tags change, writes
 * newly discovered biomes back into the config, and drops cached cells whenever
 * anything they were derived from moves.
 * <p>
 * {@code TagsUpdatedEvent} rather than {@code ServerAboutToStartEvent} for the
 * rebuild, because {@code Holder.is(TagKey)} is meaningless until tags are bound
 * and a {@code /reload} can change them. It fires on both sides, which is what
 * we want: a client that isn't running a server still needs its cells dropped,
 * it just takes its table from the server instead of building one.
 */
@EventBusSubscriber(modid = AeroWeather.MODID)
public final class BiomeWindEvents {
    /**
     * Guards against re-entry: {@code ModConfigSpec.save()} fires
     * {@code ModConfigEvent.Reloading} synchronously, and that listener would
     * otherwise come straight back here and write the config again.
     */
    private static boolean writingConfig;

    private BiomeWindEvents() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        rebuild(event.getServer());
    }

    @SubscribeEvent
    static void onTagsUpdated(TagsUpdatedEvent event) {
        // Biome factors may have changed, so nothing cached from them is trustworthy.
        WindField.invalidate();

        // Null on a client connected to someone else's server: it keeps the table the
        // server sent rather than building its own.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            rebuild(server);
            BiomeWindSync.broadcast(server);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        BiomeWindFactors.clear();
        WindField.invalidate();
    }

    /** Called from the mod bus when the config file is reloaded; see {@code AeroWeather}'s constructor. */
    public static void onConfigReloaded() {
        if (writingConfig) {
            return;
        }
        WindField.invalidate();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            rebuild(server);
            BiomeWindSync.broadcast(server);
        }
    }

    private static void rebuild(MinecraftServer server) {
        // The overworld's seed, so a given world always gets the same flow map.
        BiomeWindFactors.rebuild(server.registryAccess(), server.overworld().getSeed());
        WindField.invalidate();
        writeDiscoveredBiomes(server);
    }

    /**
     * Adds every biome the game loaded to the config, so modded ones show up
     * ready to tune. Values the user has already set are carried through
     * untouched; the list is sorted, so it stops changing after the first write.
     */
    private static void writeDiscoveredBiomes(MinecraftServer server) {
        if (!AeroWeatherCommonConfig.SPEC.isLoaded()) {
            return;
        }

        List<String> discovered = BiomeWindFactors.asConfigLines(server.registryAccess());
        if (discovered.equals(AeroWeatherCommonConfig.BIOME_WIND_FACTORS.get())) {
            return;
        }

        writingConfig = true;
        try {
            AeroWeatherCommonConfig.BIOME_WIND_FACTORS.set(discovered);
            AeroWeatherCommonConfig.SPEC.save();
            AeroWeather.LOGGER.info("Wrote {} biome wind factors to the config", discovered.size());
        } catch (RuntimeException failed) {
            // A config we can't write is not worth taking the world down for; the
            // in-memory table is already built and correct.
            AeroWeather.LOGGER.warn("Couldn't write discovered biomes to the config", failed);
        } finally {
            writingConfig = false;
        }
    }
}
