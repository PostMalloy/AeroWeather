package com.postmalloy.aeroweather;

import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = AeroWeather.MODID, dist = Dist.CLIENT)
public class AeroWeatherClient {
    public AeroWeatherClient(ModContainer container) {
        // Register our client-only ModConfigSpec (particle tunables) so FML creates and loads it
        container.registerConfig(ModConfig.Type.CLIENT, AeroWeatherClientConfig.SPEC);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
