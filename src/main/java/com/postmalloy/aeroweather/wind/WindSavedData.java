package com.postmalloy.aeroweather.wind;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persists a {@link WindState} per {@link ServerLevel}. Obtain via
 * {@link #get(ServerLevel)}, which creates and stores a fresh instance
 * the first time a level is asked for its wind. This is what makes wind
 * naturally per-dimension: each ServerLevel (Overworld, Nether, End)
 * gets its own independent state with no special-casing required.
 */
public final class WindSavedData extends SavedData {
    private static final String DATA_NAME = "aeroweather_wind";
    private static final SavedData.Factory<WindSavedData> FACTORY =
            new SavedData.Factory<>(WindSavedData::new, (tag, registries) -> new WindSavedData(WindState.load(tag)));

    private final WindState wind;

    private WindSavedData() {
        this(new WindState());
    }

    private WindSavedData(WindState wind) {
        this.wind = wind;
    }

    public static WindSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public WindState wind() {
        return wind;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return wind.save(tag);
    }
}
