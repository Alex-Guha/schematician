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

            final ForceOverlayClient.ForceSnapshot snapshot = ForceOverlayClient.currentSnapshot();

            // CoM cube tints by snapshot state: white = snapshot received, magenta = waiting for
            // first packet. Lets us tell at a glance whether the server-tracking pipeline is
            // delivering data versus the client-only marker rendering correctly.
            final RenderType fillType = OverlayRenderTypes.overlayFill();
            renderCenterOfMass(poseStack, bufferSource.getBuffer(fillType), snapshot != null);
            bufferSource.endBatch(fillType);

            if (snapshot != null) {
                final RenderType lineType = OverlayRenderTypes.forceLines();
                final RenderType triType = OverlayRenderTypes.overlayTriangles();
                final VertexConsumer lineConsumer = bufferSource.getBuffer(lineType);
                final VertexConsumer triConsumer = bufferSource.getBuffer(triType);
                renderForces(poseStack, lineConsumer, triConsumer, snapshot, rotationPoint);
                bufferSource.endBatch(lineType);
                bufferSource.endBatch(triType);
            }
        } finally {
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void renderCenterOfMass(final PoseStack poseStack, final VertexConsumer consumer, final boolean haveSnapshot) {
        // Filled cube at the rotation point (== CoM). White when we've got a force snapshot from
        // the server, magenta while we're still waiting for the first packet so the bad state is
        // visible at a glance.
        final double half = 0.08;
        final float r, g, b;
        if (haveSnapshot) {
            r = 1.0f; g = 1.0f; b = 1.0f;
        } else {
            r = 1.0f; g = 0.2f; b = 0.9f;
        }
        final float a = 1.0f;
        quadCube(poseStack, consumer, half, r, g, b, a);
    }

    // 6 quads forming an axis-aligned cube centered at the origin, edge length 2*half.
    // QUADS render type, POSITION_COLOR vertex format.
    private static void quadCube(final PoseStack poseStack, final VertexConsumer consumer,
                                 final double half,
                                 final float r, final float g, final float b, final float a) {
        final var pose = poseStack.last();
        final float n = (float) -half;
        final float p = (float) half;
        // -X face
        quad(pose, consumer, n, n, n,  n, p, n,  n, p, p,  n, n, p, r, g, b, a);
        // +X face
        quad(pose, consumer, p, n, p,  p, p, p,  p, p, n,  p, n, n, r, g, b, a);
        // -Y face
        quad(pose, consumer, n, n, p,  p, n, p,  p, n, n,  n, n, n, r, g, b, a);
        // +Y face
        quad(pose, consumer, n, p, n,  p, p, n,  p, p, p,  n, p, p, r, g, b, a);
        // -Z face
        quad(pose, consumer, p, n, n,  p, p, n,  n, p, n,  n, n, n, r, g, b, a);
        // +Z face
        quad(pose, consumer, n, n, p,  n, p, p,  p, p, p,  p, n, p, r, g, b, a);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer consumer,
                             final float x1, final float y1, final float z1,
                             final float x2, final float y2, final float z2,
                             final float x3, final float y3, final float z3,
                             final float x4, final float y4, final float z4,
                             final float r, final float g, final float b, final float a) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        consumer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }

    private static void renderForces(final PoseStack poseStack,
                                     final VertexConsumer lineConsumer,
                                     final VertexConsumer triConsumer,
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
                drawArrow(poseStack, lineConsumer, triConsumer, c.pos(), c.force(), rotationPoint, scale, minLen, maxLen, r, g, b);
            }
        }
    }

    private static void drawArrow(final PoseStack poseStack,
                                  final VertexConsumer lineConsumer,
                                  final VertexConsumer triConsumer,
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
        line(poseStack, lineConsumer, bx, by, bz, tx, ty, tz, r, g, b, dir.x, dir.y, dir.z);

        // Cone tip — half the length of the old 4-prong head and narrower.
        final Vector3d ref = Math.abs(dir.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        final Vector3d perp1 = new Vector3d(dir).cross(ref).normalize();
        final Vector3d perp2 = new Vector3d(dir).cross(perp1).normalize();

        final double coneLen = Math.max(0.09, length * 0.10);
        final double coneRadius = coneLen * 0.40;

        cone(poseStack, triConsumer,
                tx, ty, tz,
                dir.x, dir.y, dir.z,
                perp1, perp2,
                coneLen, coneRadius,
                r, g, b);
    }

    // Cone aimed forward along `dir`. Tip at (tipX, tipY, tipZ), base disc centered at
    // (tip - dir*length). Drawn as `segments` side triangles + a closing base fan.
    private static void cone(final PoseStack poseStack, final VertexConsumer consumer,
                             final double tipX, final double tipY, final double tipZ,
                             final double dx, final double dy, final double dz,
                             final Vector3d perp1, final Vector3d perp2,
                             final double length, final double radius,
                             final float r, final float g, final float b) {
        final int segments = 10;
        final double baseX = tipX - dx * length;
        final double baseY = tipY - dy * length;
        final double baseZ = tipZ - dz * length;

        final double[] cx = new double[segments + 1];
        final double[] cy = new double[segments + 1];
        final double[] cz = new double[segments + 1];
        for (int i = 0; i <= segments; i++) {
            final double angle = 2.0 * Math.PI * i / segments;
            final double c = Math.cos(angle);
            final double s = Math.sin(angle);
            cx[i] = baseX + (perp1.x * c + perp2.x * s) * radius;
            cy[i] = baseY + (perp1.y * c + perp2.y * s) * radius;
            cz[i] = baseZ + (perp1.z * c + perp2.z * s) * radius;
        }

        final var pose = poseStack.last();
        for (int i = 0; i < segments; i++) {
            // Side wall: tip → ring[i] → ring[i+1]
            triangle(pose, consumer,
                    tipX, tipY, tipZ,
                    cx[i], cy[i], cz[i],
                    cx[i + 1], cy[i + 1], cz[i + 1],
                    r, g, b);
            // Base cap: center → ring[i+1] → ring[i] (reversed winding so it's outward-facing)
            triangle(pose, consumer,
                    baseX, baseY, baseZ,
                    cx[i + 1], cy[i + 1], cz[i + 1],
                    cx[i], cy[i], cz[i],
                    r, g, b);
        }
    }

    private static void triangle(final PoseStack.Pose pose, final VertexConsumer consumer,
                                 final double x1, final double y1, final double z1,
                                 final double x2, final double y2, final double z2,
                                 final double x3, final double y3, final double z3,
                                 final float r, final float g, final float b) {
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, 1.0f);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, 1.0f);
        consumer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(r, g, b, 1.0f);
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
