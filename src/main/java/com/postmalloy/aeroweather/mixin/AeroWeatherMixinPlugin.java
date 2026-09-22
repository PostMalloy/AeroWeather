package com.postmalloy.aeroweather.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.neoforged.fml.loading.LoadingModList;

/**
 * Withholds each optional mod's mixins when that mod isn't installed.
 * <p>
 * {@code aeroweather.mixins.json} deliberately declares an <em>empty</em>
 * {@code mixins} array and lets {@link #getMixins()} supply the list instead:
 * naming a mixin in the JSON would make Mixin resolve its target class at
 * config-prepare time, which fails outright when the target mod is absent.
 * Supplying nothing here means the class is never registered and never
 * resolved.
 * <p>
 * This runs during class transformation, well before {@code ModList} exists, so
 * it can't use {@code ModCompat.isLoaded} — {@link LoadingModList} is the
 * equivalent at this stage, and the modid is spelled out here rather than
 * referenced from {@code ModCompat} so that nothing drags {@code ModList} onto
 * the classloader this early. It's a stronger guarantee than the runtime
 * {@code ModCompat} gate the Sable integration uses, since it takes effect
 * before the mixin class is ever loaded.
 */
public class AeroWeatherMixinPlugin implements IMixinConfigPlugin {
    private static final String CREATE_MODID = "create";
    private static final String WINDMILL_MIXIN = "WindmillBearingBlockEntityMixin";
    private static final String PARTICLE_RAIN_MODID = "particlerain";
    private static final String PARTICLE_RAIN_MIXIN = "ParticleRainWindMixin";
    private static final String SIMULATED_MODID = "simulated";
    private static final List<String> SIMULATED_MIXINS =
            List.of("SwivelBearingInternals", "WindBearingPlateMixin", "WindBearingPlateParentMixin",
                    "ScrollValueSlotAccessor");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>(6);
        if (isLoaded(CREATE_MODID)) {
            mixins.add(WINDMILL_MIXIN);
        }
        if (isLoaded(PARTICLE_RAIN_MODID)) {
            mixins.add(PARTICLE_RAIN_MIXIN);
        }
        if (isLoaded(SIMULATED_MODID)) {
            mixins.addAll(SIMULATED_MIXINS);
        }
        return mixins;
    }

    private static boolean isLoaded(String modid) {
        return LoadingModList.get().getModFileById(modid) != null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
