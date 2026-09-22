package com.postmalloy.aeroweather;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.integration.aeronautics.AeronauticsIntegration;
import com.postmalloy.aeroweather.integration.simulated.WindBearingIntegration;
import com.postmalloy.aeroweather.registry.AeroWeatherBlockEntityTypes;
import com.postmalloy.aeroweather.registry.AeroWeatherBlocks;
import com.postmalloy.aeroweather.registry.AeroWeatherCommandArgumentTypes;
import com.postmalloy.aeroweather.registry.AeroWeatherItems;
import com.postmalloy.aeroweather.registry.AeroWeatherParticles;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(AeroWeather.MODID)
public class AeroWeather {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "aeroweather";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public AeroWeather(IEventBus modEventBus, ModContainer modContainer) {
        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, AeroWeatherCommonConfig.SPEC);

        // Register the custom /aeroweather command's Brigadier argument types
        AeroWeatherCommandArgumentTypes.COMMAND_ARGUMENT_TYPES.register(modEventBus);

        // Register wind particle types
        AeroWeatherParticles.PARTICLE_TYPES.register(modEventBus);

        // Register the wind vane block, its item and its block entity type
        AeroWeatherBlocks.BLOCKS.register(modEventBus);
        AeroWeatherItems.ITEMS.register(modEventBus);
        AeroWeatherBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);

        // Start the Aeronautics wind force integration (a no-op unless Sable is installed)
        AeronauticsIntegration.get().start(modEventBus);

        // Register the wind bearing (a no-op unless Create, Simulated and Sable are installed)
        WindBearingIntegration.start(modEventBus);
    }
}
