package com.postmalloy.aeroweather.integration.simulated;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.block.WindBearingBlock;
import com.postmalloy.aeroweather.block.WindBearingBlockEntity;
import com.postmalloy.aeroweather.block.WindBearingPlateBlock;

import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The wind bearing's registries, kept apart from {@code registry/} on purpose:
 * touching this class loads {@link WindBearingBlock}, which extends a Create
 * Simulated type. Only {@link WindBearingIntegration} may call it, and only once
 * Create, Simulated and Sable are all confirmed loaded.
 * <p>
 * It owns its own {@code DeferredRegister}s rather than adding to the mod's
 * shared ones for the same reason — those are registered unconditionally, and a
 * conditional entry can't live in them.
 */
public final class WindBearingRegistration {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AeroWeather.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeroWeather.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroWeather.MODID);

    public static final DeferredBlock<WindBearingBlock> WIND_BEARING = BLOCKS.registerBlock("wind_bearing",
            WindBearingBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    public static final DeferredItem<BlockItem> WIND_BEARING_ITEM = ITEMS.registerSimpleBlockItem(WIND_BEARING);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindBearingBlockEntity>> WIND_BEARING_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("wind_bearing",
                    () -> BlockEntityType.Builder.of(WindBearingBlockEntity::new, WIND_BEARING.get()).build(null));

    /**
     * The top piece a wind bearing places on its contraption (see {@link WindBearingPlateBlock}).
     * No item: like Simulated's own plate it only ever exists while assembled, and drops the
     * wind bearing when broken.
     */
    public static final DeferredBlock<WindBearingPlateBlock> WIND_BEARING_PLATE = BLOCKS.registerBlock("wind_bearing_plate",
            WindBearingPlateBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    /** Simulated's own plate block entity class, unchanged; only the type is ours. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SwivelBearingPlateBlockEntity>> WIND_BEARING_PLATE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("wind_bearing_plate",
                    () -> BlockEntityType.Builder.<SwivelBearingPlateBlockEntity>of(
                            (pos, state) -> new SwivelBearingPlateBlockEntity(
                                    WindBearingRegistration.WIND_BEARING_PLATE_BLOCK_ENTITY.get(), pos, state),
                            WIND_BEARING_PLATE.get()).build(null));

    private WindBearingRegistration() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        // Added directly rather than through @EventBusSubscriber: annotation scanning happens
        // for every mod class regardless of our gate, which would defeat the point.
        modEventBus.addListener(WindBearingRegistration::onBuildCreativeTabs);

        // Only classloaded on a physical client: it references Create's renderer and Flywheel's
        // visual, neither of which exists on a dedicated server.
        if (FMLEnvironment.dist.isClient()) {
            WindBearingShaftRendering.start(modEventBus);
        }
    }

    /** Listed with the redstone blocks, alongside Create's own bearings. */
    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(WIND_BEARING_ITEM);
        }
    }
}
