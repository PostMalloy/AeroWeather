package com.postmalloy.aeroweather.network;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.network.payload.ClientboundWindSyncPayload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AeroWeather.MODID)
public final class NetworkHandler {
    private NetworkHandler() {
    }

    @SubscribeEvent
    static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(ClientboundWindSyncPayload.TYPE, ClientboundWindSyncPayload.STREAM_CODEC, ClientPayloadHandler::handleWindSync);
    }
}
