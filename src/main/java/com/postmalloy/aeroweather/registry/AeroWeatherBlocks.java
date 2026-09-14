package com.postmalloy.aeroweather.registry;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.block.BrassWindVaneBlock;
import com.postmalloy.aeroweather.block.WindVaneBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AeroWeatherBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AeroWeather.MODID);

    public static final DeferredBlock<WindVaneBlock> ZINC_WIND_VANE = BLOCKS.registerBlock("zinc_wind_vane", WindVaneBlock::new,
            windVaneProperties(MapColor.COLOR_ORANGE));

    public static final DeferredBlock<BrassWindVaneBlock> BRASS_WIND_VANE = BLOCKS.registerBlock("brass_wind_vane",
            BrassWindVaneBlock::new, windVaneProperties(MapColor.GOLD));

    private AeroWeatherBlocks() {
    }

    /** Both vanes share everything physical; only their map colour differs. */
    private static BlockBehaviour.Properties windVaneProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(3.0f, 6.0f)
                .sound(SoundType.COPPER)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }
}
