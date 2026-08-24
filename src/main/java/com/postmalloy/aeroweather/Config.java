package com.postmalloy.aeroweather;

import net.neoforged.neoforge.common.ModConfigSpec;

// Placeholder config spec. Real wind-simulation tunables land in a future
// milestone (split into common/client specs) — see CLAUDE.md's roadmap.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec SPEC = BUILDER.build();
}
