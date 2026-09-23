package com.postmalloy.aeroweather.network;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.wind.WindSavedData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Sends full wind state whenever a player needs it: on join, dimension change, or respawn. */
@EventBusSubscriber(modid = AeroWeather.MODID)
public final class PlayerSyncListener {
    private PlayerSyncListener() {
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerLevel level = serverPlayer.serverLevel();
            WindSync.sendTo(serverPlayer, level, WindSavedData.get(level));
            // The biome table rarely changes, but a joining client has none at all yet.
            BiomeWindSync.sendTo(serverPlayer);
        }
    }
}
