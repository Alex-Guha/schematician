package com.alexguha.schematician.overlay;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.alexguha.schematician.config.SchematicianClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Veil render-stage handler for the force overlay.
//
// Renders at AFTER_LEVEL so we land after DraftingViewHandler.applyIfWearingGoggles has run the
// post-process pipeline — the overlay then draws on top of the post-processed image without
// being palette-shifted itself. Uses OverlayRenderTypes.forceLines() to disable depth testing,
// so vectors and the CoM marker remain visible through the sublevel's own blocks.
//
// Matrix handling: the post-process call clobbers RenderSystem's modelview matrix (it sets up
// screen-space matrices for the fullscreen quad and doesn't restore them). We seed it from the
// frustumMatrix the event handed us, draw, then restore via push/pop on the modelview stack.
// Without this, vertices submitted in world-relative coords render in identity-view space (the
// overlay appears glued to the camera, not to the sublevel).
//
// Coordinate frame: PointForce.point and renderPose.rotationPoint() are both in the sublevel's
// local block frame. We translate the PoseStack to (renderPose.position() - camera), rotate by
// renderPose.orientation(), and the local origin then sits at the rotation point with axes
// aligned to the sublevel — so a force at `point` is drawn at `point - rotationPoint`.
public final class ForceOverlayRenderer {
    private ForceOverlayRenderer() {}

    public static void onRenderStage(final VeilRenderLevelStageEvent.Stage stage,
                                     final MultiBufferSource.BufferSource bufferSource,
                                     final Camera camera,
                                     final Matrix4fc frustumMatrix) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;
        if (player == null || level == null) return;
        if (!isWearingActiveGoggles(player)) return;

        final UUID targetId = ForceOverlayClient.currentTarget();
        if (targetId == null) return;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        final SubLevel raw = container.getSubLevel(targetId);
        if (!(raw instanceof final ClientSubLevel clientSubLevel)) return;

        final Pose3dc renderPose = clientSubLevel.renderPose();
        final Vector3dc renderPos = renderPose.position();
        final Vector3dc rotationPoint = renderPose.rotationPoint();
        final Vec3 camPos = camera.getPosition();

        // Restore the world-render modelview matrix that the drafting-view post-process clobbered
        // when it bound its fullscreen quad pipeline.
        final Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.set(frustumMatrix);
        RenderSystem.applyModelViewMatrix();

