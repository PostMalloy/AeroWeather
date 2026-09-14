package com.postmalloy.aeroweather.registry;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.block.WindVaneBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AeroWeatherBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroWeather.MODID);

    /** Shared by both vanes; the renderer tells them apart by block to pick each one's texture. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindVaneBlockEntity>> WIND_VANE =
            BLOCK_ENTITY_TYPES.register("wind_vane",
                    () -> BlockEntityType.Builder.of(WindVaneBlockEntity::new,
                            AeroWeatherBlocks.ZINC_WIND_VANE.get(), AeroWeatherBlocks.BRASS_WIND_VANE.get()).build(null));

    private AeroWeatherBlockEntityTypes() {
    }
}
