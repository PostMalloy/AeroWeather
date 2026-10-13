package com.postmalloy.aeroweather.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.postmalloy.aeroweather.AeroWeather;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Carries older config files over to the current layout before NeoForge reads them.
 * <p>
 * Both configs used to be flat. The common file's wind sections now sit under
 * {@code wind} and its blocks' and items' under {@code itemsAndBlocks}; the client
 * file's mod integrations under {@code compat}. That's what lets the config screen show
 * them as sub-menus. But NeoForge resets anything it can't find at its current path to
 * the default (backing the old file up first), which would quietly undo every setting
 * in the moved sections — a player's whole tuned biome factor list included. So each
 * old section is moved, values and all, to where it now lives. The common file's
 * removed {@code sync} section is dropped: those thresholds are constants now.
 * <p>
 * Runs from the mod constructors, before {@code registerConfig}; NeoForge only loads
 * common and client configs later in mod loading. Comments aren't carried over, since
 * NeoForge rewrites them from the spec when it corrects the file. Never throws: at
 * worst the old file is left as it was and NeoForge resets what it can't place.
 */
public final class ConfigMigration {
    /** The common file: where each formerly top-level section now lives. */
    static final Layout COMMON = new Layout(AeroWeather.MODID + "-common.toml", Map.of(
            "drift", "wind",
            "gust", "wind",
            "weather", "wind",
            "height", "wind",
            "biomeWind", "wind",
            "upperWind", "wind",
            "windVane", "itemsAndBlocks",
            "windBearing", "itemsAndBlocks",
            "breezeMaker", "itemsAndBlocks"),
            List.of("sync"));

    /** The client file. */
    static final Layout CLIENT = new Layout(AeroWeather.MODID + "-client.toml", Map.of(
            "particleRain", "compat",
            "interactiveFoliage", "compat"),
            List.of());

    private ConfigMigration() {
    }

    /** One file's moves: each old top-level section to the section it now sits in, and sections now gone. */
    record Layout(String fileName, Map<String, String> movedSections, List<String> removedSections) {
    }

    public static void common() {
        run(COMMON);
    }

    public static void client() {
        run(CLIENT);
    }

    private static void run(Layout layout) {
        Path path = FMLPaths.CONFIGDIR.get().resolve(layout.fileName());
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(path, TomlFormat.instance()).build()) {
            config.load();
            if (migrate(config, layout)) {
                config.save();
                AeroWeather.LOGGER.info("Moved {} over to the new config layout", layout.fileName());
            }
        } catch (Exception e) {
            AeroWeather.LOGGER.warn("Couldn't carry {} over to the new config layout; NeoForge will reset what it can't place",
                    layout.fileName(), e);
        }
    }

    /** Moves the old sections in place. Returns whether anything changed. */
    static boolean migrate(CommentedConfig config, Layout layout) {
        boolean changed = false;
        for (Map.Entry<String, String> move : layout.movedSections().entrySet()) {
            String section = move.getKey();
            if (!(config.get(section) instanceof CommentedConfig old)) {
                continue;
            }
            List<String> destination = List.of(move.getValue(), section);
            // A section already at its new path is newer than anything left at the old one.
            if (!config.contains(destination)) {
                config.set(destination, old);
            }
            config.remove(section);
            changed = true;
        }
        for (String section : layout.removedSections()) {
            if (config.contains(section)) {
                config.remove(section);
                changed = true;
            }
        }
        return changed;
    }
}
