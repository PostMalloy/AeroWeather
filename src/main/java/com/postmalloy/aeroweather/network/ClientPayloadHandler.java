package com.postmalloy.aeroweather.network;

import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.network.payload.ClientboundWindSyncPayload;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies a received {@link ClientboundWindSyncPayload} to {@link ClientWindState}. */
final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    static void handleWindSync(ClientboundWindSyncPayload payload, IPayloadContext context) {
        ClientWindState.update(payload.dimension(), payload.directionDeg(), payload.strength(), payload.gusting());
    }
}
