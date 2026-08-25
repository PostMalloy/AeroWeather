package com.postmalloy.aeroweather.network;

import com.postmalloy.aeroweather.client.ClientActiveContraptions;
import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.network.payload.ClientboundActiveContraptionsPayload;
import com.postmalloy.aeroweather.network.payload.ClientboundWindSyncPayload;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies received payloads to the client-side state they mirror. */
final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    static void handleWindSync(ClientboundWindSyncPayload payload, IPayloadContext context) {
        ClientWindState.update(payload.dimension(), payload.directionDeg(), payload.strength());
    }

    static void handleActiveContraptions(ClientboundActiveContraptionsPayload payload, IPayloadContext context) {
        ClientActiveContraptions.update(payload.dimension(), payload.positions());
    }
}
