package com.postmalloy.aeroweather.integration;

import net.neoforged.fml.ModList;

/**
 * Mod presence checks for optional soft dependencies. Deliberately free
 * of any Sable/Create/Aeronautics imports so it's always safe to
 * classload — see CLAUDE.md's soft-dependency isolation rule:
 * {@code AeronauticsIntegration} must confirm {@link #isLoaded} before
 * ever constructing {@code AeronauticsWindForceApplier}.
 */
public final class ModCompat {
    public static final String SABLE_MODID = "sable";

    private ModCompat() {
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