        try {
            final PoseStack poseStack = new PoseStack();
            poseStack.translate(renderPos.x() - camPos.x, renderPos.y() - camPos.y, renderPos.z() - camPos.z);
            poseStack.mulPose(new Quaternionf(renderPose.orientation()));

            final RenderType type = OverlayRenderTypes.forceLines();
            final VertexConsumer consumer = bufferSource.getBuffer(type);

            renderCenterOfMass(poseStack, consumer);

            final ForceOverlayClient.ForceSnapshot snapshot = ForceOverlayClient.currentSnapshot();
            if (snapshot != null) {
                renderForces(poseStack, consumer, snapshot, rotationPoint);
            }

            // Flush so the lines reach the framebuffer while our matrices are still in place.
            bufferSource.endBatch(type);
        } finally {
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void renderCenterOfMass(final PoseStack poseStack, final VertexConsumer consumer) {
        // Compact "dot" marker: 3 short perpendicular line segments through the origin (which is
        // the rotation point == CoM). Small enough to read as a dot, but the cross shape ensures
        // a visible silhouette from any viewing angle (a true point can't be drawn via LINES).
        final double half = 0.08;
        final float r = 1.0f, g = 1.0f, b = 1.0f;
        line(poseStack, consumer, -half, 0, 0,  half, 0, 0, r, g, b, 1, 0, 0);
        line(poseStack, consumer,  0, -half, 0, 0,  half, 0, r, g, b, 0, 1, 0);
        line(poseStack, consumer,  0, 0, -half, 0, 0,  half, r, g, b, 0, 0, 1);
    }

    private static void renderForces(final PoseStack poseStack,
                                     final VertexConsumer consumer,
                                     final ForceOverlayClient.ForceSnapshot snapshot,
                                     final Vector3dc rotationPoint) {
        final double scale = SchematicianClientConfig.METERS_PER_NEWTON.get();
        final double minLen = SchematicianClientConfig.MIN_ARROW_LENGTH.get();
        final double maxLen = SchematicianClientConfig.MAX_ARROW_LENGTH.get();
        final double angleThreshold = SchematicianClientConfig.CLUSTER_ANGLE_RADIANS.get();

        for (final Map.Entry<ResourceLocation, List<QueuedForceGroup.PointForce>> entry : snapshot.forces().entrySet()) {
            final ForceGroup group = ForceGroups.REGISTRY.get(entry.getKey());
            if (group == null) continue;

            final int color = group.color();
            final float r = ((color >> 16) & 0xFF) / 255.0f;
            final float g = ((color >> 8) & 0xFF) / 255.0f;
            final float b = (color & 0xFF) / 255.0f;

            final List<ForceClusterer.Cluster> clusters = ForceClusterer.cluster(entry.getValue(), angleThreshold);
            for (final ForceClusterer.Cluster c : clusters) {
                drawArrow(poseStack, consumer, c.pos(), c.force(), rotationPoint, scale, minLen, maxLen, r, g, b);
            }
        }
    }

    private static void drawArrow(final PoseStack poseStack,
                                  final VertexConsumer consumer,
                                  final Vector3dc forcePoint,
                                  final Vector3dc forceVec,
                                  final Vector3dc rotationPoint,
                                  final double scale,
                                  final double minLen,
                                  final double maxLen,
                                  final float r, final float g, final float b) {
        final double magnitude = forceVec.length();
        if (magnitude < 1.0e-6) return;

        double length = magnitude * scale;
        if (length < minLen) length = minLen;
        if (length > maxLen) length = maxLen;

        final Vector3d dir = new Vector3d(forceVec).div(magnitude);

        final double bx = forcePoint.x() - rotationPoint.x();
        final double by = forcePoint.y() - rotationPoint.y();
        final double bz = forcePoint.z() - rotationPoint.z();

        final double tx = bx + dir.x * length;
        final double ty = by + dir.y * length;
        final double tz = bz + dir.z * length;

        // Shaft.
        line(poseStack, consumer, bx, by, bz, tx, ty, tz, r, g, b, dir.x, dir.y, dir.z);

        // Arrowhead: 4 short backwards-angled lines splayed off-axis.
        final Vector3d ref = Math.abs(dir.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        final Vector3d perp1 = new Vector3d(dir).cross(ref).normalize();
        final Vector3d perp2 = new Vector3d(dir).cross(perp1).normalize();

        final double headLen = Math.max(0.18, length * 0.2);
        final double headSplay = headLen * 0.55;

        final double baseX = tx - dir.x * headLen;
        final double baseY = ty - dir.y * headLen;
        final double baseZ = tz - dir.z * headLen;

        for (int i = 0; i < 4; i++) {
            final Vector3d axis = (i % 2 == 0) ? perp1 : perp2;
            final double sign = (i < 2) ? 1.0 : -1.0;
            line(poseStack, consumer,
                    baseX + axis.x * headSplay * sign, baseY + axis.y * headSplay * sign, baseZ + axis.z * headSplay * sign,
                    tx, ty, tz,
                    r, g, b, dir.x, dir.y, dir.z);
        }
    }

    private static void line(final PoseStack poseStack, final VertexConsumer consumer,
                             final double x1, final double y1, final double z1,
                             final double x2, final double y2, final double z2,
                             final float r, final float g, final float b,
                             final double nx, final double ny, final double nz) {
        final var pose = poseStack.last();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, 1.0f)
                .setNormal(pose, (float) nx, (float) ny, (float) nz);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, 1.0f)
                .setNormal(pose, (float) nx, (float) ny, (float) nz);
    }

    private static boolean isWearingActiveGoggles(final LocalPlayer player) {
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(Schematician.SCHEMATICIANS_GOGGLES.asItem())) {
            return false;
        }
        return head.getOrDefault(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), Boolean.TRUE);
    }
}
