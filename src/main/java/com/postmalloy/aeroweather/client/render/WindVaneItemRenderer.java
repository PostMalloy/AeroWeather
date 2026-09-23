package com.postmalloy.aeroweather.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.postmalloy.aeroweather.block.WindVaneBlockItem;
import com.postmalloy.aeroweather.client.ClientWindState;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WindField;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a wind vane item with its vane swung into the wind wherever the item
 * appears in the world: in either hand, in first or third person, held by
 * another player or a mob, in an item frame, or dropped on the ground.
 * <p>
 * Rather than subtracting the holder's yaw - which would also need correcting
 * for each hand's display angle, left-hand mirroring, arm swing and head pitch
 * - it reads the model's actual orientation off the render transform just
 * before the vane bone is drawn, and turns the bone so the arrow lines up with
 * the wind as closely as a spin about the vane's own axis allows.
 * <p>
 * The bone is set on every draw, including in the inventory, where it rests at
 * its authored angle. That matters: GeckoLib bakes each geo file once, so this
 * item shares its bones with every placed vane, and a bone left alone keeps
 * whatever angle the last placed vane was drawn at - which is why the item used
 * to point in arbitrary directions.
 */
public class WindVaneItemRenderer extends GeoItemRenderer<WindVaneBlockItem> {
    public WindVaneItemRenderer(GeoModel<WindVaneBlockItem> model) {
        super(model);
    }

    /**
     * On entry the pose stack is still the vane's parent frame: GeckoLib applies
     * the bone's own transform (verified against 4.9.2: push, then
     * {@code RenderUtil.prepMatrixForBone}, which turns about {@code Axis.YP})
     * only after this is called.
     */
    @Override
    public void renderRecursively(PoseStack poseStack, WindVaneBlockItem animatable, GeoBone bone, RenderType renderType,
            MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
            int packedLight, int packedOverlay, int colour) {
        if (WindVaneGeoModel.VANE_BONE.equals(bone.getName())) {
            bone.setRotY(vaneRotation(poseStack.last().pose()));
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    /** The vane bone's Y rotation in radians: into the wind in the world, at rest anywhere else. */
    private float vaneRotation(Matrix4f pose) {
        if (!isInWorld(this.renderPerspective)) {
            return 0.0f;
        }
        Vector3f windFrom = windFromInWorld();
        if (windFrom == null) {
            return 0.0f;
        }

        // The model's own X and Z axes as world directions. The pose stack is world-aligned in every
        // in-world context: world and entity rendering keep the camera's rotation out of it, and the
        // first-person hand pass cancels it back out - GameRenderer.renderItemInHand starts the pose
        // stack with the inverse of the view rotation and puts the view rotation itself on the
        // model-view stack. (Applying Camera.rotation() here too double-rotated the held vane.)
        Vector3f axisX = pose.transformDirection(new Vector3f(1.0f, 0.0f, 0.0f));
        Vector3f axisZ = pose.transformDirection(new Vector3f(0.0f, 0.0f, 1.0f));

        // Turning the bone by t about +Y carries the model's -Z arrow to -sin(t)*X - cos(t)*Z, which
        // lines up best with the wind at t = atan2(-X.w, -Z.w). That holds at any tilt, and on a level
        // model it's exactly the placed vane's -bearing.
        float intoWind = (float) Math.atan2(-axisX.dot(windFrom), -axisZ.dot(windFrom));
        return intoWind + (float) Math.toRadians(WindVaneGeoModel.MODEL_ARROW_BEARING_DEG);
    }

    /** Horizontal unit vector toward where the wind blows from, or null before wind has synced here. */
    private static Vector3f windFromInWorld() {
        ClientLevel level = Minecraft.getInstance().level;
        ResourceLocation syncedDimension = ClientWindState.dimension();
        if (level == null || syncedDimension == null || !syncedDimension.equals(level.dimension().location())) {
            return null;
        }
        // Sampled at the camera, not the item: a held vane should agree with a placed one
        // standing next to the player.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        WindField.Sample field = WindField.at(level, camera.x, camera.z);
        Vec3 travel = WindDirection.travelVector(
                WindDirection.normalizeDegrees(ClientWindState.directionDeg() + field.directionOffsetDeg()));
        return new Vector3f((float) -travel.x, 0.0f, (float) -travel.z);
    }

    /** Everywhere except flat screen contexts, which have no world direction to point along. */
    private static boolean isInWorld(ItemDisplayContext context) {
        return context != null && context != ItemDisplayContext.GUI && context != ItemDisplayContext.NONE;
    }
}
