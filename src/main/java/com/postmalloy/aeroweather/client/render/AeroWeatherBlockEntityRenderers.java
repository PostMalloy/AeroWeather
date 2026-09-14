package com.postmalloy.aeroweather.client.render;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.registry.AeroWeatherBlockEntityTypes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = AeroWeather.MODID, value = Dist.CLIENT)
public final class AeroWeatherBlockEntityRenderers {
    private AeroWeatherBlockEntityRenderers() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AeroWeatherBlockEntityTypes.WIND_VANE.get(), WindVaneRenderer::new);
    }
}
