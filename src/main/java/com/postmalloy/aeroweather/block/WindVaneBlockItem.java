package com.postmalloy.aeroweather.block;

import java.util.function.Consumer;

import com.postmalloy.aeroweather.client.render.WindVaneGeoModel;
import com.postmalloy.aeroweather.client.render.WindVaneItemRenderer;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * The item for either wind vane, drawn with the same GeckoLib geometry and
 * texture as its placed block, with the vane pointed into the wind wherever the
 * item is drawn in the world (see {@link WindVaneItemRenderer}). GeckoLib's own
 * {@code BlockEntityWithoutLevelRendererMixin}
 * routes {@code builtin/entity} items to the renderer supplied here, so no
 * NeoForge client-extension registration is needed.
 * <p>
 * Safe on a dedicated server despite the client types below: GeckoLib only calls
 * {@link #createGeoRenderer} from a lazy provider gated on
 * {@code GeckoLibServices.PLATFORM.isPhysicalClient()} (verified against 4.9.2),
 * so the anonymous provider class is never loaded there.
 */
public class WindVaneBlockItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public WindVaneBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<WindVaneBlockItem> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null) {
                    // Same geometry for every vane; the texture is the one named after this item's
                    // block, through the same path builder WindVaneGeoModel uses for placed vanes.
                    this.renderer = new WindVaneItemRenderer(new DefaultedBlockGeoModel<WindVaneBlockItem>(WindVaneGeoModel.ID)
                            .withAltTexture(BuiltInRegistries.BLOCK.getKey(WindVaneBlockItem.this.getBlock())));
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No keyframe animations: WindVaneItemRenderer turns the vane bone directly.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
