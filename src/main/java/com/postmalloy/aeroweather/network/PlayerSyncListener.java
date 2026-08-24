package com.postmalloy.aeroweather.network;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.wind.WindSavedData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Sends a full wind sync whenever a player needs fresh state: on join, dimension change, or respawn. */
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
        }
    }
}
