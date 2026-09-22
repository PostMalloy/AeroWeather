package com.postmalloy.aeroweather.integration.simulated;

import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws a turning shaft through the wind bearing and its plate, the way Create draws
 * one through a shaft block — same speed as the kinetic network the bearing is on,
 * since both of Create's shaft classes take their angle from the block entity's own
 * {@code getSpeed()}.
 * <p>
 * <b>Both halves are required.</b> Create renders kinetic blocks two different ways
 * and each covers a case the other doesn't:
 * <ul>
 * <li>{@link ShaftVisual} is the Flywheel instanced path, used whenever Flywheel's
 *     backend is on — which it is by default.</li>
 * <li>{@link ShaftRenderer} is the plain block entity renderer, for when the backend
 *     is off. Registering only this one would draw nothing for most players:
 *     {@code KineticBlockEntityRenderer.renderSafe} returns immediately when
 *     {@code VisualizationManager.supportsVisualization(level)}, leaving the visual
 *     to do the work.</li>
 * </ul>
 * Flywheel's builder also marks the block entity as one to skip the vanilla render
 * for while the backend is on, so the two never draw at once.
 * <p>
 * Client-only, and gated: {@link WindBearingRegistration} only reaches this class on
 * a physical client, and only once Create, Simulated and Sable are all confirmed
 * present. References Create and Flywheel directly, like the rest of {@code M13}.
 */
final class WindBearingShaftRendering {
    private WindBearingShaftRendering() {
    }

    static void start(IEventBus modEventBus) {
        // Added directly rather than through @EventBusSubscriber, for the same reason the
        // registration class does it: annotation scanning ignores our gate.
        modEventBus.addListener(WindBearingShaftRendering::onRegisterRenderers);
        modEventBus.addListener(WindBearingShaftRendering::onClientSetup);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(WindBearingRegistration.WIND_BEARING_BLOCK_ENTITY.get(),
                ShaftRenderer::new);
        // The plate carries the shaft on into the contraption, as Simulated's own plate does.
        event.registerBlockEntityRenderer(WindBearingRegistration.WIND_BEARING_PLATE_BLOCK_ENTITY.get(),
                ShaftRenderer::new);
    }

    /**
     * A visual for the bearing only — deliberately <em>not</em> for the plate.
     * <p>
     * The plate only ever exists inside a Sable sub-level, and Sable draws a sub-level's block
     * entities through the vanilla renderer path, wrapped in the contraption's pose. That path
     * never consults Flywheel's {@code skipVanillaRender}, so a plate with a visual registered
     * gets drawn twice: once by Sable and once by Flywheel, at slightly different angles because
     * the two interpolate partial ticks differently. Simulated's own plate has a renderer and no
     * visual for the same reason — the {@code link_block} package contains no {@code Visual}
     * class, while the rest of the mod has a dozen.
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        // enqueueWork: Flywheel's visualizer registry is a plain map, and client setup runs off
        // the main thread.
        event.enqueueWork(() -> SimpleBlockEntityVisualizer
                .builder(WindBearingRegistration.WIND_BEARING_BLOCK_ENTITY.get())
                .factory(ShaftVisual::new)
                .apply());
    }
}
