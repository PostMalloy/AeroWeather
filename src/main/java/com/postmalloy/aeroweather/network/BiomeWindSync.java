package com.postmalloy.aeroweather.network;

import com.postmalloy.aeroweather.network.payload.ClientboundBiomeWindPayload;
import com.postmalloy.aeroweather.wind.BiomeWindFactors;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the biome wind table to clients. Rare and small: once per join, and
 * again only if a datapack or config reload changes it — unlike {@link WindSync},
 * which carries state that moves every second.
 */
public final class BiomeWindSync {
    private BiomeWindSync() {
    }

    public static void sendTo(ServerPlayer player) {
        if (!BiomeWindFactors.isReady()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload());
    }

    public static void broadcast(MinecraftServer server) {
        if (!BiomeWindFactors.isReady() || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        PacketDistributor.sendToAllPlayers(payload());
    }

    private static ClientboundBiomeWindPayload payload() {
        return new ClientboundBiomeWindPayload(BiomeWindFactors.flowSeed(), BiomeWindFactors.table());
    }
}
