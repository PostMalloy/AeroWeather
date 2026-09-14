package com.postmalloy.aeroweather.client.render;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.block.WindVaneBlockEntity;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * GeckoLib model for both wind vanes. They share one geometry,
 * {@code geo/block/wind_vane.geo.json}, and differ only in texture: each draws
 * {@code textures/block/<its block id>.png} - {@code zinc_wind_vane.png} for the
 * zinc vane, {@code brass_wind_vane.png} for the brass one. (Both follow the
 * {@link DefaultedBlockGeoModel} path convention, verified against GeckoLib
 * 4.9.2.) There's no animation file: the only motion is the {@code vane} bone's
 * heading, set procedurally each frame.
 * <p>
 * GeckoLib turns bones in radians about +Y (verified: its bone transform uses
 * {@code Axis.YP.rotation}), which is counter-clockwise seen from above, while
 * compass bearings run clockwise - hence the negation. The bone turns about its
 * own pivot, so the model places that pivot on the spindle.
 */
public class WindVaneGeoModel extends DefaultedBlockGeoModel<WindVaneBlockEntity> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AeroWeather.MODID, "wind_vane");

    static final String VANE_BONE = "vane";

    /** The bearing the arrowhead points at in the model as authored (0 = north, -Z in Blockbench). */
    static final float MODEL_ARROW_BEARING_DEG = 0.0f;

    public WindVaneGeoModel() {
        super(ID);
    }

    /**
     * Picks the texture per block, since one renderer - and so one model - serves
     * every vane. The one-argument form is the right hook: GeckoLib's two-argument
     * {@code getTextureResource(animatable, renderer)} delegates to it, and
     * {@link DefaultedBlockGeoModel} overrides only this one.
     */
    @Override
    public ResourceLocation getTextureResource(WindVaneBlockEntity vane) {
        return this.buildFormattedTexturePath(BuiltInRegistries.BLOCK.getKey(vane.getBlockState().getBlock()));
    }

    @Override
    public void setCustomAnimations(WindVaneBlockEntity vane, long instanceId, AnimationState<WindVaneBlockEntity> animationState) {
        float heading = vane.getRenderHeading(animationState.getPartialTick());
        this.getBone(VANE_BONE).ifPresent(bone -> bone.setRotY((float) -Math.toRadians(heading - MODEL_ARROW_BEARING_DEG)));
    }
}
