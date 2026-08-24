package com.postmalloy.aeroweather.wind;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.network.WindSync;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Advances wind simulation once per second (every 20 ticks) for each
 * loaded server level, then hands off to {@link WindSync} to decide
 * whether this step's change is worth broadcasting. LevelTickEvent also
 * fires client-side, so this filters to ServerLevel only.
 */
@EventBusSubscriber(modid = AeroWeather.MODID)
public final class WindSimulator {
    private static final int SIMULATION_INTERVAL_TICKS = 20;

    private WindSimulator() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % SIMULATION_INTERVAL_TICKS != 0) {
            return;
        }

        WindSavedData savedData = WindSavedData.get(serverLevel);
        savedData.wind().tick(serverLevel.getRandom(), serverLevel.isRaining(), serverLevel.isThundering());
        savedData.setDirty();
        WindSync.maybeSync(serverLevel, savedData);
    }
}
