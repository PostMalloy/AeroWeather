package com.postmalloy.aeroweather.network;

import com.postmalloy.aeroweather.client.ClientActiveContraptions;
import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.network.payload.ClientboundActiveContraptionsPayload;
import com.postmalloy.aeroweather.network.payload.ClientboundBiomeWindPayload;
import com.postmalloy.aeroweather.network.payload.ClientboundWindSyncPayload;
import com.postmalloy.aeroweather.wind.BiomeWindFactors;
import com.postmalloy.aeroweather.wind.WindField;

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

    /**
     * Takes the server's biome table verbatim, in place of anything derived
     * locally, so both sides compute the same per-position wind. Cached cells
     * were built from the old factors, so they go too.
     */
    static void handleBiomeWind(ClientboundBiomeWindPayload payload, IPayloadContext context) {
        BiomeWindFactors.accept(payload.flowSeed(), payload.factors());
        WindField.invalidate();
    }
}
