package com.postmalloy.aeroweather.block;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.registry.AeroWeatherBlockEntityTypes;
import com.postmalloy.aeroweather.wind.LocalWind;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity behind both wind vanes - one type, valid for either block.
 * It exists for two reasons: GeckoLib renders block entities rather than block
 * models, and the vane needs a ticker. The client eases its heading, and on a
 * brass vane the server also keeps the redstone signal current
 * ({@link BrassWindVaneBlock#serverTick}).
 * <p>
 * The heading is purely client-side and transient (no NBT). Each client tick it
 * eases toward the bearing from {@link LocalWind}, which already expresses that
 * bearing in the block's own frame - so on a Sable ship the vane keeps pointing
 * into the real wind as the ship turns beneath it. The ease rate scales with
 * strength: in calm air the vane holds still (turning with the ship, as a real
 * one would); in strong wind it tracks quickly. The very first reading snaps
 * rather than eases, so a vane doesn't visibly sweep round every time its chunk
 * loads or its ship assembles.
 */
public class WindVaneBlockEntity extends BlockEntity implements GeoBlockEntity {
    /** Fraction of the remaining turn covered per tick at (or above) full-signal strength. */
    private static final float MAX_EASE_PER_TICK = 0.15f;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private float heading;
    private float previousHeading;
    private boolean headingInitialized;

    public WindVaneBlockEntity(BlockPos pos, BlockState state) {
        super(AeroWeatherBlockEntityTypes.WIND_VANE.get(), pos, state);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, WindVaneBlockEntity vane) {
        vane.tickHeading(level, pos);
    }

    private void tickHeading(Level level, BlockPos pos) {
        this.previousHeading = this.heading;
        LocalWind.Sample wind = LocalWind.at(level, pos);
        if (wind == null) {
            return;
        }
        if (!this.headingInitialized) {
            this.heading = wind.directionDeg();
            this.previousHeading = this.heading;
            this.headingInitialized = true;
            return;
        }
        float strengthShare = (float) (wind.adjustedStrength() / AeroWeatherCommonConfig.WIND_VANE_FULL_SIGNAL_STRENGTH.getAsDouble());
        float ease = MAX_EASE_PER_TICK * Mth.clamp(strengthShare, 0.0f, 1.0f);
        this.heading += Mth.wrapDegrees(wind.directionDeg() - this.heading) * ease;
    }

    /** The bearing the vane points toward, in its block's own frame, interpolated for rendering. */
    public float getRenderHeading(float partialTick) {
        return Mth.rotLerp(partialTick, this.previousHeading, this.heading);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No keyframe animations: the vane bone is turned procedurally in WindVaneGeoModel.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
