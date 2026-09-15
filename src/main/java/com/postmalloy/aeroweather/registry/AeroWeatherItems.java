package com.postmalloy.aeroweather.registry;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.block.WindVaneBlockItem;
import com.postmalloy.aeroweather.item.BreezeMakerItem;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = AeroWeather.MODID)
public final class AeroWeatherItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeroWeather.MODID);

    public static final DeferredItem<WindVaneBlockItem> ZINC_WIND_VANE = ITEMS.register("zinc_wind_vane",
            () -> new WindVaneBlockItem(AeroWeatherBlocks.ZINC_WIND_VANE.get(), new Item.Properties()));

    public static final DeferredItem<WindVaneBlockItem> BRASS_WIND_VANE = ITEMS.register("brass_wind_vane",
            () -> new WindVaneBlockItem(AeroWeatherBlocks.BRASS_WIND_VANE.get(), new Item.Properties()));

    public static final DeferredItem<BreezeMakerItem> BREEZE_MAKER = ITEMS.register("breeze_maker",
            () -> new BreezeMakerItem(new Item.Properties()));

    private AeroWeatherItems() {
    }

    /**
     * The brass vane is listed alongside vanilla's daylight detector, the other
     * weather-reading redstone sensor. The zinc vane emits no signal, so it goes
     * with the functional blocks instead. The breeze maker is a wind tool first,
     * so it sits with the tools even though it fights like an iron sword.
     */
    @SubscribeEvent
    static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(BRASS_WIND_VANE);
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ZINC_WIND_VANE);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BREEZE_MAKER);
        }
    }
}
