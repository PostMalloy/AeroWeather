package com.postmalloy.aeroweather.client.render;

import com.postmalloy.aeroweather.block.WindVaneBlockEntity;

import software.bernie.geckolib.renderer.GeoBlockRenderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * Draws the wind vane through GeckoLib, with {@link WindVaneGeoModel} turning
 * the {@code vane} bone. The block has no facing property, so GeckoLib's own
 * {@code getFacing} falls back to NORTH and {@code rotateBlock(NORTH)} is a 0°
 * turn (verified against GeckoLib 4.9.2) - the model renders exactly as
 * authored, arrow north at heading 0.
 * <p>
 * Widens the render bounds past the default unit cube, so a vane that overhangs
 * its block - up to a block to either side, two blocks tall - isn't culled early
 * at the screen edge. On a Sable ship this matters just as much: Sable culls
 * ship-mounted block entities by transforming exactly these bounds by the
 * ship's pose.
 */
public class WindVaneRenderer extends GeoBlockRenderer<WindVaneBlockEntity> {
    public WindVaneRenderer(BlockEntityRendererProvider.Context context) {
        super(new WindVaneGeoModel());
    }

    @Override
    public AABB getRenderBoundingBox(WindVaneBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(1.0);
    }
}
